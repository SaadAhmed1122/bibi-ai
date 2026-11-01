package io.github.saadahmed1122.bibi.feature.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceTest {
    @Test
    fun theChunkerCutsEvenPiecesAndFlushesTheRest() {
        val out = mutableListOf<FloatArray>()
        val c = AudioChunker(size = 4) { out += it }
        c.add(floatArrayOf(1f, 2f, 3f))
        c.add(floatArrayOf(4f, 5f, 6f, 7f, 8f, 9f, 0f, 0f), count = 6)
        c.flush()
        assertEquals(listOf(listOf(1f, 2f, 3f, 4f), listOf(5f, 6f, 7f, 8f), listOf(9f)), out.map { it.toList() })
        c.flush()
        assertEquals(3, out.size)
    }

    @Test
    fun sentencesComeOutAsTheyFinish() {
        val s = SentenceSplitter()
        assertEquals(emptyList<String>(), s.add("Foxes are clever"))
        assertEquals(listOf("Foxes are clever animals."), s.add(" animals. They"))
        assertEquals(listOf("They hunt at night!"), s.add(" hunt at night! Some"))
        assertEquals(emptyList<String>(), s.add(" live in cities"))
        assertEquals("Some live in cities", s.finish())
        assertNull(s.finish())
    }

    @Test
    fun numbersAndAbbreviationsDontEndASentence() {
        val s = SentenceSplitter()
        assertEquals(listOf("It weighs 3.5 kg and Dr. Ali saw it."), s.add("It weighs 3.5 kg and Dr. Ali saw it. "))
    }

    @Test
    fun lineBreaksEndASentenceAndEmptyBitsAreDropped() {
        val s = SentenceSplitter()
        assertEquals(listOf("Pack light", "Bring water."), s.add("Pack light\n\nBring water. "))
    }
}
