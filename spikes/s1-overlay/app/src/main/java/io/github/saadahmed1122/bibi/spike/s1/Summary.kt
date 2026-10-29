package io.github.saadahmed1122.bibi.spike.s1

import java.util.Locale

/** Computes run results from one run file written by [SpikeLog] (exit reasons are kept separately). */
class Summary private constructor(
    private val mode: String,
    private val minutes: Double,
    private val startPct: Int?,
    private val endPct: Int?,
    private val chargeDropMah: Double?,
    private val avgFpsScreenOn: Double?,
    private val maxPssMb: Double?,
    private val events: Map<String, Int>,
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
            appendLine()
            appendLine("Gate (SPEC.md §2): Bibi cost = Bibi run drain − baseline run drain ≤ 3 %/h,")
            append("and 60 min with no kill. Compare this run with a baseline run.")
        }
    }

    companion object {
        fun from(csv: String): Summary {
            // Columns: see SpikeLog.HEADER (0 time, 1 epoch_ms, 2 run_id, 3 kind, 4 detail, 5 battery_pct,
            // 6 charge_uah, 7 current_ua, 8 charging, 9 screen_on, 10 thermal, 11 frames, 12 fps, 13 pss_kb, 14 calm, 15 mode)
            val rows = csv.lineSequence().drop(1).filter { it.isNotBlank() }.map { it.split(",") }
                .filter { it.size >= 16 }.toList()
            val samples = rows.filter { it[3] == "sample" || it[3].startsWith("start") || it[3] == "stop" || it[3] == "restart" }
            val epochs = samples.map { it[1].toLong() }
            val minutes = if (epochs.size >= 2) (epochs.last() - epochs.first()) / 60_000.0 else 0.0
            val charges = samples.mapNotNull { it[6].toIntOrNull() }
            val fps = rows.filter { it[3] == "sample" && it[9] == "true" }.mapNotNull { it[12].toDoubleOrNull() }
            return Summary(
                mode = rows.lastOrNull { it[15] != "-" }?.get(15) ?: "-",
                minutes = minutes,
                startPct = samples.firstOrNull()?.get(5)?.toIntOrNull(),
                endPct = samples.lastOrNull()?.get(5)?.toIntOrNull(),
                chargeDropMah = if (charges.size >= 2) (charges.first() - charges.last()) / 1000.0 else null,
                avgFpsScreenOn = fps.takeIf { it.isNotEmpty() }?.average(),
                maxPssMb = rows.mapNotNull { it[13].toLongOrNull() }.maxOrNull()?.div(1024.0),
                events = rows.map { it[3] }.filter { it != "sample" }.groupingBy { it }.eachCount(),
            )
        }
    }
}
