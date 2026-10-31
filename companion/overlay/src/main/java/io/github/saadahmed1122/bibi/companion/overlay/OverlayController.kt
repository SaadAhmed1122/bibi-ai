package io.github.saadahmed1122.bibi.companion.overlay

/** Adds or removes Bibi's window; the service wraps the WindowManager, tests use a fake. */
interface OverlayWindow {
    /** Returns false if the window manager refused (e.g. the overlay permission was revoked). */
    fun add(): Boolean

    fun remove()
}

/**
 * Show / Hide / Stop for the floating window (SPEC-overlay.md). Hide removes the window, so a
 * hidden Bibi costs nothing, and every add has exactly one matching remove. Main thread only.
 */
class OverlayController(private val window: OverlayWindow) {
    enum class State { Stopped, Shown, Hidden }

    var state = State.Stopped
        private set

    /** Returns false if the window couldn't be added; the state is then unchanged. */
    fun show(): Boolean {
        if (state == State.Shown) return true
        if (!window.add()) return false
        state = State.Shown
        return true
    }

    fun hide() {
        if (state == State.Shown) window.remove()
        if (state != State.Stopped) state = State.Hidden
    }

    fun stop() {
        if (state == State.Shown) window.remove()
        state = State.Stopped
    }
}
