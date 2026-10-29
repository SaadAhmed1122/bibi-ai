package io.github.saadahmed1122.bibi.spike.s1

/**
 * Decides, on each Choreographer callback, whether to draw and how long to wait before the next one.
 * Pure Kotlin (no Android imports) so it can be tested on a plain JVM; mutable fields instead of a
 * result object so the per-frame path allocates nothing.
 *
 * Frames are due on a fixed grid (`nextDueNanos += intervalNanos`), not "interval after the last
 * frame", so waiting for vsync doesn't add to the interval. `postFrameCallbackDelayed` runs "on the
 * next frame after the specified delay" (developer.android.com, Choreographer reference), so the
 * delay stops half a vsync before the due time: the callback lands on the first vsync that counts
 * as due, and each drawn frame costs one wake-up at 60, 90 or 120 Hz.
 */
class FrameSchedule(intervalNanos: Long) {
    var intervalNanos: Long = intervalNanos
        set(value) {
            require(value > 0) { "intervalNanos must be > 0" }
            field = value
        }

    /** Time the next frame is due; 0 means "draw on the first callback". */
    var nextDueNanos: Long = 0L
        private set

    /** Delay to pass to `postFrameCallbackDelayed` after the last [onFrame] call. */
    var delayMs: Long = 0L
        private set

    init {
        this.intervalNanos = intervalNanos
    }

    /** Call when the clock (re)starts, so the first callback draws immediately. */
    fun reset() {
        nextDueNanos = 0L
        delayMs = 0L
    }

    /**
     * Returns true if this callback should draw. Updates [nextDueNanos] and [delayMs].
     * A frame within half a vsync of its due time counts as due, so callback jitter can't push it a
     * whole vsync later. If drawing fell more than one interval behind, the grid restarts from now.
     */
    fun onFrame(frameTimeNanos: Long, vsyncNanos: Long): Boolean {
        val draw = nextDueNanos == 0L || frameTimeNanos >= nextDueNanos - vsyncNanos / 2
        if (draw) {
            val next = nextDueNanos + intervalNanos
            nextDueNanos = if (nextDueNanos == 0L || next <= frameTimeNanos) frameTimeNanos + intervalNanos else next
        }
        val remaining = nextDueNanos - frameTimeNanos
        delayMs = ((remaining - vsyncNanos / 2) / 1_000_000L).coerceAtLeast(0L)
        return draw
    }
}
