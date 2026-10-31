package io.github.saadahmed1122.bibi.ai.inference.litert

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Messenger
import android.os.RemoteException
import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.InferenceProtocol
import io.github.saadahmed1122.bibi.ai.inference.api.ProtocolMessage
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.ai.inference.api.toEnvelope
import io.github.saadahmed1122.bibi.ai.inference.api.toMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * [InferenceClient] over a bound [InferenceService]. [connect] binds and, once bound, asks the
 * service to load the model at [modelPath]. Replies arrive on the main thread and are routed to
 * the current send by request id; others are dropped.
 *
 * Recovery after the `:inference` process dies is Task 7 (#38): for now the state just goes to
 * [EngineState.Failed] and an in-flight reply ends with [ReplyEvent.Error].
 */
class MessengerInferenceClient(
    private val context: Context,
    private val modelPath: () -> String,
) : InferenceClient {

    private val _state = MutableStateFlow<EngineState>(EngineState.Unbound)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val replies = Messenger(Handler(Looper.getMainLooper()) { onReply(it); true })
    private val active = ActiveRequest()
    private val nextRequestId = AtomicLong(1)

    @Volatile private var service: Messenger? = null

    /** Main thread only. */
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = Messenger(binder)
            service = s
            sendTo(s, ProtocolMessage.Register, replyTo = replies)
            sendTo(s, ProtocolMessage.Load(modelPath()))
        }

        /** Only called when `:inference` crashed or was killed, not on unbind. */
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _state.value = EngineState.Failed(REASON_DISCONNECTED)
            active.fail(REASON_DISCONNECTED)
        }
    }

    /** Binds the service, which then loads the model. Main thread; no-op when already bound. */
    fun connect() {
        if (bound) return
        bound = context.bindService(Intent(context, InferenceService::class.java), connection, Context.BIND_AUTO_CREATE)
        _state.value = if (bound) EngineState.Loading else EngineState.Failed("bind failed")
    }

    /** Unbinds; the service unloads the model once no reply is running. Main thread. */
    fun disconnect() {
        if (!bound) return
        context.unbindService(connection)
        bound = false
        service = null
        active.fail(REASON_DISCONNECTED)
        _state.value = EngineState.Unbound
    }

    override fun send(text: String): Flow<ReplyEvent> = callbackFlow {
        val id = nextRequestId.getAndIncrement()
        val s = service
        if (s != null && _state.value == EngineState.Ready && active.start(id) { event -> trySend(event); if (event !is ReplyEvent.Token) close() }) {
            if (!sendTo(s, ProtocolMessage.Send(id, text))) active.fail("send failed")
        } else {
            val busy = active.currentId() != null || _state.value == EngineState.Generating
            trySend(ReplyEvent.Error(if (busy) InferenceClient.ERROR_BUSY else InferenceClient.ERROR_NOT_READY, ""))
            close()
        }
        awaitClose {
            // The collector went away mid-reply: stop the generation nobody is reading.
            if (active.currentId() == id) {
                active.clear(id)
                service?.let { sendTo(it, ProtocolMessage.Stop(id)) }
            }
        }
    }.buffer(Channel.UNLIMITED) // At most 256 tokens; never drop one.

    override fun stop() {
        val id = active.currentId() ?: return
        service?.let { sendTo(it, ProtocolMessage.Stop(id)) }
    }

    override fun resetConversation() {
        service?.let { sendTo(it, ProtocolMessage.Reset) }
    }

    private fun onReply(msg: android.os.Message) {
        when (val m = InferenceProtocol.decode(msg.toEnvelope())) {
            is ProtocolMessage.State -> if (bound) _state.value = m.state
            is ProtocolMessage.Reply -> active.deliver(m.requestId, m.event)
            else -> Unit
        }
    }

    private fun sendTo(target: Messenger, message: ProtocolMessage, replyTo: Messenger? = null): Boolean = try {
        target.send(InferenceProtocol.encode(message).toMessage().also { it.replyTo = replyTo })
        true
    } catch (e: RemoteException) {
        false
    }

    private companion object {
        const val REASON_DISCONNECTED = "inference process disconnected"
    }
}
