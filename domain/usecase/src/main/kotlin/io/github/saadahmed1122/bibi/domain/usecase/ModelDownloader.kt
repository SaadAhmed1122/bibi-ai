package io.github.saadahmed1122.bibi.domain.usecase

/** One system download as last reported (SPEC-model-download.md). */
sealed interface DownloadStatus {
    /** [waiting] is true while paused for network, Wi-Fi or a retry. [total] is null if unknown. */
    data class Running(val done: Long, val total: Long?, val waiting: Boolean = false) : DownloadStatus

    data object Done : DownloadStatus

    /** HTTP status or a DownloadManager ERROR_* code. */
    data class Failed(val code: Int) : DownloadStatus
}

/**
 * Fetches the model file in the background; the app wraps Android's DownloadManager, tests use a
 * fake. The download outlives the app process, so [activeId] survives restarts.
 */
interface ModelDownloader {
    /** The download started earlier and not removed yet, or null. */
    val activeId: Long?

    fun start(url: String): Long

    /** Null when the system no longer knows [id] (e.g. cleared in system settings). */
    fun status(id: Long): DownloadStatus?

    /** The finished file, read through the system (never by path). */
    fun source(id: Long): ModelSource

    /** Stops [id] if it runs, deletes its file and forgets it. Safe to call twice. */
    fun remove(id: Long)
}
