package io.github.saadahmed1122.bibi.ai.inference.litert

import io.github.saadahmed1122.bibi.ai.inference.api.ReplyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveRequestTest {

    private val active = ActiveRequest()
    private val received = mutableListOf<ReplyEvent>()

    @Test
    fun deliversTheCurrentRequestsEventsUntilTheTerminalOne() {
        assertTrue(active.start(1) { received += it })
        active.deliver(1, ReplyEvent.Token("Hi"))
        active.deliver(1, ReplyEvent.Done("Hi"))
        assertEquals(listOf(ReplyEvent.Token("Hi"), ReplyEvent.Done("Hi")), received)
        assertNull(active.currentId())
    }

    @Test
    fun dropsEventsForOtherOrStaleIds() {
        active.start(1) { received += it }
        active.deliver(1, ReplyEvent.Stopped(""))
        active.start(2) { received += it }
        assertFalse(active.deliver(1, ReplyEvent.Token("late token from 1")))
        assertFalse(active.deliver(3, ReplyEvent.Token("unknown")))
        active.deliver(2, ReplyEvent.Token("two"))
        assertEquals(listOf(ReplyEvent.Stopped(""), ReplyEvent.Token("two")), received)
        assertEquals(2L, active.currentId())
    }

    @Test
    fun onlyOneRequestAtATime() {
        assertTrue(active.start(1) {})
        assertFalse(active.start(2) {})
        assertEquals(1L, active.currentId())
    }

    @Test
    fun failEndsTheRequestWithTheTextSoFar() {
        active.start(1) { received += it }
        active.deliver(1, ReplyEvent.Token("Hel"))
        active.deliver(1, ReplyEvent.Token("lo"))
        active.fail("disconnected")
        assertEquals(ReplyEvent.Error("disconnected", "Hello"), received.last())
        assertNull(active.currentId())
    }

    @Test
    fun clearDropsTheRequestSilently() {
        active.start(1) { received += it }
        active.clear(1)
        assertFalse(active.deliver(1, ReplyEvent.Token("x")))
        active.fail("no request, no event")
        assertTrue(received.isEmpty())
    }
}
