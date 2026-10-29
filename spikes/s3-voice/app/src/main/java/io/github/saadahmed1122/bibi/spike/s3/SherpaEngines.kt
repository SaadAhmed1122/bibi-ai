package io.github.saadahmed1122.bibi.spike.s3

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

/**
 * sherpa-onnx 2.0.1 adapter: the only file that imports the library (it comes from the AAR in
 * app/libs). API source: the v2.0.1 Kotlin API, which the AAR compiles from
 * github.com/k2-fsa/sherpa-onnx/tree/v2.0.1/sherpa-onnx/kotlin-api (Tts.kt, OnlineRecognizer.kt,
 * OfflineRecognizer.kt, OnlineStream.kt, OfflineStream.kt, FeatureConfig.kt), plus
 * kotlin-api-examples/test_online_asr.kt for the streaming tail padding and
 * android/SherpaOnnxTts MainActivity.kt for the lexicon-based English frontend.
 * Models load from file paths (assetManager = null), not from APK assets.
 */
object SherpaEngines : EngineFactory {
    const val VERSION = "2.0.1"
    override val runtimeName = "sherpa-onnx $VERSION"

    /** Feature extraction for both recognizers; the Parakeet feature size is read from the model. */
    private val features = FeatureConfig(sampleRate = 16_000, featureDim = 80)

    override fun tts(spec: ModelSpec, files: Map<String, String>, threads: Int): TtsEngine {
        val model = when (spec.family) {
            // sherpa-onnx ≥ 2.0.0 has no espeak-ng (CHANGELOG 2.0.0, #4055): English text goes
            // through the lexicon file instead.
            Family.KOKORO -> OfflineTtsModelConfig(
                kokoro = OfflineTtsKokoroModelConfig(
                    model = files.getValue("model"),
                    voices = files.getValue("voices"),
                    tokens = files.getValue("tokens"),
                    lexicon = files.getValue("lexicon"),
                ),
                numThreads = threads,
                provider = "cpu",
            )
            Family.PIPER -> OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = files.getValue("model"),
                    tokens = files.getValue("tokens"),
                    lexicon = files.getValue("lexicon"),
                ),
                numThreads = threads,
                provider = "cpu",
            )
            else -> throw IllegalArgumentException("${spec.id} is not a TTS model")
        }
        val tts = OfflineTts(assetManager = null, config = OfflineTtsConfig(model = model))
        return object : TtsEngine {
            override fun synthesize(text: String, sid: Int): TtsAudio {
                val audio = tts.generate(text = text, sid = sid, speed = 1.0f)
                return TtsAudio(audio.samples, audio.sampleRate)
            }

            override fun close() = tts.release()
        }
    }

    override fun stt(spec: ModelSpec, files: Map<String, String>, threads: Int): SttEngine = when (spec.family) {
        Family.STREAMING -> streaming(files, threads)
        Family.PARAKEET -> parakeet(files, threads)
        else -> throw IllegalArgumentException("${spec.id} is not an STT model")
    }

    private fun streaming(files: Map<String, String>, threads: Int): SttEngine {
        val recognizer = OnlineRecognizer(
            assetManager = null,
            config = OnlineRecognizerConfig(
                featConfig = features,
                modelConfig = OnlineModelConfig(
                    transducer = OnlineTransducerModelConfig(
                        encoder = files.getValue("encoder"),
                        decoder = files.getValue("decoder"),
                        joiner = files.getValue("joiner"),
                    ),
                    tokens = files.getValue("tokens"),
                    numThreads = threads,
                    provider = "cpu",
                    modelType = "zipformer", // as in OnlineRecognizer.kt getModelConfig(type = 10)
                ),
                enableEndpoint = false, // one clip = one utterance
                decodingMethod = "greedy_search",
            ),
        )
        return object : SttEngine {
            override fun transcribe(clip: PcmClip): String {
                val stream = recognizer.createStream()
                try {
                    val chunk = clip.sampleRate * SttEngine.STREAM_CHUNK_MS / 1000
                    var from = 0
                    while (from < clip.samples.size) {
                        val to = minOf(from + chunk, clip.samples.size)
                        stream.acceptWaveform(clip.samples.copyOfRange(from, to), clip.sampleRate)
                        while (recognizer.isReady(stream)) recognizer.decode(stream)
                        from = to
                    }
                    // 0.6 s of silence flushes the last frames (kotlin-api-examples/test_online_asr.kt).
                    stream.acceptWaveform(FloatArray(clip.sampleRate * 6 / 10), clip.sampleRate)
                    stream.inputFinished()
                    while (recognizer.isReady(stream)) recognizer.decode(stream)
                    return recognizer.getResult(stream).text
                } finally {
                    stream.release()
                }
            }

            override fun close() = recognizer.release()
        }
    }

    private fun parakeet(files: Map<String, String>, threads: Int): SttEngine {
        val recognizer = OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = features,
                modelConfig = OfflineModelConfig(
                    transducer = OfflineTransducerModelConfig(
                        encoder = files.getValue("encoder"),
                        decoder = files.getValue("decoder"),
                        joiner = files.getValue("joiner"),
                    ),
                    tokens = files.getValue("tokens"),
                    numThreads = threads,
                    provider = "cpu",
                    modelType = "nemo_transducer", // as in OfflineRecognizer.kt getOfflineModelConfig(type = 30)
                ),
                decodingMethod = "greedy_search",
            ),
        )
        return object : SttEngine {
            override fun transcribe(clip: PcmClip): String {
                val stream = recognizer.createStream()
                try {
                    stream.acceptWaveform(clip.samples, clip.sampleRate)
                    recognizer.decode(stream)
                    return recognizer.getResult(stream).text
                } finally {
                    stream.release()
                }
            }

            override fun close() = recognizer.release()
        }
    }
}
