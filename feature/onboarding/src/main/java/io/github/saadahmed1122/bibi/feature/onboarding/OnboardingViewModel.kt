package io.github.saadahmed1122.bibi.feature.onboarding

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the "first run is done" flag is kept (SharedPreferences in the app, a fake in tests). */
interface OnboardingStore {
    var completed: Boolean
}

enum class OnboardingStep { Welcome, Model, Notifications }

/**
 * The first run (SPEC-onboarding.md): Welcome → Model (skippable) → Notifications (allow or not).
 * [step] is null once onboarding is complete. No permission is asked before the Notifications step.
 */
class OnboardingViewModel(private val store: OnboardingStore) : ViewModel() {

    private val _step = MutableStateFlow(if (store.completed) null else OnboardingStep.Welcome)
    val step: StateFlow<OnboardingStep?> = _step.asStateFlow()

    /** Next from Welcome, or Next / Skip from Model. */
    fun next() {
        _step.value = when (_step.value) {
            OnboardingStep.Welcome -> OnboardingStep.Model
            OnboardingStep.Model -> OnboardingStep.Notifications
            OnboardingStep.Notifications, null -> _step.value
        }
    }

    fun back() {
        _step.value = when (_step.value) {
            OnboardingStep.Model -> OnboardingStep.Welcome
            OnboardingStep.Notifications -> OnboardingStep.Model
            OnboardingStep.Welcome, null -> _step.value
        }
    }

    /** After the notification permission answer, whatever it was; denying is fine. */
    fun finish() {
        if (_step.value != OnboardingStep.Notifications) return
        store.completed = true
        _step.value = null
    }
}
