package io.github.saadahmed1122.bibi.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndpointerTest {
    /** 0.1 s of 16 kHz audio at a constant [level] (RMS equals the level). */
    private fun chunk(level: Float) = FloatArray(1_600) { if (it % 2 == 0) level else -level }

    private val quiet = 0.004f // room noise measured on the vivo (RMS 0.0042–0.0047)
    private val voice = 0.05f

    private fun Endpointer.feed(level: Float, tenths: Int): List<Endpointer.Event> =
        (1..tenths).mapNotNull { add(chunk(level)) }

    @Test
    fun speechThenAPauseEndsTheUtterance() {
        val e = Endpointer()
        assertEquals(emptyList<Endpointer.Event>(), e.feed(quiet, 5))
        assertEquals(listOf(Endpointer.Event.SpeechStarted), e.feed(voice, 10))
        // 1.0 s of quiet isn't enough; the 11th tenth ends it.
        assertEquals(emptyList<Endpointer.Event>(), e.feed(quiet, 10))
        assertEquals(listOf(Endpointer.Event.SpeechEnded), e.feed(quiet, 1))
    }

    @Test
    fun aShortPauseInsideASentenceDoesNotEndIt() {
        val e = Endpointer()
        e.feed(quiet, 3)
        e.feed(voice, 5)
        assertEquals(emptyList<Endpointer.Event>(), e.feed(quiet, 5))
        assertEquals(emptyList<Endpointer.Event>(), e.feed(voice, 5))
        assertEquals(listOf(Endpointer.Event.SpeechEnded), e.feed(quiet, 11))
    }

    @Test
    fun softSyllablesInsideASentenceStillCountAsSpeech() {
        // Measured on the vivo: words at ~0.02 with softer parts around 0.008, under the 0.012 start bar.
        val e = Endpointer()
        e.feed(quiet, 3)
        assertEquals(listOf(Endpointer.Event.SpeechStarted), e.feed(0.02f, 3))
        assertEquals(emptyList<Endpointer.Event>(), (1..4).flatMap { e.feed(0.008f, 6) + e.feed(0.02f, 2) })
        assertEquals(listOf(Endpointer.Event.SpeechEnded), e.feed(quiet, 11))
    }

    @Test
    fun aClickIsNotSpeech() {
        val e = Endpointer()
        e.feed(quiet, 3)
        // 0.1 s of loud is below the 0.2 s minimum.
        assertEquals(emptyList<Endpointer.Event>(), e.feed(voice, 1) + e.feed(quiet, 20))
    }

    @Test
    fun eightSecondsOfQuietIsNoSpeech() {
        val e = Endpointer()
        assertEquals(emptyList<Endpointer.Event>(), e.feed(quiet, 79))
        assertEquals(listOf(Endpointer.Event.NoSpeech), e.feed(quiet, 1))
    }

    @Test
    fun aVeryLongUtteranceIsCutAtTwentySeconds() {
        val e = Endpointer()
        e.feed(quiet, 3)
        val events = e.feed(voice, 210)
        assertEquals(listOf(Endpointer.Event.SpeechStarted, Endpointer.Event.SpeechEnded), events)
    }

    @Test
    fun speakingDuringTheFirstTenthsStillCounts() {
        // The bar is capped, so speech that starts at once doesn't hide itself.
        val e = Endpointer()
        assertEquals(listOf(Endpointer.Event.SpeechStarted), e.feed(voice, 6))
    }

    @Test
    fun speechDuringCalibrationDoesNotHideQuieterSpeech() {
        // Found on the vivo: the mic opened mid-sentence, so calibration heard speech, not the room.
        val e = Endpointer()
        e.feed(0.004f, 1)
        e.feed(0.03f, 2)
        assertEquals(listOf(Endpointer.Event.SpeechStarted), e.feed(0.03f, 3))
    }

    @Test
    fun aNoisyRoomRaisesTheBar() {
        val e = Endpointer()
        // Steady fan noise at 0.02 for 1 s: not speech, and it lifts the threshold above 0.02.
        assertEquals(emptyList<Endpointer.Event>(), e.feed(0.02f, 10))
        assertEquals(listOf(Endpointer.Event.SpeechStarted), e.feed(0.12f, 3))
    }
}

class TalkCommandTest {
    @Test
    fun opensChat() {
        listOf("Open chat", "open the chat.", "Bibi, open chat mode", "Hey BB, open chat", "let me type", "Switch to chat").forEach {
            assertEquals(it, TalkCommand.OPEN_CHAT, TalkCommand.parse(it))
        }
    }

    @Test
    fun ends() {
        listOf("Stop.", "bye", "Goodbye Bibi", "Bye BB", "Bye B D.", "bye for now", "that's all", "Thanks, that's all", "stop listening").forEach {
            assertEquals(it, TalkCommand.END, TalkCommand.parse(it))
        }
    }

    @Test
    fun normalSentencesAreNotCommands() {
        listOf("Where is the bus stop?", "stop the music", "bye the way, what time is it", "How do I open a chat with my friend on WhatsApp", "set a timer").forEach {
            assertNull(it, TalkCommand.parse(it))
        }
    }
}
