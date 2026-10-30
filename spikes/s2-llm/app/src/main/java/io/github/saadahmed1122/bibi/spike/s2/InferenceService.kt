package io.github.saadahmed1122.bibi.spike.s2

import android.app.ActivityManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.os.SystemClock
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Runs the benchmark in the `:inference` process (manifest `android:process`), PRD §11.3.
 *
 * The UI binds with a [Messenger] (developer.android.com, "Bound services overview" → "Use a
 * Messenger"). While a run is going the service is also *started*, so a rebinding activity can't
 * end the run. All engine calls happen on one thread ([engineThread], PRD §12.7).
 */
class InferenceService : Service() {

    private val engineThread: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "llm-engine") }
    private val main = Handler(Looper.getMainLooper())
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { handle(it); true })

    /** Main thread only. */
    private var client: Messenger? = null

    @Volatile private var status = "Idle. Pick a model."

    @Volatile private var runFile: String? = null

    @Volatile private var busy = false

    @Volatile private var stopRequested = false

    @Volatile private var engine: LlmEngine? = null

    override fun onBind(intent: Intent): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onDestroy() {
        stopRequested = true
        engine?.cancel()
        engineThread.shutdown()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        when (msg.what) {
            MSG_REGISTER -> {
                client = msg.replyTo
                sendStatus()
            }
            MSG_START -> {
                val path = msg.data.getString(KEY_PATH)
                val mode = msg.data.getString(KEY_MODE) ?: MODE_SMOKE
                if (busy || path == null) {
                    sendStatus()
                    return
                }
                busy = true
                stopRequested = false
                startService(Intent(this, InferenceService::class.java))
                engineThread.execute { run(path, mode) }
            }
            MSG_STOP -> {
                stopRequested = true
                engine?.cancel()
            }
        }
    }

    private fun setStatus(text: String) {
        status = text
        main.post { sendStatus() }
    }

    private fun sendStatus() {
        val reply = Message.obtain(null, MSG_STATUS)
        reply.data = Bundle().apply {
            putString(KEY_STATUS, status)
            putString(KEY_RUN_FILE, runFile)
            putBoolean(KEY_BUSY, busy)
        }
        try {
            client?.send(reply)
        } catch (e: RemoteException) {
            client = null
        }
    }

    /**
     * Engine thread. One run: load, then each prompt once, then stop. Soak and smoke use a fresh
     * conversation per prompt; chat mode keeps one conversation (system prompt prefilled once,
     * untimed) and resets it, untimed, before a turn could overflow the KV cache.
     */
    private fun run(path: String, mode: String) {
        val log = BenchLog(this)
        val set = PromptSet.parse(assets.open(PROMPTS_ASSET).bufferedReader().use { it.readText() })
        val chat = mode == MODE_CHAT
        val prompts = if (mode == MODE_SOAK || chat) set.prompts else set.prompts.take(SMOKE_PROMPTS)
        val chatSystem = if (chat) assets.open(CHAT_SYSTEM_ASSET).bufferedReader().use { it.readText() }.trim() else ""
        val modelFile = File(path)
        val model = modelFile.nameWithoutExtension
        val file = log.newRunFile(model, mode)
        val runId = System.currentTimeMillis().toString()
        runFile = file.name
        fun row(kind: String, detail: String = "", fields: Map<String, String> = emptyMap()) =
            log.write(file, runId, kind, detail, model, mode, fields)

        val llm: LlmEngine = LiteRtLmEngine(cacheDir.path, set.system, maxNumTokens = if (chat) CHAT_CONTEXT_TOKENS else null)
        engine = llm
        val totalRamMb = ActivityManager.MemoryInfo()
            .also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }.totalMem / (1024 * 1024)
        row(
            "start",
            "file=${modelFile.name} bytes=${modelFile.length()} device=${Build.MANUFACTURER} ${Build.MODEL} " +
                "android=${Build.VERSION.RELEASE} api=${Build.VERSION.SDK_INT} ram_total_mb=$totalRamMb " +
                "lmk_report=${ActivityManager.isLowMemoryKillReportSupported()} prompts=${prompts.size} " +
                "max_new=$MAX_NEW_TOKENS runtime=${llm.label}" +
                if (chat) " system=$CHAT_SYSTEM_ASSET context=$CHAT_CONTEXT_TOKENS" else "",
        )
        try {
            setStatus("Loading $model …")
            val loadStart = SystemClock.elapsedRealtime()
            llm.load(path)
            row("load", fields = mapOf("load_ms" to (SystemClock.elapsedRealtime() - loadStart).toString()) + memory())

            // Chat mode: tokens in the KV cache, turns in the current conversation, last turn failed.
            var used = 0
            var turns = 0
            var failed = false

            /** Opens the chat with the preface prefilled; untimed for the gate, logged as its own row. */
            fun openChat(kind: String, detail: String) {
                setStatus("$model: prefilling system prompt ($kind) …")
                val t0 = SystemClock.elapsedRealtimeNanos()
                val tokens = llm.openChat(chatSystem, CHAT_PRIMER_USER, CHAT_PRIMER_REPLY, MAX_NEW_TOKENS)
                val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
                // Unknown counts as not prefilled: Summary marks the run invalid on an empty ctx_tokens.
                used = tokens ?: Metrics.turnTokenEstimate(chatSystem.length + CHAT_PRIMER_USER.length + CHAT_PRIMER_REPLY.length)
                turns = 0
                failed = false
                row(
                    kind,
                    detail,
                    mapOf(
                        "total_ms" to fmt(ms, 1),
                        "prefill_tokens" to (tokens?.toString() ?: ""),
                        "ctx_tokens" to (tokens?.toString() ?: ""),
                    ) + memory(),
                )
            }
            if (chat) openChat("preface", "system=$CHAT_SYSTEM_ASSET chars=${chatSystem.length} primer=1 context=$CHAT_CONTEXT_TOKENS")

            for ((i, prompt) in prompts.withIndex()) {
                if (stopRequested) break
                if (chat && (failed || Metrics.needsContextReset(used, turns, prompt.text.length, MAX_NEW_TOKENS, CHAT_CONTEXT_TOKENS))) {
                    openChat("conv_reset", "before=${i + 1} used=$used turns=$turns${if (failed) " after_error" else ""}")
                }
                setStatus("$model: prompt ${i + 1}/${prompts.size} … ${lastLine.orEmpty()}")
                val stream = Stream()
                val start = SystemClock.elapsedRealtimeNanos()
                val counts = try {
                    val onToken: (String) -> Unit = { stream.chunk(SystemClock.elapsedRealtimeNanos()) }
                    if (chat) llm.chat(prompt.text, onToken) else llm.generate(prompt.text, MAX_NEW_TOKENS, onToken)
                } catch (e: Throwable) {
                    if (stopRequested) break
                    failed = true
                    row("error", "${e.javaClass.simpleName}: ${e.message}", mapOf("prompt_idx" to "${i + 1}", "prompt_id" to prompt.id) + memory())
                    continue
                }
                val end = SystemClock.elapsedRealtimeNanos()
                if (stopRequested) break // cut short by Stop: not a complete prompt
                val engineTokens = counts.decodeTokens?.takeIf { it > 0 }
                val tokens = engineTokens ?: stream.chunks
                val ttft = if (stream.chunks > 0) Metrics.ttftMs(start, stream.firstNs) else null
                val tps = Metrics.decodeTokensPerSecond(tokens, stream.firstNs, stream.lastNs)
                turns++
                used = counts.contextTokens ?: (used + Metrics.turnTokenEstimate(prompt.text.length) + tokens)
                row(
                    "prompt",
                    fields = mapOf(
                        "prompt_idx" to "${i + 1}",
                        "prompt_id" to prompt.id,
                        "prompt_chars" to prompt.text.length.toString(),
                        "prefill_tokens" to (counts.prefillTokens?.toString() ?: ""),
                        "decode_tokens" to tokens.toString(),
                        "tokens_source" to if (engineTokens != null) "engine" else "chunks",
                        "chunks" to stream.chunks.toString(),
                        "ttft_ms" to fmt(ttft, 1),
                        "decode_tps" to fmt(tps, 2),
                        "total_ms" to fmt((end - start) / 1e6, 1),
                        "eng_ttft_ms" to fmt(counts.ttftMs, 1),
                        "eng_decode_tps" to fmt(counts.decodeTokensPerSecond, 2),
                        "ctx_tokens" to if (chat) used.toString() else (counts.contextTokens?.toString() ?: ""),
                    ) + memory(),
                )
                lastLine = "last: TTFT ${fmt(ttft, 0)} ms, ${fmt(tps, 1)} tok/s"
            }
            row(if (stopRequested) "cancelled" else "stop", fields = memory())
            setStatus(if (stopRequested) "Stopped by user. $model" else "Done: $model. ${lastLine.orEmpty()}")
        } catch (e: Throwable) {
            row("error", "${e.javaClass.simpleName}: ${e.message}")
            row("aborted")
            setStatus("Failed: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            runCatching { llm.close() }
            engine = null
            lastLine = null
            busy = false
            main.post {
                sendStatus()
                stopSelf()
            }
        }
    }

    @Volatile private var lastLine: String? = null

    /** This process's memory. Debug.getPss() is slow (tens of ms), so it runs between prompts only. */
    private fun memory(): Map<String, String> {
        val status = runCatching { File("/proc/self/status").readText() }.getOrDefault("")
        return mapOf(
            "pss_kb" to Debug.getPss().toString(),
            "rss_kb" to (Metrics.procStatusKb(status, "VmRSS")?.toString() ?: ""),
            "rss_peak_kb" to (Metrics.procStatusKb(status, "VmHWM")?.toString() ?: ""),
        )
    }

    /** Chunk timestamps for one prompt; written on the runtime's callback thread. */
    private class Stream {
        @Volatile var chunks = 0
        @Volatile var firstNs = 0L
        @Volatile var lastNs = 0L

        fun chunk(now: Long) {
            if (chunks == 0) firstNs = now
            lastNs = now
            chunks++
        }
    }

    companion object {
        const val MSG_REGISTER = 1
        const val MSG_START = 2
        const val MSG_STOP = 3
        const val MSG_STATUS = 10

        const val KEY_PATH = "path"
        const val KEY_MODE = "mode"
        const val KEY_STATUS = "status"
        const val KEY_RUN_FILE = "run_file"
        const val KEY_BUSY = "busy"

        /** The 50-prompt gate run. */
        const val MODE_SOAK = "soak"

        /**
         * The 50 prompts as consecutive turns of one conversation, system prompt prefilled once
         * (untimed). Judged separately; whether it decides the S2 gate is [OPEN] (SPEC.md §2.1).
         */
        const val MODE_CHAT = "chat"

        /** A 3-prompt check that the model loads and streams; never valid for the gate. */
        const val MODE_SMOKE = "smoke"

        const val SMOKE_PROMPTS = 3
        const val MAX_NEW_TOKENS = 128
        const val PROMPTS_ASSET = "prompts.tsv"

        /** Chat mode's system prompt: Bibi persona + remembered facts, ~250–350 tokens. */
        const val CHAT_SYSTEM_ASSET = "system_bibi.txt"

        /**
         * Chat mode's KV cache size, set explicitly so the reset budget matches the engine.
         * gemma3-1b-it-int4.litertlm has no max_num_tokens in its LlmMetadata, so 0.16.1's
         * engine_settings.cc default would also be 4096 [VERIFY on device; other models may differ].
         */
        const val CHAT_CONTEXT_TOKENS = 4096

        /**
         * Chat mode's scripted first exchange, prefilled with the system prompt and never decoded.
         * Gemma 3 has no system turn, so without a user message the preface renders to only BOS
         * and the system prompt would be prefilled inside prompt 1's timed TTFT (LiteRtLmEngine).
         */
        const val CHAT_PRIMER_USER = "Hi Bibi."
        const val CHAT_PRIMER_REPLY = "Hi! What can I help you with?"

        fun fmt(v: Double?, digits: Int): String = v?.let { "%.${digits}f".format(Locale.US, it) } ?: ""
    }
}
