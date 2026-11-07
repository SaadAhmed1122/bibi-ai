package io.github.saadahmed1122.bibi.domain.model

/**
 * The in-app model download as the screens show it (SPEC-model-download.md). Once the bytes are
 * on the phone the check runs as a normal import, so [ModelState.Importing] takes over from here.
 */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState

    /** [total] is null until the server reports a size; [waiting] means paused for network or a retry. */
    data class Downloading(val done: Long, val total: Long?, val waiting: Boolean) : ModelDownloadState {
        val progress: Float? get() = total?.takeIf { it > 0 }?.let { (done.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** [code] is the HTTP status, or a DownloadManager ERROR_* value (1000–1009). */
    data class Failed(val code: Int) : ModelDownloadState {
        val kind: Kind get() = when (code) {
            401, 403 -> Kind.NeedsLogin
            404, 410 -> Kind.NotFound
            ERROR_INSUFFICIENT_SPACE -> Kind.NoSpace
            else -> Kind.Network
        }
    }

    enum class Kind { NeedsLogin, NotFound, NoSpace, Network }

    companion object {
        /** DownloadManager.ERROR_INSUFFICIENT_SPACE; copied so this module stays pure Kotlin. */
        const val ERROR_INSUFFICIENT_SPACE = 1006
    }
}
