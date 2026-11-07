package io.github.saadahmed1122.bibi.companion.overlay

import io.github.saadahmed1122.bibi.core.designsystem.BibiMood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * A short animation Bibi plays over its current pose (SPEC-behaviors.md, PRD §17.3). Only the four
 * existing poses are used; the motion is a lift and a squash drawn by [BibiView].
 * [priority]: the user's own touch (100) beats reactions (40), as in PRD §17.3.2.
 */
enum class Act(val durationMs: Long, val priority: Int, val pose: BibiMood?) {
    /** Tap: a small happy hop. */
    HOP(600, 100, null),

    /** Dropped after a drag: squash and settle. */
    LAND(400, 100, null),

    /** B-02 unlock: paw raised (the speaking pose) and two little hops. */
    GREET(1_800, 40, BibiMood.SPEAKING),

    /** B-05 charger connected: one happy hop. */
    CHARGING(1_200, 40, BibiMood.SPEAKING),

    /** B-06 battery at 15 % or less: the puzzled pose and a small sink. */
    LOW_BATTERY(2_500, 40, BibiMood.CONFUSED),
}

/**
 * Decides when Bibi plays an [Act] and when it sleeps. Pure Kotlin with times passed in (elapsed
 * ms, local hour), so the rules are tested on the JVM. Main thread only.
 *
 * Rules: nothing at all in Calm mode (it promises 2 fps); spontaneous acts wait while Bibi is
 * [busy] (thinking or talking, the card open, a call) and in quiet hours; at most
 * [BUDGET_PER_HOUR] of them per rolling hour; an act never cuts off one of higher priority.
 */
class Behaviors {
    var calm = false
    var busy = false

    private var act: Act? = null
    private var actStart = 0L
    private var lastTouch = 0L
    private var lastGreet: Long? = null
    private var lowBatteryArmed = true
    private val spontaneous = ArrayDeque<Long>()

    /** The act playing at [now], or null. */
    fun current(now: Long): Act? = act?.takeIf { now - actStart < it.durationMs }

    /** 0..1 through the current act. */
    fun progress(now: Long): Float = act?.let { ((now - actStart).toFloat() / it.durationMs).coerceIn(0f, 1f) } ?: 0f

    fun onTap(now: Long): Act? {
        lastTouch = now
        return play(Act.HOP, now)
    }

    fun onDrop(now: Long): Act? {
        lastTouch = now
        return play(Act.LAND, now)
    }

    /** Touching Bibi without a tap (a drag start) still counts as the user being here. */
    fun onTouch(now: Long) {
        lastTouch = now
    }

    fun onUnlock(now: Long, hour: Int): Act? {
        if (quietHour(hour)) return null
        val last = lastGreet
        if (last != null && now - last < GREET_COOLDOWN_MS) return null
        return spontaneous(Act.GREET, now)?.also { lastGreet = now }
    }

    fun onPowerConnected(now: Long): Act? = spontaneous(Act.CHARGING, now)

    fun onBattery(level: Int, charging: Boolean, now: Long): Act? {
        if (charging || level > LOW_BATTERY_REARM) lowBatteryArmed = true
        if (charging || level > LOW_BATTERY || !lowBatteryArmed) return null
        return spontaneous(Act.LOW_BATTERY, now)?.also { lowBatteryArmed = false }
    }

    /** B-03: asleep (eyes closed, slow breath) in quiet hours once nobody touched Bibi for 2 minutes. */
    fun sleeping(now: Long, hour: Int): Boolean =
        !calm && !busy && quietHour(hour) && now - lastTouch >= SLEEP_AFTER_MS && current(now) == null

    private fun spontaneous(candidate: Act, now: Long): Act? {
        if (busy) return null
        while (spontaneous.isNotEmpty() && now - spontaneous.first() >= HOUR_MS) spontaneous.removeFirst()
        if (spontaneous.size >= BUDGET_PER_HOUR) return null
        return play(candidate, now)?.also { spontaneous.addLast(now) }
    }

    private fun play(candidate: Act, now: Long): Act? {
        if (calm) return null
        val playing = current(now)
        if (playing != null && playing.priority > candidate.priority) return null
        act = candidate
        actStart = now
        return candidate
    }

    companion object {
        const val BUDGET_PER_HOUR = 6
        const val GREET_COOLDOWN_MS = 30 * 60_000L
        const val SLEEP_AFTER_MS = 2 * 60_000L
        const val LOW_BATTERY = 15
        const val LOW_BATTERY_REARM = 20
        private const val HOUR_MS = 60 * 60_000L

        /** 23:00 to 07:00 local time. */
        fun quietHour(hour: Int): Boolean = hour >= 23 || hour < 7
    }
}

/**
 * How each act moves, as fractions of Bibi's height; t is 0..1. Starts and ends at rest. The frames
 * have 9–12 % empty space above the ears (26–35 px of 288), so lift plus stretch stays under 9 %.
 */
object ActMotion {
    /** Upward offset (negative sinks). */
    fun lift(act: Act, t: Float): Float {
        val s = sin(PI * t).toFloat()
        return when (act) {
            Act.HOP -> 0.07f * s
            Act.LAND -> 0f
            Act.GREET -> 0.05f * abs(sin(2 * PI * t).toFloat())
            Act.CHARGING -> 0.06f * s
            Act.LOW_BATTERY -> -0.03f * s
        }
    }

    /** Vertical scale around the paws; the width gets the inverse so Bibi keeps its volume. */
    fun squashY(act: Act, t: Float): Float = when (act) {
        Act.LAND -> 1f - 0.12f * sin(PI * t).toFloat()
        Act.HOP, Act.CHARGING -> 1f - 0.03f * sin(2 * PI * t).toFloat()
        else -> 1f
    }
}
