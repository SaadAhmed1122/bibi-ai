package io.github.saadahmed1122.bibi.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyTimerTest {

    @Test
    fun ttftIsSendToFirstTokenAndSpeedCountsTokensAfterTheFirst() {
        val timer = ReplyTimer(startNs = 0)
        timer.token(nowNs = 1_500_000_000) // first token at 1.5 s
        timer.token(nowNs = 2_000_000_000)
        timer.token(nowNs = 2_500_000_000) // 2 more tokens in 1 s
        assertEquals(1_500.0, timer.ttftMs()!!, 0.001)
        assertEquals(2.0, timer.tokensPerSecond()!!, 0.001)
        assertEquals(3, timer.tokens)
    }

    @Test
    fun noTokensMeansNoNumbers() {
        val timer = ReplyTimer(startNs = 0)
        assertNull(timer.ttftMs())
        assertNull(timer.tokensPerSecond())
    }

    @Test
    fun oneTokenHasTtftButNoSpeed() {
        val timer = ReplyTimer(startNs = 100)
        timer.token(nowNs = 1_000_100)
        assertEquals(1.0, timer.ttftMs()!!, 0.001)
        assertNull(timer.tokensPerSecond())
    }
}
