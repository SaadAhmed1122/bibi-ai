package io.github.saadahmed1122.bibi.companion.overlay

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * What an Activity gives Compose, for a window owned by a service (docs/architecture.md): a
 * lifecycle, a ViewModel store and a saved-state registry. It lives as long as the service, so the
 * chat's ViewModel (and its model connection) survives closing and reopening the card, and the
 * card and the talk bubble share it.
 * Main thread only.
 */
internal class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }

    /** Windows on screen that use this owner (the chat card and the talk bubble). */
    private var shown = 0

    /** RESUMED while any of its windows is on screen, CREATED while none is. Calls come in pairs. */
    fun setVisible(visible: Boolean) {
        shown = (shown + if (visible) 1 else -1).coerceAtLeast(0)
        registry.currentState = if (shown > 0) Lifecycle.State.RESUMED else Lifecycle.State.CREATED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
