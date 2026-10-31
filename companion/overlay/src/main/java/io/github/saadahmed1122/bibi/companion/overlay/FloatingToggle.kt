package io.github.saadahmed1122.bibi.companion.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The saved floating-Bibi settings (the overlay SharedPreferences in the app, a fake in tests). */
interface FloatingSettings {
    var enabled: Boolean
    var calm: Boolean
}

/** Starts and stops the overlay service. */
interface OverlayControl {
    fun start()
    fun stop()

    /** Applies Calm mode to a running overlay. */
    fun applyCalm()
}

/** [showExplainer]: the "Bibi needs Display over other apps" dialog is up. */
data class FloatingState(val enabled: Boolean, val calm: Boolean, val showExplainer: Boolean = false)

/**
 * The Floating Bibi switch (SPEC-onboarding.md, PRD 0.9): the overlay permission is asked only when
 * the user turns the switch on, after an explainer. Without the permission the switch stays off and
 * chat still works. Main thread only.
 */
class FloatingToggle(
    private val settings: FloatingSettings,
    private val canDrawOverlays: () -> Boolean,
    private val overlay: OverlayControl,
) {
    private val _state = MutableStateFlow(FloatingState(enabled = settings.enabled, calm = settings.calm))
    val state: StateFlow<FloatingState> = _state.asStateFlow()

    /** Set while the system overlay-permission page is open. */
    private var awaitingPermission = false

    fun onSwitch(on: Boolean) {
        when {
            !on -> {
                setEnabled(false)
                overlay.stop()
            }
            canDrawOverlays() -> enable()
            else -> _state.value = _state.value.copy(showExplainer = true)
        }
    }

    /** "Open settings" on the explainer: the caller then opens the system page. */
    fun onExplainerContinue() {
        _state.value = _state.value.copy(showExplainer = false)
        awaitingPermission = true
    }

    /** "Not now" on the explainer: the switch stays off. */
    fun onExplainerDismiss() {
        _state.value = _state.value.copy(showExplainer = false)
    }

    /** Back from the system page: start if the permission was granted, else stay off. */
    fun onReturnFromSettings() {
        if (!awaitingPermission) return
        awaitingPermission = false
        if (canDrawOverlays()) enable()
    }

    /**
     * App start or resume: re-reads the switch (the notification's Turn off clears it) and brings
     * floating Bibi back if it is on, e.g. after the system killed the process. If the permission
     * was revoked meanwhile, the switch turns off.
     */
    fun onAppVisible() {
        if (!settings.enabled) {
            _state.value = _state.value.copy(enabled = false)
        } else if (canDrawOverlays()) {
            _state.value = _state.value.copy(enabled = true)
            overlay.start()
        } else {
            setEnabled(false)
        }
    }

    fun onCalm(on: Boolean) {
        settings.calm = on
        _state.value = _state.value.copy(calm = on)
        if (settings.enabled) overlay.applyCalm()
    }

    private fun enable() {
        setEnabled(true)
        overlay.start()
    }

    private fun setEnabled(on: Boolean) {
        settings.enabled = on
        _state.value = _state.value.copy(enabled = on)
    }
}
