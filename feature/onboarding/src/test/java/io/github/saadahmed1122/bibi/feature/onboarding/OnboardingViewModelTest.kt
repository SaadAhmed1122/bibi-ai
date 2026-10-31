package io.github.saadahmed1122.bibi.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingViewModelTest {
    private class FakeStore(override var completed: Boolean = false) : OnboardingStore

    @Test
    fun walksWelcomeModelNotificationsThenSavesTheFlag() {
        val store = FakeStore()
        val vm = OnboardingViewModel(store)
        assertEquals(OnboardingStep.Welcome, vm.step.value)
        vm.next()
        assertEquals(OnboardingStep.Model, vm.step.value)
        vm.next() // Skip the model: allowed, the chat then asks for it
        assertEquals(OnboardingStep.Notifications, vm.step.value)
        assertFalse(store.completed)
        vm.finish() // permission denied or granted: same path
        assertNull(vm.step.value)
        assertTrue(store.completed)
    }

    @Test
    fun aCompletedFirstRunIsNotShownAgain() {
        assertNull(OnboardingViewModel(FakeStore(completed = true)).step.value)
    }

    @Test
    fun finishOnlyCountsOnTheLastStep() {
        val store = FakeStore()
        val vm = OnboardingViewModel(store)
        vm.finish()
        assertEquals(OnboardingStep.Welcome, vm.step.value)
        assertFalse(store.completed)
    }

    @Test
    fun backGoesToThePreviousStep() {
        val vm = OnboardingViewModel(FakeStore())
        vm.next(); vm.next()
        vm.back()
        assertEquals(OnboardingStep.Model, vm.step.value)
        vm.back(); vm.back()
        assertEquals(OnboardingStep.Welcome, vm.step.value)
    }
}
