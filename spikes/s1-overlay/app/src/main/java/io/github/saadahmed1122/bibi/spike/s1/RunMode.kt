package io.github.saadahmed1122.bibi.spike.s1

/**
 * How a run measures, chosen at start and fixed until Stop (SPEC.md §2).
 * [logName] goes into the `mode` column of every log row; [calm] into the `calm` column.
 */
enum class RunMode(val logName: String, val label: String, val showsBibi: Boolean, val calm: Boolean) {
    /** Same service and logging, no overlay: separates Bibi's cost from the screen's. */
    BASELINE("baseline", "baseline (no Bibi)", showsBibi = false, calm = false),
    BIBI_12("bibi12", "Bibi 12 fps", showsBibi = true, calm = false),
    BIBI_CALM("bibiCalm", "Bibi Calm 2 fps", showsBibi = true, calm = true),
    ;

    companion object {
        fun fromLogName(name: String?): RunMode? = entries.firstOrNull { it.logName == name }
    }
}
