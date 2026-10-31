package io.github.saadahmed1122.bibi.companion.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingToggleTest {
    private class Fakes : FloatingSettings, OverlayControl {
        override var enabled = false
        override var calm = false
        var permission = false
        val calls = mutableListOf<String>()
        override fun start() { calls += "start" }
        override fun stop() { calls += "stop" }
        override fun applyCalm() { calls += "calm" }
    }

    private fun toggle(f: Fakes) = FloatingToggle(f, { f.permission }, f)

    @Test
    fun withThePermissionTheSwitchStartsTheService() {
        val f = Fakes().apply { permission = true }
        val t = toggle(f)
        t.onSwitch(true)
        assertTrue(t.state.value.enabled)
        assertTrue(f.enabled)
        assertEquals(listOf("start"), f.calls)
    }

    @Test
    fun withoutThePermissionTheExplainerComesFirstAndNothingStarts() {
        val f = Fakes()
        val t = toggle(f)
        t.onSwitch(true)
        assertTrue(t.state.value.showExplainer)
        assertFalse(t.state.value.enabled)
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun grantedInSettingsStartsOnReturn() {
        val f = Fakes()
        val t = toggle(f)
        t.onSwitch(true)
        t.onExplainerContinue()
        f.permission = true // the user allowed it on the system page
        t.onReturnFromSettings()
        assertTrue(t.state.value.enabled)
        assertEquals(listOf("start"), f.calls)
    }

    @Test
    fun deniedInSettingsLeavesTheSwitchOff() {
        val f = Fakes()
        val t = toggle(f)
        t.onSwitch(true)
        t.onExplainerContinue()
        t.onReturnFromSettings()
        assertFalse(t.state.value.enabled)
        assertFalse(f.enabled)
        assertTrue(f.calls.isEmpty())
    }

    @Test
    fun notNowOnTheExplainerLeavesTheSwitchOff() {
        val f = Fakes()
        val t = toggle(f)
        t.onSwitch(true)
        t.onExplainerDismiss()
        t.onReturnFromSettings() // no settings visit: ignored
        assertFalse(t.state.value.enabled)
        assertFalse(t.state.value.showExplainer)
    }

    @Test
    fun switchingOffStopsTheService() {
        val f = Fakes().apply { permission = true }
        val t = toggle(f)
        t.onSwitch(true)
        t.onSwitch(false)
        assertFalse(f.enabled)
        assertEquals(listOf("start", "stop"), f.calls)
    }

    @Test
    fun appStartBringsBibiBackOnlyIfOnAndAllowed() {
        val on = Fakes().apply { enabled = true; permission = true }
        toggle(on).onAppVisible()
        assertEquals(listOf("start"), on.calls)

        val revoked = Fakes().apply { enabled = true }
        val t = toggle(revoked)
        t.onAppVisible()
        assertFalse(revoked.enabled)
        assertFalse(t.state.value.enabled)
        assertTrue(revoked.calls.isEmpty())

        val off = Fakes().apply { permission = true }
        toggle(off).onAppVisible()
        assertTrue(off.calls.isEmpty())
    }

    @Test
    fun turnOffFromTheNotificationShowsOnResume() {
        val f = Fakes().apply { permission = true }
        val t = toggle(f)
        t.onSwitch(true)
        f.enabled = false // the notification's Turn off
        t.onAppVisible()
        assertFalse(t.state.value.enabled)
    }

    @Test
    fun calmIsSavedAndAppliedWhenRunning() {
        val f = Fakes().apply { permission = true }
        val t = toggle(f)
        t.onCalm(true)
        assertTrue(f.calm)
        assertTrue(f.calls.isEmpty()) // not running: just saved
        t.onSwitch(true)
        t.onCalm(false)
        assertEquals(listOf("start", "calm"), f.calls)
    }
}
