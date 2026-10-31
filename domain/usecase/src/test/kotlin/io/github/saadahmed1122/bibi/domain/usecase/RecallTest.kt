package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.model.MemoryItem.Kind.FACT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecallTest {
    private fun item(id: Long, text: String, pinned: Boolean = false, updatedAt: Long = id) =
        MemoryItem(id, FACT, text, pinned, 0, updatedAt)

    @Test
    fun noPinnedNotesMeansNoBlock() {
        assertEquals("", MemoryPrompt.pinned(listOf(item(1, "My sister is Sara"))))
    }

    @Test
    fun pinnedNotesNewestFirstAsSentences() {
        val block = MemoryPrompt.pinned(listOf(item(1, "My name is Saad", pinned = true, updatedAt = 1), item(2, "I live in Milan.", pinned = true, updatedAt = 2)))
        assertEquals("What you know about the user: You live in Milan. Your name is Saad.", block)
    }

    @Test
    fun pinnedNotesStayUnderTheCap() {
        val many = (1L..40).map { item(it, "Note number $it about something the user likes", pinned = true) }
        assertTrue(MemoryPrompt.pinned(many).length <= MemoryPrompt.PINNED_MAX_CHARS + 70)
    }

    @Test
    fun aTurnWithoutMatchesIsUnchanged() {
        assertEquals("what's my sister's name?", MemoryPrompt.turn("what's my sister's name?", emptyList()))
    }

    @Test
    fun matchesComeBeforeTheMessage() {
        assertEquals(
            "(Remember: Your sister is Sara.)\nwhat's my sister's name?",
            MemoryPrompt.turn("what's my sister's name?", listOf(item(1, "My sister is Sara"))),
        )
    }

    @Test
    fun theUsersWordsBecomeYou() {
        assertEquals("Your sister is Sara", MemoryPrompt.toSecondPerson("My sister is Sara"))
        assertEquals("You are allergic to peanuts", MemoryPrompt.toSecondPerson("I am allergic to peanuts"))
        assertEquals("You're from Lahore and your mum calls you Saadi", MemoryPrompt.toSecondPerson("I'm from Lahore and my mum calls me Saadi"))
        assertEquals("The gym opens at 6", MemoryPrompt.toSecondPerson("The gym opens at 6"))
        assertEquals("Mimic the band", MemoryPrompt.toSecondPerson("Mimic the band")) // whole words only
    }

    @Test
    fun anOverlongNoteIsCut() {
        val turn = MemoryPrompt.turn("hi", listOf(item(1, "x".repeat(1_000))))
        assertTrue(turn.length < MemoryPrompt.MATCHES_MAX_CHARS + 60)
        assertTrue(turn.contains("…"))
    }
}
