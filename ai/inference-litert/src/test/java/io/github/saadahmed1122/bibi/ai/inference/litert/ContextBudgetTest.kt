package io.github.saadahmed1122.bibi.ai.inference.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBudgetTest {
    // ~310-token preface, as with system_bibi.txt plus the primer.
    private fun budget() = ContextBudget(prefaceChars = 945)

    private val shortReply = "Sure! Here is a short, friendly answer to that."

    @Test
    fun aFreshConversationIsNeverReset() {
        // Even a message that can't fit: resetting an empty conversation would loop.
        assertFalse(ContextBudget(prefaceChars = 945, contextTokens = 400).needsReset("x".repeat(2_000)))
    }

    @Test
    fun resetTriggersBeforeTheBudgetIsExceeded() {
        val b = budget()
        var turns = 0
        val longReply = "y".repeat(1_000) // about a 256-token answer
        while (!b.needsReset("Tell me more.")) {
            b.record("Tell me more.", longReply)
            turns++
            // The turn that was allowed, plus a full reply, must fit.
            assertTrue(b.usedTokens <= LiteRtLmEngine.MAX_NUM_TOKENS)
        }
        assertTrue("reset after $turns turns", turns in 5..15)
    }

    @Test
    fun reopenCarriesTheLastSixTurnsInOrder() {
        val b = budget()
        repeat(9) { b.record("q$it", "a$it") }
        val carried = b.reopen()
        assertEquals((3..8).map { Turn("q$it", "a$it") }, carried)
    }

    @Test
    fun reopenDropsOldTurnsThatWouldNotLeaveRoom() {
        // No carry cap here, so only the room-for-the-next-turn rule limits the carry.
        val b = ContextBudget(prefaceChars = 945, maxCarryTokens = Int.MAX_VALUE)
        repeat(6) { b.record("x".repeat(2_000), "y".repeat(1_000)) }
        val carried = b.reopen()
        assertTrue(carried.size in 1..5)
        // Room for a maximum-length message and reply right after the reopen.
        assertFalse(b.needsReset("x".repeat(ContextBudget.MAX_PROMPT_CHARS)))
    }

    @Test
    fun repliesThatAreEmptyAreNotCarried() {
        val b = budget()
        b.record("hello", shortReply)
        b.record("failed one", "")
        assertEquals(listOf(Turn("hello", shortReply)), b.reopen())
    }

    @Test
    fun reopenResetsTheCountToPrefaceAndCarriedTurns() {
        val b = budget()
        val fresh = b.usedTokens
        repeat(3) { b.record("q", shortReply) }
        val beforeReopen = b.usedTokens
        b.reopen()
        assertEquals(beforeReopen, b.usedTokens) // all three carried, nothing dropped
        b.clear()
        assertEquals(fresh, b.usedTokens)
        assertTrue(b.reopen().isEmpty())
    }

    @Test
    fun carriedTextIsCappedSoAReopenStaysShort() {
        val b = budget()
        repeat(6) { b.record("Explain how volcanoes work.", "y".repeat(1_000)) } // ~350 tokens each
        assertEquals(1, b.reopen().size)
    }

    @Test
    fun aLongNewestTurnCarriesNothing() {
        val b = budget()
        b.record("q", "short")
        b.record("x".repeat(2_000), "y".repeat(1_000))
        assertTrue(b.reopen().isEmpty())
    }
}
