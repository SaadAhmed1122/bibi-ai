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
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
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
 *
 * The action router ([DecisionRouter], SPEC-tools.md) is a second engine with its own thread,
 * [routerThread], so routing a message never waits behind a reply.
 */
class InferenceService : Service() {

    private val engineThread = Executors.newSingleThreadExecutor { Thread(it, "llm-engine") }
    private val routerThread = Executors.newSingleThreadExecutor { Thread(it, "router") }
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

    /** Main thread only. Set while the router is loading or loaded; cleared when its load fails. */
    private var routerPath: String? = null

    /** Router thread only. */
    private var router: DecisionRouter? = null

    /** Engine thread only. Set once the system prompt is read in [load]. */
    private var budget: ContextBudget? = null

    /** Engine thread only. */
    private lateinit var systemPrompt: String

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
        routerThread.execute {
            router?.close()
            router = null
        }
        routerThread.shutdown()
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
                engineThread.execute {
                    budget?.clear()
                    reset()
                }
            }
            is ProtocolMessage.LoadRouter -> if (routerPath == null) {
                routerPath = m.modelPath
                routerThread.execute { loadRouter(m.modelPath) }
            }
            is ProtocolMessage.Route -> routerThread.execute { route(m.requestId, m.text) }
            // Service -> client messages and unknown codes are ignored.
            is ProtocolMessage.State, is ProtocolMessage.Reply, is ProtocolMessage.Routed, null -> Unit
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
            systemPrompt = assets.open(SYSTEM_PROMPT_ASSET).bufferedReader().use { it.readText() }.trim()
            budget = ContextBudget(
                prefaceChars = systemPrompt.length + LiteRtLmEngine.PRIMER_USER.length + LiteRtLmEngine.PRIMER_REPLY.length,
            )
            e.openChat(systemPrompt)
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

    /** Router thread. Loads the Decision Maker and precomputes its choices (about 13 s on the vivo, S4). */
    private fun loadRouter(path: String) {
        try {
            require(File(path).isFile) { "router model not found: $path" }
            val t0 = SystemClock.elapsedRealtime()
            router = DecisionRouter(this).also { it.load(path) }
            Log.i(TAG, "router load_ms=${SystemClock.elapsedRealtime() - t0}")
        } catch (e: Exception) {
            Log.w(TAG, "router load failed", e)
            router = null
            main.post { routerPath = null }
        }
    }

    /** Router thread. A request that arrives while the router loads waits for it on this thread. */
    private fun route(requestId: Long, text: String) {
        val r = router
        val result = if (r == null) {
            RouteResult.Unavailable(REASON_NO_ROUTER)
        } else {
            try {
                val t0 = SystemClock.elapsedRealtime()
                val answer = r.evaluate(text)
                Log.i(TAG, "route label=${answer.label} confidence=${answer.confidence} ms=${SystemClock.elapsedRealtime() - t0}")
                RouteResult.Label(answer.label, answer.confidence)
            } catch (e: Exception) {
                Log.w(TAG, "route failed", e)
                RouteResult.Unavailable("${e.javaClass.simpleName}: ${e.message}")
            }
        }
        post(ProtocolMessage.Routed(requestId, result))
    }

    /** Engine thread. One turn; tokens are forwarded as they stream. */
    private fun generate(requestId: Long, text: String) {
        val e = engine
        val reply = StringBuilder()
        // Written on the runtime's callback thread; read here after chat() returns.
        var chunks = 0
        var capped = false
        var failure: String? = null
        val t0 = SystemClock.elapsedRealtime()
        var firstTokenMs = -1L
        try {
            if (e == null) error("engine not loaded")
            if (checkNotNull(budget).needsReset(text)) {
                // Lands in this turn's TTFT; rare (a few thousand tokens of chat between resets).
                Log.i(TAG, "context reset before turn, used_est=${budget?.usedTokens}")
                openConversation(e)
            }
            e.chat(text) { chunk ->
                if (!capped) {
                    if (chunks == 0) firstTokenMs = SystemClock.elapsedRealtime() - t0
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
        // Service-side timing for the chat checks (Checkpoint B); excludes the IPC hop to the UI.
        Log.i(TAG, "turn ttft_ms=$firstTokenMs chunks=$chunks total_ms=${SystemClock.elapsedRealtime() - t0} end=${endLabel(terminal)}")
        if (terminal is ReplyEvent.Error) Log.w(TAG, "turn failed: ${terminal.reason}")
        // A stopped or failed turn is carried over with the text the user saw.
        budget?.record(text, reply.toString())
        if (ReplyOutcome.needsNewConversation(terminal, capped)) {
            // A cancelled or failed turn poisons the conversation (LiteRtLmEngine.cancel), so the
            // next turn needs a fresh one, rebuilt with the recent turns. The reply reaches the UI
            // first; Ready comes after.
            post(ProtocolMessage.Reply(requestId, terminal))
            reset()
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

    /**
     * Engine thread. Fresh conversation with the recent turns (none after Clear). Reports Loading
     * while it prefills (about 7 s for the preface alone on the vivo), so clients don't offer Send.
     */
    private fun reset() {
        try {
            setState(EngineState.Loading)
            openConversation(checkNotNull(engine) { "engine not loaded" })
            setState(EngineState.Ready)
        } catch (e: Exception) {
            setState(EngineState.Failed("${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    /** Engine thread. Reopens the conversation with the turns [ContextBudget.reopen] picks. */
    private fun openConversation(e: LiteRtLmEngine) {
        val carried = checkNotNull(budget).reopen()
        val t0 = SystemClock.elapsedRealtime()
        e.openChat(systemPrompt, carried)
        Log.i(
            TAG,
            "conversation reopened, carried_turns=${carried.size} used_est=${budget?.usedTokens} " +
                "prefill_ms=${SystemClock.elapsedRealtime() - t0}",
        )
    }

    /** Fixed words, because R8 renames the event classes in release builds. */
    private fun endLabel(event: ReplyEvent): String = when (event) {
        is ReplyEvent.Done -> "Done"
        is ReplyEvent.Stopped -> "Stopped"
        is ReplyEvent.Error -> "Error"
        is ReplyEvent.Token -> "Token"
    }

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
        const val REASON_NO_ROUTER = "router not loaded"
    }
}
