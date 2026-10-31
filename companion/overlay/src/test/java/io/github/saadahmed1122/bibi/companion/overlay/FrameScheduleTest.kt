package io.github.saadahmed1122.bibi.companion.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Ported from spikes/s1-overlay/tools/summary-test/FrameScheduleTest.kt.
class FrameScheduleTest {
    private val vsync60 = 16_666_667L

    /**
     * Simulates Choreographer for [seconds]: callbacks land only on vsync ticks, and a callback posted
     * with a delay runs on the first vsync after (post time + delay). The post happens [workNanos]
     * after the frame time. Returns the frames drawn and checks there are no wasted wake-ups.
     */
    private fun simulate(hz: Double, fps: Int, seconds: Int, workNanos: Long = 2_000_000L): Int {
        val vsync = (1e9 / hz).toLong()
        val schedule = FrameSchedule(FrameSchedule.intervalNanos(fps))
        val end = seconds * 1_000_000_000L
        val start = 5_000_000_000L // a non-zero clock, like System.nanoTime()
        var tick = 0L
        var drawn = 0
        var callbacks = 0
        while (true) {
            val frameTime = start + tick * vsync
            if (frameTime - start >= end) break
            callbacks++
            if (schedule.onFrame(frameTime, vsync)) drawn++
            val runAfter = frameTime + workNanos + schedule.delayMs * 1_000_000L
            tick = (runAfter - start) / vsync + 1 // first vsync strictly after runAfter
        }
        assertTrue("$callbacks callbacks for $drawn frames", callbacks <= drawn + 1)
        return drawn
    }

    @Test
    fun normalDraws12FramesPerSecondAt60Hz() {
        // +-1 over 1 s: a due frame can fall on the last vsync before the window ends.
        assertEquals(12.0, simulate(60.0, FrameSchedule.FPS_NORMAL, 1).toDouble(), 1.0)
        assertEquals(120.0, simulate(60.0, FrameSchedule.FPS_NORMAL, 10).toDouble(), 2.0)
    }

    @Test
    fun calmDraws2FramesPerSecondAt60Hz() {
        assertEquals(2.0, simulate(60.0, FrameSchedule.FPS_CALM, 1).toDouble(), 1.0)
        assertEquals(20.0, simulate(60.0, FrameSchedule.FPS_CALM, 10).toDouble(), 1.0)
    }

    @Test
    fun normalHolds12FpsAt90And120Hz() {
        assertEquals(120.0, simulate(90.0, FrameSchedule.FPS_NORMAL, 10).toDouble(), 2.0)
        assertEquals(120.0, simulate(120.0, FrameSchedule.FPS_NORMAL, 10).toDouble(), 2.0)
    }

    @Test
    fun stallRestartsGridWithoutBurst() {
        val s = FrameSchedule(83_333_333L)
        assertTrue("first callback draws", s.onFrame(1_000_000_000L, vsync60))
        assertFalse("early callback skips", s.onFrame(1_016_666_667L, vsync60))
        assertTrue("stall draws", s.onFrame(2_000_000_000L, vsync60))
        assertEquals(2_083_333_333L, s.nextDueNanos)
        assertFalse("no burst after stall", s.onFrame(2_016_666_667L, vsync60))
        assertEquals((2_083_333_333L - 2_016_666_667L - vsync60 / 2) / 1_000_000L, s.delayMs)
    }

    @Test
    fun resetDrawsImmediately() {
        val s = FrameSchedule(83_333_333L)
        s.onFrame(1_000_000_000L, vsync60)
        s.reset()
        assertTrue(s.onFrame(9_000_000_000L, vsync60))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveInterval() {
        FrameSchedule(0L)
    }

    @Test
    fun intervalNanosForFps() {
        assertEquals(83_333_333L, FrameSchedule.intervalNanos(FrameSchedule.FPS_NORMAL))
        assertEquals(500_000_000L, FrameSchedule.intervalNanos(FrameSchedule.FPS_CALM))
    }
}
