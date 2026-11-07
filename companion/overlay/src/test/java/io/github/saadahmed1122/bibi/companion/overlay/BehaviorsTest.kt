package io.github.saadahmed1122.bibi.companion.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BehaviorsTest {
    private val min = 60_000L
    private val noon = 12

    private fun b() = Behaviors()

    @Test
    fun tapHopsAndDropLands() {
        val b = b()
        assertEquals(Act.HOP, b.onTap(0))
        assertEquals(Act.HOP, b.current(100))
        assertEquals(Act.LAND, b.onDrop(1_000))
        assertNull(b.current(1_000 + Act.LAND.durationMs))
    }

    @Test
    fun calmTurnsEveryActOff() {
        val b = b().apply { calm = true }
        assertNull(b.onTap(0))
        assertNull(b.onUnlock(0, noon))
        assertNull(b.onPowerConnected(0))
    }

    @Test
    fun greetOnUnlockHasA30MinuteCooldown() {
        val b = b()
        assertEquals(Act.GREET, b.onUnlock(0, noon))
        assertNull(b.onUnlock(29 * min, noon))
        assertEquals(Act.GREET, b.onUnlock(31 * min, noon))
    }

    @Test
    fun noGreetingInQuietHours() {
        assertNull(b().onUnlock(0, 2))
        assertNull(b().onUnlock(0, 23))
        assertEquals(Act.GREET, b().onUnlock(0, 7))
    }

    @Test
    fun spontaneousActsWaitWhileBusy() {
        val b = b().apply { busy = true }
        assertNull(b.onUnlock(0, noon))
        assertNull(b.onPowerConnected(0))
        // Touch is the user's own action: it still answers.
        assertEquals(Act.HOP, b.onTap(0))
    }

    @Test
    fun chargingReactsOncePerConnect() {
        val b = b()
        assertEquals(Act.CHARGING, b.onPowerConnected(0))
    }

    @Test
    fun lowBatteryOncePerCycle() {
        val b = b()
        assertNull(b.onBattery(16, charging = false, now = 0))
        assertEquals(Act.LOW_BATTERY, b.onBattery(15, charging = false, now = 1_000))
        assertNull(b.onBattery(14, charging = false, now = 10 * min))
        // Charging re-arms it.
        assertNull(b.onBattery(30, charging = true, now = 20 * min))
        assertEquals(Act.LOW_BATTERY, b.onBattery(15, charging = false, now = 90 * min))
    }

    @Test
    fun hourlyBudgetCapsSpontaneousActs() {
        val b = b()
        repeat(Behaviors.BUDGET_PER_HOUR) { i -> assertEquals(Act.CHARGING, b.onPowerConnected(i * min)) }
        assertNull(b.onPowerConnected(10 * min))
        assertEquals(Act.CHARGING, b.onPowerConnected(61 * min))
    }

    @Test
    fun aReactionDoesNotCutOffATouchAct() {
        val b = b()
        b.onTap(0)
        assertNull(b.onPowerConnected(100))
        assertEquals(Act.HOP, b.current(100))
    }

    @Test
    fun sleepsInQuietHoursAfterTwoQuietMinutesAndATapWakes() {
        val b = b()
        b.onTap(0)
        assertFalse(b.sleeping(1 * min, 1))
        assertTrue(b.sleeping(3 * min, 1))
        assertFalse(b.sleeping(3 * min, noon))
        b.onTap(3 * min)
        assertFalse(b.sleeping(4 * min, 1))
        // Calm keeps its own look: no sleep frame swap.
        b.calm = true
        assertFalse(b.sleeping(60 * min, 1))
    }

    @Test
    fun motionStartsAndEndsAtRest() {
        for (act in Act.entries) {
            assertEquals(0f, ActMotion.lift(act, 0f), 0.001f)
            assertEquals(0f, ActMotion.lift(act, 1f), 0.001f)
            assertEquals(1f, ActMotion.squashY(act, 1f), 0.001f)
        }
        assertTrue(ActMotion.lift(Act.HOP, 0.5f) > 0.05f)
    }
}
