package io.github.saadahmed1122.bibi.feature.chat

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The microphone, behind an interface so the chat logic is JVM-tested (SPEC-voice.md). */
interface AudioSource {
    /** Starts recording 16 kHz mono; [onChunk] gets about 0.5 s at a time, on a background thread. */
    fun start(onChunk: (FloatArray) -> Unit): Boolean

    /** Stops and delivers what is left as a last, shorter chunk. */
    fun stop()
}

/**
 * What the chat needs for voice (SPEC-voice.md): the microphone, whether the voice pack is ready, the
 * "read answers aloud" switch (saved by the caller through [setSpeakReplies]) and the notice shown
 * when push-to-talk heard nothing.
 */
class ChatVoice(
    val audio: AudioSource,
    val ready: Flow<Boolean>,
    val speakReplies: StateFlow<Boolean>,
    val setSpeakReplies: (Boolean) -> Unit,
    val notHeard: String,
    /** Numbers only (loudness, timings), never what was said: for tuning tap-to-talk on a phone. */
    val log: (String) -> Unit = {},
)

/** Groups recorded samples into [size]-sample chunks (0.5 s at 16 kHz) for the binder. */
class AudioChunker(private val size: Int = CHUNK_SAMPLES, private val emit: (FloatArray) -> Unit) {
    private val buf = FloatArray(size)
    private var n = 0

    fun add(samples: FloatArray, count: Int = samples.size) {
        var i = 0
        while (i < count) {
            val take = minOf(size - n, count - i)
            samples.copyInto(buf, n, i, i + take)
            n += take
            i += take
            if (n == size) {
                emit(buf.copyOf())
                n = 0
            }
        }
    }

    /** The remainder, if any. */
    fun flush() {
        if (n > 0) emit(buf.copyOf(n))
        n = 0
    }

    companion object {
        const val CHUNK_SAMPLES = 8_000
    }
}

/**
 * Cuts a streaming answer into sentences to speak (SPEC-voice.md: each finished sentence is spoken
 * while the rest streams). A sentence ends at . ! ? or a line break followed by a space or the end;
 * numbers like "3.5" and short abbreviations ("Dr.") don't end one.
 */
class SentenceSplitter {
    private val pending = StringBuilder()

    /** Adds streamed [text]; returns the sentences it completed. */
    fun add(text: String): List<String> {
        pending.append(text)
        val out = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < pending.length) {
            val c = pending[i]
            val next = pending.getOrNull(i + 1)
            val ends = (c == '\n') ||
                (c in ".!?" && next != null && next.isWhitespace() && !abbreviation(pending.substring(start, i)))
            if (ends) {
                pending.substring(start, i + 1).trim().takeIf { it.any(Char::isLetterOrDigit) }?.let(out::add)
                start = i + 1
            }
            i++
        }
        pending.delete(0, start)
        return out
    }

    /** What is left when the answer ends. */
    fun finish(): String? = pending.toString().trim().takeIf { it.any(Char::isLetterOrDigit) }.also { pending.clear() }

    private fun abbreviation(before: String): Boolean {
        val word = before.substringAfterLast(' ').trim()
        return word.length in 1..3 && word.first().isUpperCase() && word.lowercase() in ABBREVIATIONS
    }

    private companion object {
        val ABBREVIATIONS = setOf("dr", "mr", "mrs", "ms", "st", "vs", "etc", "no")
    }
}
