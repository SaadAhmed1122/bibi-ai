package io.github.saadahmed1122.bibi.spike.s3

import java.io.File

// Model catalog. Pure Kotlin (no Android), so it is JVM-testable.
// File names come from the sherpa-onnx docs (k2-fsa/sherpa, docs/source/onnx/...) and the
// v2.0.1 Android examples; every name the docs did not list for that exact package is a
// candidate list, and the first file that exists wins. Sizes and names: SPEC.md §5 [VERIFY].

enum class Task(val logName: String) { TTS("tts"), STT("stt") }

/** Model family; decides the gate and the PRD 0.7 fallback recommendation. */
enum class Family(val logName: String, val task: Task) {
    KOKORO("kokoro", Task.TTS),
    PIPER("piper", Task.TTS),
    STREAMING("streaming", Task.STT),
    PARAKEET("parakeet", Task.STT),
    ;

    companion object {
        fun fromLog(name: String): Family? = entries.firstOrNull { it.logName == name }
    }
}

/**
 * One benchmark model. [files] maps a role (model, voices, tokens, lexicon, encoder, decoder,
 * joiner) to candidate file names inside [dirName]; the first one that exists is used.
 */
class ModelSpec(
    val id: String,
    val label: String,
    val family: Family,
    val dirName: String,
    val files: Map<String, List<String>>,
    /** Speaker ID used unless the owner overrides it in the app. */
    val defaultSid: Int = 0,
    /** Download page or archive, for the "missing files" message. */
    val source: String,
)

/** Result of [Models.resolve]: absolute paths by role, or the roles with no file. */
class Resolution(val paths: Map<String, String>, val missing: List<String>) {
    val ready: Boolean get() = missing.isEmpty()
}

object Models {
    private const val TTS_RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models"
    private const val ASR_RELEASE = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models"

    /** English lexicon used by sherpa-onnx ≥ 2.0.0 for Kokoro and Piper (espeak-ng was removed). */
    const val LEXICON_EN_US = "lexicon-en-us.txt"

    val all: List<ModelSpec> = listOf(
        ModelSpec(
            id = "kokoro-en-v0_19",
            label = "Kokoro en v0.19 (fp32)",
            family = Family.KOKORO,
            dirName = "kokoro-en-v0_19",
            files = mapOf(
                "model" to listOf("model.onnx"),
                "voices" to listOf("voices.bin"),
                "tokens" to listOf("tokens.txt"),
                "lexicon" to listOf(LEXICON_EN_US),
            ),
            defaultSid = 2, // af_nicole, the speaker the sherpa docs use for their RTF test
            source = "$TTS_RELEASE/kokoro-en-v0_19.tar.bz2 + $TTS_RELEASE/$LEXICON_EN_US",
        ),
        ModelSpec(
            id = "kokoro-int8-multi-lang-v1_1",
            label = "Kokoro multi-lang v1.1 (int8)",
            family = Family.KOKORO,
            dirName = "kokoro-int8-multi-lang-v1_1",
            files = mapOf(
                "model" to listOf("model.int8.onnx", "model.onnx"),
                "voices" to listOf("voices.bin"),
                "tokens" to listOf("tokens.txt"),
                "lexicon" to listOf("lexicon-us-en.txt", LEXICON_EN_US),
            ),
            defaultSid = 0, // [VERIFY] that speaker 0 of v1.1 is an English voice
            source = "$TTS_RELEASE/kokoro-int8-multi-lang-v1_1.tar.bz2",
        ),
        ModelSpec(
            id = "vits-piper-en_US-amy-low",
            label = "Piper en_US amy low (fallback voice)",
            family = Family.PIPER,
            dirName = "vits-piper-en_US-amy-low",
            files = mapOf(
                "model" to listOf("en_US-amy-low.onnx"),
                "tokens" to listOf("tokens.txt"),
                "lexicon" to listOf(LEXICON_EN_US),
            ),
            source = "$TTS_RELEASE/vits-piper-en_US-amy-low.tar.bz2 + $TTS_RELEASE/$LEXICON_EN_US",
        ),
        ModelSpec(
            id = "streaming-zipformer-en-20M",
            label = "Streaming Zipformer en 20M (small)",
            family = Family.STREAMING,
            dirName = "sherpa-onnx-streaming-zipformer-en-20M-2023-02-17",
            files = mapOf(
                "encoder" to listOf("encoder-epoch-99-avg-1.int8.onnx", "encoder-epoch-99-avg-1.onnx"),
                "decoder" to listOf("decoder-epoch-99-avg-1.onnx", "decoder-epoch-99-avg-1.int8.onnx"),
                "joiner" to listOf("joiner-epoch-99-avg-1.int8.onnx", "joiner-epoch-99-avg-1.onnx"),
                "tokens" to listOf("tokens.txt"),
            ),
            source = "$ASR_RELEASE/sherpa-onnx-streaming-zipformer-en-20M-2023-02-17.tar.bz2",
        ),
        ModelSpec(
            id = "parakeet-tdt-0.6b-v2-int8",
            label = "Parakeet TDT 0.6B v2 (int8)",
            family = Family.PARAKEET,
            dirName = "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8",
            files = mapOf(
                "encoder" to listOf("encoder.int8.onnx"),
                "decoder" to listOf("decoder.int8.onnx"),
                "joiner" to listOf("joiner.int8.onnx"),
                "tokens" to listOf("tokens.txt"),
            ),
            source = "$ASR_RELEASE/sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8.tar.bz2",
        ),
    )

    fun byId(id: String): ModelSpec? = all.firstOrNull { it.id == id }

    fun forTask(task: Task): List<ModelSpec> = all.filter { it.family.task == task }

    /** Picks the first existing candidate per role under [modelsRoot]/[ModelSpec.dirName]. */
    fun resolve(spec: ModelSpec, modelsRoot: File, exists: (File) -> Boolean = { it.isFile }): Resolution {
        val dir = File(modelsRoot, spec.dirName)
        val paths = mutableMapOf<String, String>()
        val missing = mutableListOf<String>()
        for ((role, candidates) in spec.files) {
            val hit = candidates.map { File(dir, it) }.firstOrNull(exists)
            if (hit != null) paths[role] = hit.absolutePath else missing += "$role (${candidates.joinToString(" or ")})"
        }
        return Resolution(paths, missing)
    }
}
