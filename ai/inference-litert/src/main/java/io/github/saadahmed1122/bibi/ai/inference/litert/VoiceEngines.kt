package io.github.saadahmed1122.bibi.ai.inference.litert

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * sherpa-onnx 2.0.1 adapters (ported from the S3 spike, which measured them on the vivo): the only
 * file that imports the library. Models load from file paths (assetManager = null). Threads as
 * measured in S3: Parakeet 4, Piper 2. Not thread-safe: InferenceService's voice thread only.
 */
internal class ParakeetStt(dir: File) : AutoCloseable {
    private val recognizer = OfflineRecognizer(
        assetManager = null,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(dir, "encoder.int8.onnx").path,
                    decoder = File(dir, "decoder.int8.onnx").path,
                    joiner = File(dir, "joiner.int8.onnx").path,
                ),
                tokens = File(dir, "tokens.txt").path,
                numThreads = 4,
                provider = "cpu",
                modelType = "nemo_transducer",
            ),
            decodingMethod = "greedy_search",
        ),
    )

    /** [samples] are 16 kHz mono in -1..1. */
    fun transcribe(samples: FloatArray): String {
        val stream = recognizer.createStream()
        try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            return recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() = recognizer.release()

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}

/** Piper en_US amy low, played through an AudioTrack in this process. */
internal class PiperTts(dir: File) : AutoCloseable {
    private val tts = OfflineTts(
        assetManager = null,
        config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(dir, "en_US-amy-low.onnx").path,
                    tokens = File(dir, "tokens.txt").path,
                    // sherpa-onnx 2.x has no espeak-ng: English goes through the lexicon (S3).
                    lexicon = File(dir, "lexicon-en-us.txt").path,
                ),
                numThreads = 2,
                provider = "cpu",
            ),
        ),
    )

    @Volatile private var track: AudioTrack? = null

    @Volatile private var stopped = false

    /** Synthesizes and plays [text]; blocks until played or [stop]. Returns false when stopped. */
    fun speak(text: String): Boolean {
        stopped = false
        val audio = tts.generate(text = text, sid = 0, speed = 1.0f)
        if (stopped || audio.samples.isEmpty()) return !stopped
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(audio.sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(audio.sampleRate * 4 / 2)
            .build()
        track = t
        try {
            t.play()
            var at = 0
            val step = audio.sampleRate / 10
            while (at < audio.samples.size && !stopped) {
                val n = minOf(step, audio.samples.size - at)
                t.write(audio.samples, at, n, AudioTrack.WRITE_BLOCKING)
                at += n
            }
            if (!stopped) {
                // Let the last buffer drain before releasing.
                Thread.sleep((audio.sampleRate * 4 / 2) * 1000L / (audio.sampleRate * 4))
            }
        } finally {
            t.stop()
            t.release()
            track = null
        }
        return !stopped
    }

    /** Any thread: stops the sentence being played. */
    fun stop() {
        stopped = true
        track?.let { runCatching { it.pause(); it.flush() } }
    }

    override fun close() = tts.release()
}
