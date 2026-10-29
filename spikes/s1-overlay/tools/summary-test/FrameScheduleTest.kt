package io.github.saadahmed1122.bibi.spike.s1.frametest

import io.github.saadahmed1122.bibi.spike.s1.FrameSchedule

// JVM-only tests for FrameSchedule (no Android). Run with tools/summary-test/run.sh.

private fun check(name: String, ok: Boolean, detail: String) {
    if (!ok) throw AssertionError("FAIL $name: $detail")
    println("PASS $name")
}

/**
 * Simulates Choreographer for [seconds]: callbacks land only on vsync ticks, and a callback posted
 * with a delay runs on the first vsync after (post time + delay). The post happens [workNanos]
 * after the frame time (the callback's own work). Returns the number of frames drawn.
 */
private fun simulate(hz: Double, fps: Double, seconds: Int, workNanos: Long = 2_000_000L): Int {
    val vsync = (1e9 / hz).toLong()
    val schedule = FrameSchedule((1e9 / fps).toLong())
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
    println("  ${hz}Hz target ${fps}fps over ${seconds}s: $drawn frames, $callbacks callbacks")
    check("${hz}Hz ${fps}fps wakes only to draw", callbacks <= drawn + 1, "$callbacks callbacks for $drawn frames")
    return drawn
}

fun main() {
    val f12 = simulate(60.0, 12.0, 10)
    check("60Hz 12fps draws 120 +-2 in 10 s", f12 in 118..122, "got $f12")
    val f2 = simulate(60.0, 2.0, 10)
    check("60Hz 2fps draws 20 +-1 in 10 s", f2 in 19..21, "got $f2")
    val f12at90 = simulate(90.0, 12.0, 10)
    check("90Hz 12fps draws 120 +-2 in 10 s", f12at90 in 118..122, "got $f12at90")
    val f12at120 = simulate(120.0, 12.0, 10)
    check("120Hz 12fps draws 120 +-2 in 10 s", f12at120 in 118..122, "got $f12at120")

    // The old "draw, then postFrameCallbackDelayed(83 ms)" rule, for contrast: interval + up to a vsync.
    val vsync = 16_666_667L
    var t = 0L
    var old = 0
    while (t < 10_000_000_000L) {
        old++
        t = ((t + 2_000_000L + 83_000_000L) / vsync + 1) * vsync
    }
    println("  old rule at 60Hz: $old frames in 10 s")
    check("new rule beats old rule", f12 > old, "new $f12, old $old")

    // Fell behind (e.g. a long stall): the grid restarts from now instead of bursting to catch up.
    val s = FrameSchedule(83_333_333L)
    check("first callback draws", s.onFrame(1_000_000_000L, vsync), "no draw")
    check("early callback skips", !s.onFrame(1_016_666_667L, vsync), "drew early")
    check("stall draws", s.onFrame(2_000_000_000L, vsync), "no draw after stall")
    check("stall resets grid", s.nextDueNanos == 2_083_333_333L, "nextDue ${s.nextDueNanos}")
    check("no burst after stall", !s.onFrame(2_016_666_667L, vsync), "burst")
    check("delay stops half a vsync early", s.delayMs == (2_083_333_333L - 2_016_666_667L - vsync / 2) / 1_000_000L, "delay ${s.delayMs}")

    // reset() makes the next callback draw at once (clock restarted after being stopped).
    s.reset()
    check("reset draws immediately", s.onFrame(9_000_000_000L, vsync), "no draw")

    println("ALL PASS")
}
