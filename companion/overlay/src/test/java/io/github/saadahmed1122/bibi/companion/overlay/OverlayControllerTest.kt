package io.github.saadahmed1122.bibi.companion.overlay

import io.github.saadahmed1122.bibi.companion.overlay.OverlayController.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OverlayControllerTest {
    private class FakeWindow(var allow: Boolean = true) : OverlayWindow {
        var attached = 0
        var adds = 0

        override fun add(): Boolean {
            if (!allow) return false
            check(attached == 0) { "window added twice" }
            attached++
            adds++
            return true
        }

        override fun remove() {
            check(attached == 1) { "removed a window that isn't there" }
            attached--
        }
    }

    @Test
    fun showHideShowStopLeavesNoWindow() {
        val w = FakeWindow()
        val c = OverlayController(w)
        c.show(); assertEquals(State.Shown, c.state); assertEquals(1, w.attached)
        c.hide(); assertEquals(State.Hidden, c.state); assertEquals(0, w.attached)
        c.show(); assertEquals(State.Shown, c.state); assertEquals(1, w.attached)
        c.stop(); assertEquals(State.Stopped, c.state); assertEquals(0, w.attached)
        assertEquals(2, w.adds)
    }

    @Test
    fun repeatedCallsDoNotAddOrRemoveTwice() {
        val w = FakeWindow()
        val c = OverlayController(w)
        repeat(3) { c.show() }
        repeat(3) { c.hide() }
        repeat(3) { c.stop() }
        assertEquals(1, w.adds)
        assertEquals(0, w.attached)
    }

    @Test
    fun stopWhileHiddenRemovesNothing() {
        val w = FakeWindow()
        val c = OverlayController(w)
        c.show(); c.hide(); c.stop()
        assertEquals(State.Stopped, c.state)
        assertEquals(0, w.attached)
    }

    @Test
    fun hideBeforeStartStaysStopped() {
        val c = OverlayController(FakeWindow())
        c.hide()
        assertEquals(State.Stopped, c.state)
    }

    @Test
    fun aRefusedWindowKeepsTheState() {
        val w = FakeWindow(allow = false)
        val c = OverlayController(w)
        assertFalse(c.show())
        assertEquals(State.Stopped, c.state)
    }
}
