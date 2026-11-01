package io.github.saadahmed1122.bibi.ai.inference.litert

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepetitionGuardTest {
    @Test
    fun theLoopSeenOnTheVivoIsCaught() {
        // #107: "who is Sara" ran to the 256-token cap like this.
        val text = "Sara-Marie-Jones-Smith-Jones-Smith-Jones-Smith-Jones-Smith"
        assertEquals("Sara-Marie-Jones-Smith…", RepetitionGuard.check(text))
    }

    @Test
    fun aRepeatedSentenceIsCaught() {
        val loop = "I am here for you. ".repeat(3)
        assertEquals("Okay! I am here for you.…", RepetitionGuard.check("Okay! $loop"))
    }

    @Test
    fun normalAnswersPass() {
        listOf(
            "Try dimming your lights an hour before bed and keeping your bedroom cool.",
            "1. Drink water. 2. Walk a bit. 3. Sleep early.",
            "Ha ha ha, that's funny!",
            "Here is a line:\n----------------------------------------\nDone.",
            "Paris!",
        ).forEach { assertNull(it, RepetitionGuard.check(it)) }
    }

    @Test
    fun twoRepeatsAreNotALoop() {
        assertNull(RepetitionGuard.check("Jones-Smith-Jones-Smith and that's all I know about the family."))
    }
}
