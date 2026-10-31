package io.github.saadahmed1122.bibi.ai.inference.litert

import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyOutcomeTest {

    @Test
    fun finishedReplyIsDone() {
        assertEquals(ReplyEvent.Done("hi"), ReplyOutcome.terminal("hi", stopRequested = false, capped = false, failure = null))
    }

    @Test
    fun stopWinsOverTheCancelError() {
        // cancelProcess() ends the turn through onError; a requested stop must still read as Stopped.
        assertEquals(
            ReplyEvent.Stopped("hel"),
            ReplyOutcome.terminal("hel", stopRequested = true, capped = false, failure = "cancelled"),
        )
        assertEquals(ReplyEvent.Stopped("hel"), ReplyOutcome.terminal("hel", stopRequested = true, capped = false, failure = null))
    }

    @Test
    fun cappedReplyIsDoneWithTheTextSoFar() {
        assertEquals(
            ReplyEvent.Done("long"),
            ReplyOutcome.terminal("long", stopRequested = false, capped = true, failure = "cancelled"),
        )
    }

    @Test
    fun failureIsErrorWithThePartialText() {
        assertEquals(
            ReplyEvent.Error("boom", "par"),
            ReplyOutcome.terminal("par", stopRequested = false, capped = false, failure = "boom"),
        )
    }

    @Test
    fun onlyAnUncancelledFinishedTurnKeepsTheConversation() {
        assertFalse(ReplyOutcome.needsNewConversation(ReplyEvent.Done("a"), capped = false))
        assertTrue(ReplyOutcome.needsNewConversation(ReplyEvent.Done("a"), capped = true))
        assertTrue(ReplyOutcome.needsNewConversation(ReplyEvent.Stopped("a"), capped = false))
        assertTrue(ReplyOutcome.needsNewConversation(ReplyEvent.Error("x", "a"), capped = false))
    }

    @Test
    fun backstopLetsTheEngineCapStopFirst() {
        assertFalse(ReplyOutcome.reachedCap(ReplyOutcome.MAX_REPLY_TOKENS))
        assertTrue(ReplyOutcome.reachedCap(ReplyOutcome.MAX_REPLY_TOKENS + 8))
    }
}
