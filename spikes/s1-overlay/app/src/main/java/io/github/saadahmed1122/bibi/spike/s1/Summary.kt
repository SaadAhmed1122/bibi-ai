package io.github.saadahmed1122.bibi.spike.s1

import java.util.Locale

/** Computes run results from the CSV log written by [SpikeLog]. */
class Summary private constructor(
    private val mode: String,
    private val minutes: Double,
    private val startPct: Int?,
    private val endPct: Int?,
    private val chargeDropMah: Double?,
    private val avgFpsScreenOn: Double?,
    private val maxPssMb: Double?,
    private val events: Map<String, Int>,
    private val exitReasons: List<String>,
) {

    fun describe(): String {
        if (minutes <= 0.0) return "No samples yet. A sample is written every 60 s while running."
        val hours = minutes / 60.0
        fun f(v: Double?, digits: Int = 2) = v?.let { "%.${digits}f".format(Locale.US, it) } ?: "n/a"
        val pctDrop = if (startPct != null && endPct != null) (startPct - endPct).toDouble() else null
        return buildString {
            appendLine("Run mode:        $mode")
            appendLine("Duration:        ${f(minutes, 1)} min")
            appendLine("Battery:         ${startPct ?: "?"}% → ${endPct ?: "?"}%")
            appendLine("Drain:           ${f(pctDrop?.div(hours))} %/h (1% steps; coarse)")
            appendLine("Charge counter:  ${f(chargeDropMah?.div(hours), 1)} mAh/h")
            appendLine("Avg fps (screen on): ${f(avgFpsScreenOn, 1)}")
            appendLine("Max PSS:         ${f(maxPssMb, 1)} MB")
            appendLine("Events:          ${events.entries.joinToString { "${it.key}=${it.value}" }.ifEmpty { "none" }}")
            appendLine("Exit reasons:    ${if (exitReasons.isEmpty()) "none" else ""}")
            exitReasons.forEach { appendLine("  - $it") }
            appendLine()
            appendLine("Gate (SPEC.md §2): Bibi cost = Bibi run drain − baseline run drain ≤ 3 %/h,")
            append("and 60 min with no kill. Compare this run with a baseline run.")
        }
    }

    companion object {
        fun from(csv: String): Summary {
            val rows = csv.lineSequence().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
                .filter { it.size >= 15 }.toList()
            val samples = rows.filter { it[2] == "sample" || it[2].startsWith("start") || it[2] == "stop" || it[2] == "restart" }
            val epochs = samples.map { it[1].toLong() }
            val minutes = if (epochs.size >= 2) (epochs.last() - epochs.first()) / 60_000.0 else 0.0
            val charges = samples.mapNotNull { it[5].toIntOrNull() }
            val fps = rows.filter { it[2] == "sample" && it[8] == "true" }.mapNotNull { it[11].toDoubleOrNull() }
            return Summary(
                mode = rows.lastOrNull { it[14] != "-" }?.get(14) ?: "-",
                minutes = minutes,
                startPct = samples.firstOrNull()?.get(4)?.toIntOrNull(),
                endPct = samples.lastOrNull()?.get(4)?.toIntOrNull(),
                chargeDropMah = if (charges.size >= 2) (charges.first() - charges.last()) / 1000.0 else null,
                avgFpsScreenOn = fps.takeIf { it.isNotEmpty() }?.average(),
                maxPssMb = rows.mapNotNull { it[12].toLongOrNull() }.maxOrNull()?.div(1024.0),
                events = rows.map { it[2] }.filter { it != "sample" }.groupingBy { it }.eachCount(),
                exitReasons = rows.filter { it[2] == "exit_reason" }.map { it[3] },
            )
        }
    }
}
