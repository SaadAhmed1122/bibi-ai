package io.github.saadahmed1122.bibi.domain.model

sealed interface ModelState {
    data object Missing : ModelState

    /** [progress] is in 0..1. */
    data class Importing(val progress: Float) : ModelState

    data class Ready(val path: String) : ModelState

    /** [reason] is a short code such as "size" or "checksum"; the UI maps it to a message. */
    data class Invalid(val reason: String) : ModelState
}
