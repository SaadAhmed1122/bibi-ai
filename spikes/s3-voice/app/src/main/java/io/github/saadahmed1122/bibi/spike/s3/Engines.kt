package io.github.saadahmed1122.bibi.spike.s3

// Runtime-neutral engine interfaces. Only SherpaEngines.kt knows the runtime library, so a
// second runtime could be benchmarked by adding one adapter file. Pure Kotlin.

/** Synthesized speech: mono samples in [-1, 1]. */
class TtsAudio(val samples: FloatArray, val sampleRate: Int) {
    val seconds: Double get() = if (sampleRate > 0) samples.size.toDouble() / sampleRate else 0.0
}

interface TtsEngine : AutoCloseable {
    /** Synthesizes [text] with speaker [sid] at normal speed. Blocks until all audio is ready. */
    fun synthesize(text: String, sid: Int): TtsAudio
}

interface SttEngine : AutoCloseable {
    /**
     * Transcribes a whole clip as fast as possible. Streaming engines get the clip in
     * [STREAM_CHUNK_MS] chunks, as a microphone would deliver it, but without waiting.
     */
    fun transcribe(clip: PcmClip): String

    companion object {
        const val STREAM_CHUNK_MS = 100
    }
}

/** Creates engines from resolved model files (role -> absolute path). */
interface EngineFactory {
    val runtimeName: String
    fun tts(spec: ModelSpec, files: Map<String, String>, threads: Int): TtsEngine
    fun stt(spec: ModelSpec, files: Map<String, String>, threads: Int): SttEngine
}
