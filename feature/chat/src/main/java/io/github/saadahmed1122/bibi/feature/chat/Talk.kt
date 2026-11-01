package io.github.saadahmed1122.bibi.feature.chat

import kotlin.math.sqrt

/**
 * Hears when someone starts and stops speaking, from the loudness of each piece of audio, for
 * tap-to-talk (#118). The first [CALIBRATE_MS] set the room's noise level (people take longer than
 * that to start talking); speech is anything clearly louder, held for [MIN_SPEECH_MS]; while it lasts, half that loudness
 * still counts, so soft syllables don't end a sentence. The talk
 * turn ends after [END_SILENCE_MS] of quiet, or at [MAX_UTTERANCE_MS]; nothing at all for
 * [NO_SPEECH_MS] is [Event.NoSpeech]. Thresholds are first guesses from the vivo's room noise
 * (RMS 0.0042–0.0047) [VERIFY] with more voices and rooms.
 */
class Endpointer(private val rate: Int = 16_000) {
    enum class Event { SpeechStarted, SpeechEnded, NoSpeech }

    private var elapsedMs = 0
    private var calibrationSum = 0f
    private var calibrationFrames = 0
    private var noise = 0f
    private var loudMs = 0
    private var quietMs = 0
    private var speechStartMs = -1
    private var done = false

    /** Feeds one piece of audio; returns what it changed, if anything. */
    fun add(samples: FloatArray): Event? {
        if (done || samples.isEmpty()) return null
        val ms = samples.size * 1_000 / rate
        val level = rms(samples)
        elapsedMs += ms
        if (elapsedMs <= CALIBRATE_MS) {
            calibrationSum += level
            calibrationFrames++
            noise = calibrationSum / calibrationFrames
            return null
        }
        // Hysteresis: once someone is talking, softer syllables still count, down to half the bar.
        val loud = level > threshold() * if (speechStartMs >= 0) HOLD_FACTOR else 1f
        if (level <= threshold()) noise += (level - noise) * NOISE_ADAPT
        if (speechStartMs < 0) {
            loudMs = if (loud) loudMs + ms else 0
            if (loudMs >= MIN_SPEECH_MS) {
                speechStartMs = elapsedMs
                return Event.SpeechStarted
            }
            if (elapsedMs >= NO_SPEECH_MS) return finish(Event.NoSpeech)
            return null
        }
        quietMs = if (loud) 0 else quietMs + ms
        if (quietMs > END_SILENCE_MS || elapsedMs - speechStartMs >= MAX_UTTERANCE_MS) return finish(Event.SpeechEnded)
        return null
    }

    private fun threshold() = (noise * NOISE_FACTOR).coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    private fun finish(event: Event): Event {
        done = true
        return event
    }

    private fun rms(samples: FloatArray): Float {
        var sum = 0.0
        for (s in samples) sum += s * s
        return sqrt(sum / samples.size).toFloat()
    }

    companion object {
        const val CALIBRATE_MS = 300
        const val MIN_SPEECH_MS = 200
        const val END_SILENCE_MS = 1_000
        const val NO_SPEECH_MS = 8_000
        const val MAX_UTTERANCE_MS = 20_000
        private const val NOISE_FACTOR = 3f
        private const val NOISE_ADAPT = 0.05f
        private const val HOLD_FACTOR = 0.5f
        private const val MIN_THRESHOLD = 0.012f
        private const val MAX_THRESHOLD = 0.04f
    }
}

/** What a spoken turn can mean besides a message to Bibi (#118). Whole short phrases only. */
enum class TalkCommand {
    OPEN_CHAT,
    END,
    ;

    companion object {
        private val OPEN = Regex("""^(?:(?:hey |ok |okay )?bibi,? )?(?:please )?(?:open|show|switch to|go to)(?: the)? chat(?: mode)?(?: please)?$|^let me type$""")
        private val STOP = Regex("""^(?:(?:ok|okay|thanks|thank you),? )?(?:stop(?: listening)?|bye|goodbye(?: bibi)?|bye bibi|that'?s all|that is all)(?: bibi)?$""")

        fun parse(text: String): TalkCommand? {
            val t = text.lowercase().trim().trimEnd('.', '!', '?').trim()
            return when {
                OPEN.matches(t) -> OPEN_CHAT
                STOP.matches(t) -> END
                else -> null
            }
        }
    }
}
