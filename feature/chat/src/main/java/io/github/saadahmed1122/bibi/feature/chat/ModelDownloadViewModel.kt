package io.github.saadahmed1122.bibi.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.saadahmed1122.bibi.domain.model.ModelDownloadState
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.DownloadStatus
import io.github.saadahmed1122.bibi.domain.usecase.ModelDownloader
import io.github.saadahmed1122.bibi.domain.usecase.ModelRepository
import io.github.saadahmed1122.bibi.domain.usecase.ModelSource
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
 * Downloads a pack's files from [urls] in the background, then hands them, in the same order, to
 * [install], which runs the usual size and SHA-256 checks (SPEC-model-download.md). The system keeps
 * downloading when the app closes; a new ViewModel picks the same downloads up again
 * ([ModelDownloader.activeIds]). One failed file stops the pack.
 */
class ModelDownloadViewModel(
    private val urls: List<String>,
    /** Each file's expected size, same order as [urls]: exact progress before the server answers. */
    private val sizes: List<Long>,
    private val downloader: ModelDownloader,
    private val isReady: () -> Boolean,
    private val install: suspend (List<ModelSource>) -> Unit,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
    private val pollMs: Long = 500,
) : ViewModel(scope) {

    /** One model file of [size] bytes into [repository]. */
    constructor(
        url: String,
        size: Long,
        downloader: ModelDownloader,
        repository: ModelRepository,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob()),
        pollMs: Long = 500,
    ) : this(listOf(url), listOf(size), downloader, { repository.state.value is ModelState.Ready }, { repository.import(it.single()) }, scope, pollMs)

    init {
        require(urls.size == sizes.size && urls.isNotEmpty()) { "one size per url" }
    }

    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    init {
        downloader.activeIds.takeIf { it.isNotEmpty() }?.let(::follow)
    }

    fun start() {
        if (job?.isActive == true || isReady()) return
        follow(downloader.start(urls))
    }

    fun cancel() {
        job?.cancel()
        downloader.activeIds.forEach(downloader::remove)
        _state.value = ModelDownloadState.Idle
    }

    private fun follow(ids: List<Long>) {
        _state.value = ModelDownloadState.Downloading(0, null, waiting = false)
        job = viewModelScope.launch {
            while (true) {
                val all = ids.map(downloader::status)
                val failed = all.firstNotNullOfOrNull { it as? DownloadStatus.Failed }
                when {
                    all.any { it == null } -> {
                        ids.forEach(downloader::remove)
                        _state.value = ModelDownloadState.Idle
                        return@launch
                    }
                    failed != null -> {
                        ids.forEach(downloader::remove)
                        _state.value = ModelDownloadState.Failed(failed.code)
                        return@launch
                    }
                    all.all { it == DownloadStatus.Done } -> {
                        // The repository shows Importing from here; a wrong file ends Invalid, as with a picked file.
                        _state.value = ModelDownloadState.Idle
                        try {
                            install(ids.map(downloader::source))
                        } finally {
                            ids.forEach(downloader::remove)
                        }
                        return@launch
                    }
                    else -> {
                        val done = all.indices.sumOf { i ->
                            when (val s = all[i]) {
                                is DownloadStatus.Running -> s.done.coerceAtMost(sizes[i])
                                else -> sizes[i]
                            }
                        }
                        val waiting = all.any { it is DownloadStatus.Running && it.waiting }
                        _state.value = ModelDownloadState.Downloading(done, sizes.sum(), waiting)
                    }
                }
                delay(pollMs)
            }
        }
    }
}
