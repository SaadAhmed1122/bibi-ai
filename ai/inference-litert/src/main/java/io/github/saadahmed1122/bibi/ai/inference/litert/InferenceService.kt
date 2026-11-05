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
import io.github.saadahmed1122.bibi.ai.inference.api.Pcm16
import io.github.saadahmed1122.bibi.ai.inference.api.Persona
import io.github.saadahmed1122.bibi.ai.inference.api.ProtocolMessage
import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import io.github.saadahmed1122.bibi.ai.inference.api.RouteResult
import io.github.saadahmed1122.bibi.ai.inference.api.toEnvelope
import io.github.saadahmed1122.bibi.ai.inference.api.toMessage
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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
    private val voiceThread = Executors.newSingleThreadExecutor { Thread(it, "voice") }
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

    /** True while a memory summary runs; a new message cancels it (SPEC-memory.md). */
    @Volatile private var summarizing = false

    /** Written on the engine thread; read on main for [LiteRtLmEngine.cancel]. */
    @Volatile private var engine: LiteRtLmEngine? = null

    /** Main thread only. The imported voice pack folder, "" when there is none. */
    private var voiceDir = ""

    /** Voice thread only. Loaded on first use, released after [VOICE_IDLE_MS] (memory check, #72). */
    private var stt: ParakeetStt? = null
    private var tts: PiperTts? = null

    /** Read from the main thread for [ProtocolMessage.SpeakStop]. */
    @Volatile private var speaking: PiperTts? = null

    /** Main thread only. Audio of the push-to-talk in progress, by request id. */
    private val listening = HashMap<Long, MutableList<FloatArray>>()
    private val releaseVoice = Runnable { voiceThread.execute { releaseVoiceEngines() } }

    /** Pinned memory notes; read on the engine thread whenever a conversation opens. */
    @Volatile private var memoryNotes = ""

    /** Main thread only. The personality preset; clients send it before Load. */
    private var persona = Persona.FRIENDLY

    /** Main thread only. Set while the router is loading or loaded; cleared when its load fails. */
    private var routerPath: String? = null

    /** Router thread only. */
    private var router: DecisionRouter? = null

    /** Engine thread only. Set once the system prompt is read in [load]. */
    private var budget: ContextBudget? = null

    /** Engine thread only. */
    private lateinit var systemPrompt: String

    /**
     * Opened when the chat model's first load ends (loaded or failed). The router waits for it, so
     * LiteRT-LM and MediaPipe never initialize at the same time: two native crashes (#150) were
     * Gemma's createConversation running right after a concurrent router load.
     */
    private val chatLoadSettled = CountDownLatch(1)

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
        main.removeCallbacks(releaseVoice)
        speaking?.stop()
        voiceThread.execute { releaseVoiceEngines() }
        voiceThread.shutdown()
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
                val p = persona
                engineThread.execute { load(m.modelPath, p) }
            }
            is ProtocolMessage.Send -> {
                // The user comes first: a running summary is cancelled, and this turn queues behind it.
                if (summarizing) engine?.cancel()
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
            is ProtocolMessage.SetVoice -> voiceDir = m.dir
            is ProtocolMessage.SttStart -> {
                listening[m.requestId] = mutableListOf()
                // Load while the user is still talking (Parakeet takes about 10 s, S3).
                val dir = voiceDir
                if (dir.isNotEmpty()) voiceThread.execute { sttEngine(dir) }
            }
            is ProtocolMessage.SttChunk -> listening[m.requestId]?.add(Pcm16.decode(m.pcm))
            is ProtocolMessage.SttEnd -> {
                val chunks = listening.remove(m.requestId)
                val dir = voiceDir
                when {
                    chunks == null -> post(ProtocolMessage.Transcript(m.requestId, null, "not listening"))
                    dir.isEmpty() -> post(ProtocolMessage.Transcript(m.requestId, null, REASON_NO_VOICE))
                    else -> voiceThread.execute { transcribe(m.requestId, dir, chunks) }
                }
            }
            is ProtocolMessage.Speak -> {
                val dir = voiceDir
                if (dir.isEmpty()) post(ProtocolMessage.Spoken(m.requestId, REASON_NO_VOICE)) else voiceThread.execute { speak(m.requestId, dir, m.text) }
            }
            ProtocolMessage.SpeakStop -> speaking?.stop()
            is ProtocolMessage.Summarize -> {
                if (state != EngineState.Ready || activeRequestId != null || summarizing) {
                    post(ProtocolMessage.Summary(m.requestId, null, REASON_BUSY))
                } else {
                    summarizing = true
                    engineThread.execute { summarize(m.requestId, m.transcript) }
                }
            }
            // No reopen: the notes join the next conversation (after a reset, a preset change or a restart).
            is ProtocolMessage.SetMemory -> memoryNotes = m.notes
            is ProtocolMessage.SetPersona -> {
                val next = PersonaPrompt.normalize(m.persona)
                val reopen = PersonaPrompt.needsReopen(persona, next, modelLoaded = loadedPath != null)
                persona = next
                // Queued behind a running reply or load on the engine thread.
                if (reopen) engineThread.execute { switchPersona(next) }
            }
            // Service -> client messages and unknown codes are ignored.
            is ProtocolMessage.State, is ProtocolMessage.Reply, is ProtocolMessage.Routed, is ProtocolMessage.Summary,
            is ProtocolMessage.Transcript, is ProtocolMessage.Spoken, null -> Unit
        }
    }

    /** Engine thread. Loads the model and prefills the [persona]'s system prompt and primer. */
    private fun load(path: String, persona: String) {
        try {
            require(File(path).isFile) { "model file not found: $path" }
            val e = LiteRtLmEngine(cacheDir.path)
            engine = e
            val t0 = SystemClock.elapsedRealtime()
            e.load(path)
            val t1 = SystemClock.elapsedRealtime()
            systemPrompt = prompt(persona)
            // Room for the longer of the two prompts plus the notes, so a preset or notes change keeps the budget and its turns.
            val longest = maxOf(prompt(Persona.FRIENDLY).length, prompt(Persona.CONCISE).length) + PersonaPrompt.MAX_NOTES_CHARS + 2
            budget = ContextBudget(
                prefaceChars = longest + LiteRtLmEngine.PRIMER_USER.length + LiteRtLmEngine.PRIMER_REPLY.length,
            )
            e.openChat(PersonaPrompt.compose(systemPrompt, memoryNotes))
            Log.i(TAG, "load_ms=${t1 - t0} preface_prefill_ms=${SystemClock.elapsedRealtime() - t1}")
            setState(EngineState.Ready)
        } catch (e: Exception) {
            Log.w(TAG, "load failed", e)
            runCatching { engine?.close() }
            engine = null
            setState(EngineState.Failed("${e.javaClass.simpleName}: ${e.message}"))
            main.post { loadedPath = null }
        } finally {
            chatLoadSettled.countDown()
        }
    }

    private fun prompt(persona: String): String =
        assets.open(PersonaPrompt.asset(persona)).bufferedReader().use { it.readText() }.trim()

    /** Engine thread. A new preset: the conversation reopens with its prompt and the carried turns. */
    private fun switchPersona(persona: String) {
        if (engine == null || state is EngineState.Failed) return // a later load reads the new preset
        systemPrompt = prompt(persona)
        Log.i(TAG, "persona=$persona")
        reset()
    }

    /** Router thread. Loads the Decision Maker and precomputes its choices (about 13 s on the vivo, S4). */
    private fun loadRouter(path: String) {
        try {
            require(File(path).isFile) { "router model not found: $path" }
            val w0 = SystemClock.elapsedRealtime()
            val settled = chatLoadSettled.await(ROUTER_WAIT_S, TimeUnit.SECONDS)
            Log.i(TAG, "router waited_ms=${SystemClock.elapsedRealtime() - w0} chat_settled=$settled")
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

    /** Voice thread. The recognizer, loaded on first use. */
    private fun sttEngine(dir: String): ParakeetStt = stt ?: run {
        val t0 = SystemClock.elapsedRealtime()
        ParakeetStt(File(dir, VoicePackDirs.STT)).also {
            stt = it
            Log.i(TAG, "stt load_ms=${SystemClock.elapsedRealtime() - t0}")
        }
    }

    /** Voice thread. */
    private fun transcribe(requestId: Long, dir: String, chunks: List<FloatArray>) {
        // Tap-to-talk ends a quiet turn with no audio (#118): nothing to decode (Parakeet throws on 0 frames).
        val result = if (chunks.all { it.isEmpty() }) ProtocolMessage.Transcript(requestId, "") else try {
            val samples = FloatArray(chunks.sumOf { it.size }).also { out ->
                var at = 0
                for (c in chunks) { c.copyInto(out, at); at += c.size }
            }
            val t0 = SystemClock.elapsedRealtime()
            val text = sttEngine(dir).transcribe(samples)
            val ms = SystemClock.elapsedRealtime() - t0
            // Loudness only (no content): an all-zero signal means Android silenced the microphone.
            val rms = kotlin.math.sqrt(samples.fold(0.0) { a, x -> a + x * x } / samples.size.coerceAtLeast(1))
            Log.i(TAG, "stt audio_ms=${samples.size * 1000L / ParakeetStt.SAMPLE_RATE} ms=$ms chars=${text.length} rms=${"%.5f".format(rms)}")
            ProtocolMessage.Transcript(requestId, text)
        } catch (e: Exception) {
            Log.w(TAG, "stt failed", e)
            ProtocolMessage.Transcript(requestId, null, "${e.javaClass.simpleName}: ${e.message}")
        }
        post(result)
        scheduleVoiceRelease()
    }

    /** Voice thread. Speaks one sentence or short text; blocks until it has played. */
    private fun speak(requestId: Long, dir: String, text: String) {
        val reason = try {
            val engine = tts ?: run {
                val t0 = SystemClock.elapsedRealtime()
                PiperTts(File(dir, VoicePackDirs.TTS)).also {
                    tts = it
                    Log.i(TAG, "tts load_ms=${SystemClock.elapsedRealtime() - t0}")
                }
            }
            speaking = engine
            val t0 = SystemClock.elapsedRealtime()
            val finished = engine.speak(text)
            Log.i(TAG, "tts chars=${text.length} ms=${SystemClock.elapsedRealtime() - t0} finished=$finished")
            if (finished) "" else "stopped"
        } catch (e: Exception) {
            Log.w(TAG, "tts failed", e)
            "${e.javaClass.simpleName}: ${e.message}"
        } finally {
            speaking = null
        }
        post(ProtocolMessage.Spoken(requestId, reason))
        scheduleVoiceRelease()
    }

    private fun scheduleVoiceRelease() {
        main.removeCallbacks(releaseVoice)
        main.postDelayed(releaseVoice, VOICE_IDLE_MS)
    }

    /** Voice thread. */
    private fun releaseVoiceEngines() {
        if (stt != null || tts != null) Log.i(TAG, "voice released")
        stt?.close()
        stt = null
        tts?.close()
        tts = null
    }

    /** Engine thread. A memory summary in a temporary conversation; the chat conversation is untouched. */
    private fun summarize(requestId: Long, transcript: String) {
        val t0 = SystemClock.elapsedRealtime()
        val result = try {
            val e = checkNotNull(engine) { "engine not loaded" }
            val instruction = assets.open(SUMMARY_ASSET).bufferedReader().use { it.readText() }.trim()
            ProtocolMessage.Summary(requestId, e.oneShot(instruction, transcript.take(MAX_TRANSCRIPT_CHARS), SUMMARY_MAX_TOKENS))
        } catch (ex: Exception) {
            ProtocolMessage.Summary(requestId, null, if (activeRequestId != null) REASON_CANCELLED else "${ex.javaClass.simpleName}: ${ex.message}")
        } finally {
            summarizing = false
        }
        Log.i(TAG, "summary ms=${SystemClock.elapsedRealtime() - t0} ok=${result.text != null} chars=${result.text?.length ?: 0}")
        post(result)
    }

    /** Engine thread. One turn; tokens are forwarded as they stream. */
    private fun generate(requestId: Long, text: String) {
        val e = engine
        val reply = StringBuilder()
        // Written on the runtime's callback thread; read here after chat() returns.
        var chunks = 0
        var capped = false
        // Set when the reply started looping (#107): the text to keep instead of the loop.
        var unlooped: String? = null
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
                    } else {
                        RepetitionGuard.check(reply.toString())?.let {
                            unlooped = it
                            capped = true
                            e.cancel()
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            failure = "${ex.javaClass.simpleName}: ${ex.message}"
        }
        val kept = unlooped ?: reply.toString()
        if (unlooped != null) Log.i(TAG, "loop cut after chunks=$chunks kept_chars=${kept.length}")
        val terminal = ReplyOutcome.terminal(kept, stopRequested, capped, failure)
        // Service-side timing for the chat checks (Checkpoint B); excludes the IPC hop to the UI.
        Log.i(TAG, "turn ttft_ms=$firstTokenMs chunks=$chunks total_ms=${SystemClock.elapsedRealtime() - t0} end=${endLabel(terminal)}")
        if (terminal is ReplyEvent.Error) Log.w(TAG, "turn failed: ${terminal.reason}")
        // A stopped or failed turn is carried over with the text the user saw.
        budget?.record(text, kept)
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
        e.openChat(PersonaPrompt.compose(systemPrompt, memoryNotes), carried)
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
        const val REASON_NO_ROUTER = "router not loaded"
        const val REASON_BUSY = "busy"
        const val REASON_NO_VOICE = "no voice pack"

        /** Voice models leave memory after 2 idle minutes (SPEC-voice.md). */
        const val VOICE_IDLE_MS = 120_000L

        /** Longest wait for the chat model before the router loads anyway (a normal load takes about 11 s). */
        const val ROUTER_WAIT_S = 120L
        const val REASON_CANCELLED = "cancelled by a new message"
        const val SUMMARY_ASSET = "summary_instruction.txt"

        /** About 600 estimated tokens of chat (prefill ~12 s on the vivo, only while idle). */
        const val MAX_TRANSCRIPT_CHARS = 1_800
        const val SUMMARY_MAX_TOKENS = 96
    }
}
