package io.github.saadahmed1122.bibi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.ModelRepository
import io.github.saadahmed1122.bibi.domain.usecase.ModelSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Runs the import for the model screen; the copy keeps going if the screen is rotated. */
class ModelImportViewModel(
    private val repository: ModelRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
) : ViewModel(scope) {

    val state: StateFlow<ModelState> = repository.state

    private var importJob: Job? = null

    fun import(source: ModelSource) {
        if (importJob?.isActive == true) return
        importJob = viewModelScope.launch { repository.import(source) }
    }

    fun cancel() {
        importJob?.cancel()
    }

    fun remove() {
        if (importJob?.isActive != true) repository.remove()
    }
}
