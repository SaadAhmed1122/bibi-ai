package io.github.saadahmed1122.bibi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.domain.model.ModelDownloadState
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.DownloadStatus
import io.github.saadahmed1122.bibi.domain.usecase.ModelDownloader
import io.github.saadahmed1122.bibi.domain.usecase.ModelRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Downloads the model from [url] in the background, then hands the file to [repository] for the
 * usual size and SHA-256 check (SPEC-model-download.md). The system keeps downloading when the app
 * closes; a new ViewModel picks the same download up again ([ModelDownloader.activeId]).
 */
class ModelDownloadViewModel(
    private val url: String,
    private val downloader: ModelDownloader,
    private val repository: ModelRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
    private val pollMs: Long = 500,
) : ViewModel(scope) {

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        downloader.activeId?.let(::follow)
    }

    fun start() {
        if (job?.isActive == true || repository.state.value is ModelState.Ready) return
        follow(downloader.start(url))
    }

    fun cancel() {
        job?.cancel()
        downloader.activeId?.let(downloader::remove)
        _state.value = ModelDownloadState.Idle
    }

    private fun follow(id: Long) {
        _state.value = ModelDownloadState.Downloading(0, null, waiting = false)
        job = viewModelScope.launch {
            while (true) {
                when (val s = downloader.status(id)) {
                    null -> {
                        _state.value = ModelDownloadState.Idle
                        return@launch
                    }
                    is DownloadStatus.Running -> _state.value = ModelDownloadState.Downloading(s.done, s.total, s.waiting)
                    is DownloadStatus.Failed -> {
                        downloader.remove(id)
                        _state.value = ModelDownloadState.Failed(s.code)
                        return@launch
                    }
                    DownloadStatus.Done -> {
                        // The repository shows Importing from here; a wrong file ends Invalid, as with a picked file.
                        _state.value = ModelDownloadState.Idle
                        try {
                            repository.import(downloader.source(id))
                        } finally {
                            downloader.remove(id)
                        }
                        return@launch
                    }
                }
                delay(pollMs)
            }
        }
    }
}
