package io.github.saadahmed1122.bibi.ai.inference.litert

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import io.github.saadahmed1122.bibi.ai.inference.api.EngineState
import io.github.saadahmed1122.bibi.ai.inference.api.Envelope
import io.github.saadahmed1122.bibi.ai.inference.api.InferenceClient
import io.github.saadahmed1122.bibi.ai.inference.api.InferenceProtocol
import io.github.saadahmed1122.bibi.ai.inference.api.ProtocolMessage
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.ai.inference.api.toEnvelope
import io.github.saadahmed1122.bibi.ai.inference.api.toMessage
import java.io.File
import java.util.concurrent.Executors

/**
 * Runs the LLM in the `:inference` process (manifest `android:process`), bound with a
 * [Messenger] that speaks [InferenceProtocol] (ported from the S2 spike).
 *
 * Messages are handled on the main thread. Every engine call except [LiteRtLmEngine.cancel] runs
 * on one thread, [engineThread] (PRD §12.7); cancel has to come from outside because the engine
 * thread is blocked in the turn. While a reply is generating the service is also *started*, so a
 * client that unbinds or rebinds mid-answer doesn't destroy it; it stops itself after the reply.
 */
class InferenceService : Service() {

    private val engineThread = Executors.newSingleThreadExecutor { Thread(it, "llm-engine") }
    private val main = Handler(Looper.getMainLooper())
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { handle(it); true })

    /** Main thread only. */
    private var client: Messenger? = null

    /** Main thread only. Set while a model is loading or loaded; cleared when a load fails. */
    private var loadedPath: String? = null

    /** Main thread only. The send whose reply is in progress. */
    private var activeRequestId: Long? = null

    @Volatile private var state: EngineState = EngineState.Unbound

    @Volatile private var stopRequested = false

    /** Written on the engine thread; read on main for [LiteRtLmEngine.cancel]. */
    @Volatile private var engine: LiteRtLmEngine? = null

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        stopRequested = true
        engine?.cancel()
        engineThread.execute {
            runCatching { engine?.close() }
            engine = null
        }
        engineThread.shutdown()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        when (val m = InferenceProtocol.decode(msg.toEnvelope())) {
            ProtocolMessage.Register -> {
                client = msg.replyTo
                post(ProtocolMessage.State(state))
            }
            is ProtocolMessage.Load -> {
                if (loadedPath != null) {
                    post(ProtocolMessage.State(state))
                    return
                }
                loadedPath = m.modelPath
                setState(EngineState.Loading)
                engineThread.execute { load(m.modelPath) }
            }
            is ProtocolMessage.Send -> {
                val current = state
                if (current != EngineState.Ready || activeRequestId != null) {
                    val busy = current == EngineState.Generating || activeRequestId != null
                    val reason = if (busy) InferenceClient.ERROR_BUSY else InferenceClient.ERROR_NOT_READY
                    post(ProtocolMessage.Reply(m.requestId, ReplyEvent.Error(reason, "")))
                    return
                }
                activeRequestId = m.requestId
                stopRequested = false
                setState(EngineState.Generating)
                startService(Intent(this, InferenceService::class.java))
                engineThread.execute { generate(m.requestId, m.text) }
            }
            is ProtocolMessage.Stop -> if (m.requestId == activeRequestId) {
                stopRequested = true
                engine?.cancel()
            }
            ProtocolMessage.Reset -> if (state == EngineState.Ready) {
                engineThread.execute { reset() }
            }
            // Service -> client messages and unknown codes are ignored.
            is ProtocolMessage.State, is ProtocolMessage.Reply, null -> Unit
        }
    }

    /** Engine thread. Loads the model and prefills the system prompt and primer. */
    private fun load(path: String) {
        try {
            require(File(path).isFile) { "model file not found: $path" }
            val e = LiteRtLmEngine(cacheDir.path)
            engine = e
            val t0 = SystemClock.elapsedRealtime()
            e.load(path)
            val t1 = SystemClock.elapsedRealtime()
            e.openChat(systemPrompt())
            Log.i(TAG, "load_ms=${t1 - t0} preface_prefill_ms=${SystemClock.elapsedRealtime() - t1}")
            setState(EngineState.Ready)
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
            runCatching { engine?.close() }
            engine = null
            setState(EngineState.Failed("${e.javaClass.simpleName}: ${e.message}"))
            main.post { loadedPath = null }
        }
    }

    /** Engine thread. One turn; tokens are forwarded as they stream. */
    private fun generate(requestId: Long, text: String) {
        val e = engine
        val reply = StringBuilder()
        // Written on the runtime's callback thread; read here after chat() returns.
        var chunks = 0
        var capped = false
        var failure: String? = null
        try {
            if (e == null) error("engine not loaded")
            e.chat(text) { chunk ->
                if (!capped) {
                    reply.append(chunk)
                    chunks++
                    post(ProtocolMessage.Reply(requestId, ReplyEvent.Token(chunk)))
                    if (ReplyOutcome.reachedCap(chunks)) {
                        capped = true
                        e.cancel()
                    }
                }
            }
        } catch (ex: Exception) {
            failure = "${ex.javaClass.simpleName}: ${ex.message}"
        }
        val terminal = ReplyOutcome.terminal(reply.toString(), stopRequested, capped, failure)
        if (terminal is ReplyEvent.Error) Log.w(TAG, "turn failed: ${terminal.reason}")
        if (ReplyOutcome.needsNewConversation(terminal, capped)) {
            // A cancelled or failed turn poisons the conversation (LiteRtLmEngine.cancel), so the
            // next turn needs a fresh one. The reply reaches the UI first; Ready comes after.
            // TODO(#38): rebuild with the last turns instead of the preface only.
            post(ProtocolMessage.Reply(requestId, terminal))
            reset()
            if (state == EngineState.Generating) setState(EngineState.Ready)
        } else {
            // Ready first, so a client that gets the terminal event can send right away.
            setState(EngineState.Ready)
            post(ProtocolMessage.Reply(requestId, terminal))
        }
        main.post {
            if (activeRequestId == requestId) activeRequestId = null
            stopSelf()
        }
    }

    /** Engine thread. Fresh conversation: system prompt and primer only. */
    private fun reset() {
        try {
            val t0 = SystemClock.elapsedRealtime()
            checkNotNull(engine) { "engine not loaded" }.openChat(systemPrompt())
            Log.i(TAG, "conversation reopened, preface_prefill_ms=${SystemClock.elapsedRealtime() - t0}")
        } catch (e: Exception) {
            setState(EngineState.Failed("${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    private fun systemPrompt(): String = assets.open(SYSTEM_PROMPT_ASSET).bufferedReader().use { it.readText() }.trim()

    /** Any thread. */
    private fun setState(newState: EngineState) {
        state = newState
        post(ProtocolMessage.State(newState))
    }

    /** Any thread. Sends in order on the main thread. */
    private fun post(message: ProtocolMessage) {
        val envelope = InferenceProtocol.encode(message)
        main.post { deliver(envelope) }
    }

    private fun deliver(envelope: Envelope) {
        val target = client ?: return
        try {
            target.send(envelope.toMessage())
        } catch (e: RemoteException) {
            client = null
        }
    }

    private companion object {
        const val TAG = "BibiInference"
        const val SYSTEM_PROMPT_ASSET = "system_bibi.txt"
    }
}
