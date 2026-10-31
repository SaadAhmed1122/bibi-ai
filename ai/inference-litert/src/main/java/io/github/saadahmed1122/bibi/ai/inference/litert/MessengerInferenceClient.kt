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
import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import io.github.saadahmed1122.bibi.ai.inference.api.ProtocolMessage
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
import io.github.saadahmed1122.bibi.ai.inference.api.toEnvelope
import io.github.saadahmed1122.bibi.ai.inference.api.toMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * [InferenceClient] over a bound [InferenceService]. [connect] binds and, once bound, asks the
 * service to load the model at [modelPath]. Replies arrive on the main thread and are routed to
 * the current send by request id; others are dropped.
 *
 * Recovery (SPEC-inference.md): if `:inference` dies, an in-flight reply ends with
 * [ReplyEvent.Error] and the state goes to [EngineState.Loading]. The system restarts a bound
 * service on its own and [ServiceConnection.onServiceConnected] reloads the model. A second death
 * before the model is Ready again gives up with [EngineState.Failed], so a model that crashes on
 * load doesn't loop.
 *
 * The action router loads next to the model when [routerPath] gives a file (at bind, or later
 * through [loadRouter]); [route] waits for the answer with its request id.
 */
class MessengerInferenceClient(
    private val context: Context,
    private val modelPath: () -> String,
    private val routerPath: () -> String? = { null },
    /** The saved personality preset, sent before every Load so the first prompt is the right one. */
    private val persona: () -> String = { Persona.FRIENDLY },
    /** The pinned memory notes block, sent before every Load (SPEC-memory.md). */
    private val memoryNotes: () -> String = { "" },
) : InferenceClient {

    private val _state = MutableStateFlow<EngineState>(EngineState.Unbound)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    private val replies = Messenger(Handler(Looper.getMainLooper()) { onReply(it); true })
    private val active = ActiveRequest()
    private val nextRequestId = AtomicLong(1)

    @Volatile private var service: Messenger? = null

    /** Main thread only. */
    private var bound = false

    /** Main thread only. True from a death of `:inference` until the reloaded model is Ready. */
    private var recovering = false

    /** Main thread only. Screens using the client (app chat, chat card); unbinds when it drops to 0. */
    private var users = 0

    /** Main thread only. Route requests waiting for their answer, by request id. */
    private val routes = HashMap<Long, CompletableDeferred<RouteResult>>()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val s = Messenger(binder)
            service = s
            sendTo(s, ProtocolMessage.Register, replyTo = replies)
            sendTo(s, ProtocolMessage.SetPersona(persona()))
            sendTo(s, ProtocolMessage.SetMemory(memoryNotes()))
            sendTo(s, ProtocolMessage.Load(modelPath()))
            routerPath()?.let { sendTo(s, ProtocolMessage.LoadRouter(it)) }
        }

        /** Only called when `:inference` crashed or was killed, not on unbind. */
        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            active.fail(REASON_DISCONNECTED)
            failRoutes()
            if (recovering) {
                giveUp()
            } else {
                recovering = true
                _state.value = EngineState.Loading
            }
        }

        /**
         * The binding can't come back by itself: bind once more. If that one dies too before
         * Ready, [onServiceDisconnected] gives up and unbinds, and this returns early.
         */
        override fun onBindingDied(name: ComponentName) {
            if (!bound) return
            context.unbindService(this)
            bound = false
            bind()
        }
    }

    private fun giveUp() {
        _state.value = EngineState.Failed(REASON_DISCONNECTED)
        if (bound) context.unbindService(connection)
        bound = false
    }

    /**
     * Adds a user and binds the service if needed; the service then loads the model. Main thread.
     * Every [connect] needs one [disconnect].
     */
    fun connect() {
        users++
        bind()
    }

    private fun bind() {
        if (bound) return
        bound = context.bindService(Intent(context, InferenceService::class.java), connection, Context.BIND_AUTO_CREATE)
        _state.value = if (bound) EngineState.Loading else EngineState.Failed("bind failed")
    }

    /** Removes a user; the last one unbinds, and the service unloads the model once no reply is running. Main thread. */
    fun disconnect() {
        if (users == 0) return
        users--
        if (users > 0 || !bound) return
        context.unbindService(connection)
        bound = false
        recovering = false
        service = null
        active.fail(REASON_DISCONNECTED)
        failRoutes()
        _state.value = EngineState.Unbound
    }

    /** Applies a new preset now; the service reopens the conversation (about 7 s). Main thread. */
    fun setPersona(id: String) {
        service?.let { sendTo(it, ProtocolMessage.SetPersona(id)) }
    }

    /** New pinned notes for a running service; they join the next conversation open. Main thread. */
    fun setMemoryNotes(notes: String) {
        service?.let { sendTo(it, ProtocolMessage.SetMemory(notes)) }
    }

    /** Loads the router into a running service, e.g. right after its import. Main thread. */
    fun loadRouter(path: String) {
        service?.let { sendTo(it, ProtocolMessage.LoadRouter(path)) }
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

    override suspend fun route(text: String): RouteResult = withContext(Dispatchers.Main.immediate) {
        val s = service ?: return@withContext RouteResult.Unavailable(REASON_DISCONNECTED)
        val id = nextRequestId.getAndIncrement()
        val answer = CompletableDeferred<RouteResult>()
        routes[id] = answer
        try {
            if (!sendTo(s, ProtocolMessage.Route(id, text))) return@withContext RouteResult.Unavailable("send failed")
            answer.await()
        } finally {
            routes.remove(id)
        }
    }

    private fun failRoutes() {
        routes.values.forEach { it.complete(RouteResult.Unavailable(REASON_DISCONNECTED)) }
        routes.clear()
    }

    private fun onReply(msg: android.os.Message) {
        when (val m = InferenceProtocol.decode(msg.toEnvelope())) {
            is ProtocolMessage.State -> if (bound) {
                _state.value = m.state
                if (m.state == EngineState.Ready) recovering = false
            }
            is ProtocolMessage.Reply -> active.deliver(m.requestId, m.event)
            is ProtocolMessage.Routed -> routes[m.requestId]?.complete(m.result)
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
