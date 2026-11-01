package io.github.saadahmed1122.bibi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.VoicePackRepository
import io.github.saadahmed1122.bibi.domain.usecase.VoicePackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Runs the voice pack import for the model screen; the copy keeps going if the screen is rotated. */
class VoicePackViewModel(
    private val repository: VoicePackRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : ViewModel(scope) {
    val state: StateFlow<ModelState> = repository.state
    private var job: Job? = null

    fun import(source: VoicePackSource) {
        if (job?.isActive == true) return
        job = viewModelScope.launch(Dispatchers.IO) { repository.import(source) }
    }

    fun cancel() {
        job?.cancel()
    }

    fun remove() {
        if (job?.isActive != true) repository.remove()
    }
}
