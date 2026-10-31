package io.github.saadahmed1122.bibi.core.common

/** The app's two processes (docs/architecture.md §1). */
object ProcessNames {
    const val INFERENCE_SUFFIX = ":inference"

    /** True for the `:inference` process, which runs only the model service and skips UI wiring. */
    fun isInference(processName: String): Boolean = processName.endsWith(INFERENCE_SUFFIX)
}
