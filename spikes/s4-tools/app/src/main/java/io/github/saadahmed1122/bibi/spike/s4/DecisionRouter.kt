package io.github.saadahmed1122.bibi.spike.s4

import android.content.Context
import com.google.mediapipe.tasks.decision.ChoiceQuestion
import com.google.mediapipe.tasks.decision.DecisionMaker
import com.google.mediapipe.tasks.decision.DecisionMakerDelegate
import com.google.mediapipe.tasks.decision.DecisionMakerOptions

/** The router's answer as plain types. */
data class RouterResult(val label: String, val confidence: Float, val probabilities: Map<String, Float>)

/**
 * MediaPipe Decision Maker adapter: the ONLY file that imports com.google.mediapipe. It picks which
 * tool schema the LLM sees ([ToolRouting.CHOICES]); it never dispatches an action
 * (Decision 2026-10-30, PRD §13.4).
 *
 * API as exposed by tasks-decision 1.2.0, checked with javap on the AAR from Google Maven and the
 * Android guide (developers.google.com/edge/mediapipe/solutions/decision/decision_maker/android):
 * DecisionMakerOptions.builder() (modelPath, maxNumTokens, delegate), DecisionMaker.createFromOptions,
 * ChoiceQuestion.withDescriptions(options, temperature, instructions, scoringMode, normalizePrior),
 * prewarm(ChoiceQuestion), evaluate(String, ChoiceQuestion) → ChoiceResult(selectedKey,
 * probabilities, confidence). The 1.2.0 jar has no prewarmChoice/evaluateChoice (builder style).
 * createFromOptions uses modelPath as a file when it exists, else as an asset name (bytecode).
 *
 * Not thread-safe: call it from one thread (the caller's single-thread dispatcher).
 */
class DecisionRouter(context: Context) : AutoCloseable {
    private val appContext = context.applicationContext
    private var decisionMaker: DecisionMaker? = null

    // Defaults kept for temperature and scoringMode (LENGTH_NORMALIZED_LOG_PROB per the 1.2.0 bytecode).
    private val question = ChoiceQuestion.withDescriptions(
        options = ToolRouting.CHOICES,
        instructions = ToolRouting.INSTRUCTIONS,
        normalizePrior = true,
    )

    val isLoaded: Boolean get() = decisionMaker != null

    /** Loads the model and precomputes the option embeddings. Slow (model load): never on the main thread. */
    fun load(modelPath: String) {
        check(decisionMaker == null) { "already loaded" }
        val options = DecisionMakerOptions.builder().apply {
            this.modelPath = modelPath
            maxNumTokens = MAX_NUM_TOKENS
            // CPU only: PRD 0.5 says not to rely on GPU/NPU on the floor device.
            delegate = DecisionMakerDelegate.CPU
        }.build()
        val dm = DecisionMaker.createFromOptions(appContext, options)
        try {
            dm.prewarm(question)
        } catch (t: Throwable) {
            dm.close()
            throw t
        }
        decisionMaker = dm
    }

    fun evaluate(text: String): RouterResult {
        val dm = checkNotNull(decisionMaker) { "load() first" }
        val r = dm.evaluate(text, question)
        // What `confidence` measures is not defined in the guide [VERIFY]; it is logged next to the probabilities.
        return RouterResult(r.selectedKey, r.confidence, r.probabilities)
    }

    override fun close() {
        decisionMaker?.close()
        decisionMaker = null
    }

    companion object {
        /** Must match app/build.gradle.kts. */
        const val LIBRARY_VERSION = "1.2.0"
        const val MODEL_FILE = "embeddinggemma-2-text-270m.litertlm"

        /** The guide's value. User requests are short; longer text is cut [VERIFY how]. */
        const val MAX_NUM_TOKENS = 512
    }
}
