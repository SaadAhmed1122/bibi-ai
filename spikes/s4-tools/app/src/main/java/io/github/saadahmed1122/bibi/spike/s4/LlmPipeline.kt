package io.github.saadahmed1122.bibi.spike.s4

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/** Result of a load. [llmMs] null = Gemma was not loaded by this call. */
data class LoadReport(val dmMs: Double, val llmMs: Double?, val pssKb: Long)

/** Everything one LLM-path request produced, before the Permission Engine. */
data class LlmRun(
    val router: RouterResult?,
    val dmMs: Double?,
    val selection: ToolSelection?,
    val route: LlmRouteResult?,
    val ttftMs: Double?,
    val llmMs: Double?,
    val error: String?,
    /** Pass 2 was the experimental Gemma tool call; false = slot extraction (the default). */
    val llmToolCall: Boolean,
)

/**
 * The real path after a shortcut miss: Decision Maker → pass 2 → the same parser/validator route.
 * Pass 2 is [SlotToolRouter] (deterministic slot extraction, Decision 2026-10-30) by default; with
 * `llmToolCall` it is the old experiment, Gemma 3 1B on LiteRT-LM shown one tool → [LlmToolRouter],
 * kept so the 2026-10-30 benchmark stays reproducible. Gemma is loaded only for that mode.
 * It never acts; the caller sends a [LlmRoute.Call] to the Permission Engine on the main thread.
 *
 * Threads (CLAUDE.md §5, PRD §12.7): one single-thread dispatcher per engine; models load once and
 * are reused; nothing runs on the main thread. Cancelling the caller's coroutine stops between
 * steps, and [cancel] also stops a running generation.
 */
class LlmPipeline(context: Context) {
    /** adb push target; null if shared storage isn't available. */
    val modelsDir: File? = context.getExternalFilesDir("models")
    val dmFile: File? get() = modelsDir?.let { File(it, DecisionRouter.MODEL_FILE) }
    val llmFile: File? get() = modelsDir?.let { File(it, LiteRtToolLlm.MODEL_FILE) }

    private val dmExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "bibi-s4-dm") }
    private val llmExecutor: ExecutorService = Executors.newSingleThreadExecutor { Thread(it, "bibi-s4-llm") }
    private val dmDispatcher: ExecutorCoroutineDispatcher = dmExecutor.asCoroutineDispatcher()
    private val llmDispatcher: ExecutorCoroutineDispatcher = llmExecutor.asCoroutineDispatcher()

    private val router = DecisionRouter(context)
    private val llm = LiteRtToolLlm(context.cacheDir.absolutePath)
    private val loadLock = Mutex()

    @Volatile
    private var dmLoaded = false

    @Volatile
    private var llmLoaded = false

    /** The models [llmToolCall] mode needs are loaded. */
    fun isLoaded(llmToolCall: Boolean): Boolean = dmLoaded && (!llmToolCall || llmLoaded)

    fun filesPresent(llmToolCall: Boolean): Boolean = dmFile?.isFile == true && (!llmToolCall || llmFile?.isFile == true)

    /**
     * Loads what [llmToolCall] mode needs (the Decision Maker; Gemma too if [llmToolCall]) once;
     * null if nothing was left to load. Throws if a file is missing or a load fails.
     */
    suspend fun load(llmToolCall: Boolean): LoadReport? = loadLock.withLock {
        if (isLoaded(llmToolCall)) return@withLock null
        val dmMs = if (!dmLoaded) {
            val dm = checkNotNull(dmFile?.takeIf { it.isFile }) { "missing ${DecisionRouter.MODEL_FILE} in $modelsDir" }
            withContext(dmDispatcher) { timed { router.load(dm.absolutePath) } }.also { dmLoaded = true }
        } else {
            0.0
        }
        coroutineContext.ensureActive()
        val llmMs = if (llmToolCall && !llmLoaded) {
            val lm = checkNotNull(llmFile?.takeIf { it.isFile }) { "missing ${LiteRtToolLlm.MODEL_FILE} in $modelsDir" }
            withContext(llmDispatcher) { timed { llm.load(lm.absolutePath) } }.also { llmLoaded = true }
        } else {
            null
        }
        // Debug.getPss() takes tens of ms: off the main thread, outside any timed section.
        LoadReport(dmMs, llmMs, withContext(dmDispatcher) { Debug.getPss() })
    }

    /** One request. Never throws except for cancellation; failures come back in [LlmRun.error]. */
    suspend fun run(text: String, llmToolCall: Boolean, apps: AppResolver?): LlmRun {
        var router: RouterResult? = null
        var dmMs: Double? = null
        var selection: ToolSelection? = null
        try {
            load(llmToolCall)
            val t0 = SystemClock.elapsedRealtimeNanos()
            router = withContext(dmDispatcher) { this@LlmPipeline.router.evaluate(text) }
            dmMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            val sel = ToolRouting.select(router.label, router.confidence)
            selection = sel
            if (sel !is ToolSelection.Expose) return LlmRun(router, dmMs, sel, null, null, null, null, llmToolCall)
            coroutineContext.ensureActive()
            if (!llmToolCall) {
                // Pure and fast; on the router's thread so the main thread stays free.
                val result = withContext(dmDispatcher) { SlotToolRouter.route(sel, text, apps) }
                return LlmRun(router, dmMs, sel, result, null, null, null, false)
            }
            return withContext(llmDispatcher) {
                llm.open(sel.tool).use { session ->
                    val result = LlmToolRouter.route(session.send(text), sel.exposed) { repair -> session.send(repair) }
                    LlmRun(router, dmMs, sel, result, session.firstTtftMs, session.totalMs, null, true)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return LlmRun(router, dmMs, selection, null, null, null, "${e.javaClass.simpleName}: ${e.message}", llmToolCall)
        }
    }

    /** PSS of this process in kB. Slow (tens of ms): call it off the main thread, outside timed sections. */
    suspend fun pssKb(): Long = withContext(dmDispatcher) { Debug.getPss() }

    fun cancel() = llm.cancel()

    /** Releases both models on their own threads, after any running work, then stops the threads. */
    fun close() {
        llm.cancel()
        dmExecutor.execute { router.close() }
        llmExecutor.execute { llm.close() }
        dmExecutor.shutdown()
        llmExecutor.shutdown()
    }

    private inline fun timed(block: () -> Unit): Double {
        val t0 = SystemClock.elapsedRealtimeNanos()
        block()
        return (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
    }
}
