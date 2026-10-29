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

/** Result of loading both models. */
data class LoadReport(val dmMs: Double, val llmMs: Double, val pssKb: Long)

/** Everything one LLM-path request produced, before the Permission Engine. */
data class LlmRun(
    val router: RouterResult?,
    val dmMs: Double?,
    val selection: ToolSelection?,
    val route: LlmRouteResult?,
    val ttftMs: Double?,
    val llmMs: Double?,
    val error: String?,
)

/**
 * The real-LLM path after a shortcut miss: Decision Maker → (one tool) LiteRT-LM → [LlmToolRouter].
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
    var loaded: Boolean = false
        private set

    fun filesPresent(): Boolean = dmFile?.isFile == true && llmFile?.isFile == true

    /** Loads both models once; later calls return null at once. Throws if a file is missing or a load fails. */
    suspend fun load(): LoadReport? = loadLock.withLock {
        if (loaded) return@withLock null
        val dm = checkNotNull(dmFile?.takeIf { it.isFile }) { "missing ${DecisionRouter.MODEL_FILE} in $modelsDir" }
        val lm = checkNotNull(llmFile?.takeIf { it.isFile }) { "missing ${LiteRtToolLlm.MODEL_FILE} in $modelsDir" }
        val dmMs = withContext(dmDispatcher) { if (!router.isLoaded) timed { router.load(dm.absolutePath) } else 0.0 }
        coroutineContext.ensureActive()
        val llmMs = withContext(llmDispatcher) { if (!llm.isLoaded) timed { llm.load(lm.absolutePath) } else 0.0 }
        loaded = true
        // Debug.getPss() takes tens of ms: off the main thread, outside any timed section.
        LoadReport(dmMs, llmMs, withContext(dmDispatcher) { Debug.getPss() })
    }

    /** One request. Never throws except for cancellation; failures come back in [LlmRun.error]. */
    suspend fun run(text: String): LlmRun {
        var router: RouterResult? = null
        var dmMs: Double? = null
        var selection: ToolSelection? = null
        try {
            load()
            val t0 = SystemClock.elapsedRealtimeNanos()
            router = withContext(dmDispatcher) { this@LlmPipeline.router.evaluate(text) }
            dmMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6
            val sel = ToolRouting.select(router.label, router.confidence)
            selection = sel
            if (sel !is ToolSelection.Expose) return LlmRun(router, dmMs, sel, null, null, null, null)
            coroutineContext.ensureActive()
            return withContext(llmDispatcher) {
                llm.open(sel.tool).use { session ->
                    val result = LlmToolRouter.route(session.send(text), sel.exposed) { repair -> session.send(repair) }
                    LlmRun(router, dmMs, sel, result, session.firstTtftMs, session.totalMs, null)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return LlmRun(router, dmMs, selection, null, null, null, "${e.javaClass.simpleName}: ${e.message}")
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
