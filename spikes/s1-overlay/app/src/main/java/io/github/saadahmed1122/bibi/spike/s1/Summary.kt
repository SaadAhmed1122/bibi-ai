package io.github.saadahmed1122.bibi.spike.s1

import java.util.Locale
import kotlin.math.abs

/**
 * Computes run results and gate validity from one run file written by [SpikeLog]
 * (exit reasons are kept separately). Uses only the Kotlin stdlib and java.util, so it can be
 * tested on a plain JVM (tools/summary-test). Columns are found by header name.
 */
class Summary private constructor(
    val mode: String,
    val minutes: Double,
    val startPct: Int?,
    val endPct: Int?,
    /** Time-weighted mean of |current_ua| over sample rows, in mA; null with fewer than two samples. */
    val meanCurrentMa: Double?,
    val avgFpsScreenOn: Double?,
    val maxPssMb: Double?,
    val events: Map<String, Int>,
    val invalidReasons: List<String>,
    val conditions: List<String>,
) {

    val valid: Boolean get() = invalidReasons.isEmpty()

    /** Battery %/h estimated from [meanCurrentMa] and [CAPACITY_MAH]. */
    val pctPerHourFromCurrent: Double? get() = meanCurrentMa?.let { it / CAPACITY_MAH * 100.0 }

    /** Coarse %/h from the 1 % battery_pct steps, first to last row. */
    val pctPerHourCoarse: Double?
        get() = if (startPct != null && endPct != null && minutes > 0.0) {
            (startPct - endPct) / (minutes / 60.0)
        } else {
            null
        }

    /**
     * Battery-percent drain per mA of mean current for this run: [pctPerHourCoarse] / [meanCurrentMa].
     * Only for runs of at least 60 min with a drop of at least [MIN_PCT_DROP] points (1 % steps are too
     * coarse otherwise); null then. On a baseline run it converts a mA difference into %/h without
     * trusting the rated capacity.
     */
    val pctPerMa: Double?
        get() {
            val drop = if (startPct != null && endPct != null) startPct - endPct else return null
            val ma = meanCurrentMa ?: return null
            val pctH = pctPerHourCoarse ?: return null
            return if (minutes >= MIN_MINUTES && drop >= MIN_PCT_DROP && ma > 0.0) pctH / ma else null
        }

    fun validityLine(): String =
        if (valid) "VALID for gate: yes" else "VALID for gate: no — ${invalidReasons.joinToString("; ")}"

    fun describe(): String {
        if (minutes <= 0.0) return "No samples yet. A sample is written every 10 s while running."
        fun f(v: Double?, digits: Int = 2) = v?.let { "%.${digits}f".format(Locale.US, it) } ?: "n/a"
        return buildString {
            appendLine("Run mode:        $mode")
            appendLine("Duration:        ${f(minutes, 1)} min")
            appendLine("Mean current:    ${f(meanCurrentMa, 1)} mA (time-weighted, sample rows)")
            appendLine("Drain (current): ${f(pctPerHourFromCurrent)} %/h (at $CAPACITY_MAH mAh)")
            appendLine("Battery:         ${startPct ?: "?"}% → ${endPct ?: "?"}%")
            appendLine("Drain (pct):     ${f(pctPerHourCoarse)} %/h (1% steps; coarse)")
            appendLine("pct_per_ma:      ${f(pctPerMa, 5)} (%/h per mA; needs ≥ 60 min and ≥ $MIN_PCT_DROP points drop)")
            appendLine("Avg fps (screen on): ${f(avgFpsScreenOn, 1)}")
            appendLine("Max PSS:         ${f(maxPssMb, 1)} MB")
            appendLine("Events:          ${events.entries.joinToString { "${it.key}=${it.value}" }.ifEmpty { "none" }}")
            appendLine("Conditions:")
            conditions.forEach { appendLine("  $it") }
            appendLine(validityLine())
            appendLine()
            appendLine("Gate (SPEC.md §2): Bibi cost = mean current (Bibi run) − mean current (baseline run);")
            appendLine("3 %/h ≈ 150 mA on $CAPACITY_MAH mAh. Needs 60 min with no kill.")
            append(
                "Relative cost: Bibi cost %/h ≈ (Bibi mA − baseline mA) × baseline pct_per_ma " +
                    "(each run's mean current and pct_per_ma are printed per run; compare the two by hand).",
            )
        }
    }

    companion object {
        /** vivo Y17s rated capacity (GSMArena) [VERIFY]; the charge counter can't confirm it (RESULTS.md). */
        const val CAPACITY_MAH = 5000

        const val MIN_MINUTES = 60.0

        /** Minimum battery_pct drop for [pctPerMa]. */
        const val MIN_PCT_DROP = 3

        /**
         * Event kinds (written by OverlayService) that mean the run was not one clean start.
         * `start_blocked` is not here: it only goes to exit_reasons.csv, never to a run file.
         */
        val RESTART_KINDS = setOf("restart", "restart_blocked", "baseline_restart", "baseline_restart_blocked")

        /** A run file's first row must be one of these, and its last row `stop`. */
        val START_KINDS = setOf("start", "start_baseline")

        /** User touches on Bibi; they change what is measured (toast, window moves). */
        val TOUCH_KINDS = setOf("tap", "move")

        fun from(csv: String): Summary {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val header = lines.firstOrNull()?.split(",").orEmpty()
            val idx = header.withIndex().associate { it.value.trim() to it.index }
            val rows = lines.drop(1).map { Row(it.split(","), idx) }.filter { it.long("epoch_ms") != null }

            val epochs = rows.mapNotNull { it.long("epoch_ms") }
            val minutes = if (epochs.size >= 2) (epochs.max() - epochs.min()) / 60_000.0 else 0.0
            val samples = rows.filter { it["kind"] == "sample" }
            val pcts = rows.mapNotNull { it.int("battery_pct") }
            val modes = rows.map { it["mode"] }.filter { it.isNotEmpty() && it != "-" }.distinct()
            val kinds = rows.map { it["kind"] }

            val reasons = mutableListOf<String>()
            if (minutes < MIN_MINUTES) reasons += "shorter than 60 min (%.1f min)".format(Locale.US, minutes)
            if ("screen_off" in kinds || samples.any { it["screen_on"] == "false" }) reasons += "screen off during run"
            if (samples.any { it["charging"] == "true" }) reasons += "charging during run"
            val restarts = kinds.filter { it in RESTART_KINDS }.distinct()
            if (restarts.isNotEmpty()) reasons += "restart or blocked start (${restarts.joinToString("/")})"
            if ("overlay_error" in kinds) reasons += "overlay window error"
            if (modes.size > 1) reasons += "mixed modes (${modes.joinToString("/")})"
            if ("hide" in kinds) reasons += "Bibi hidden during run"
            val touches = kinds.filter { it in TOUCH_KINDS }.distinct()
            if (touches.isNotEmpty()) reasons += "touched during run (${touches.joinToString("/")})"
            if (kinds.firstOrNull() !in START_KINDS || kinds.lastOrNull() != "stop") {
                reasons += "no clean stop (process died?)"
            }

            return Summary(
                mode = modes.lastOrNull() ?: "-",
                minutes = minutes,
                startPct = pcts.firstOrNull(),
                endPct = pcts.lastOrNull(),
                meanCurrentMa = timeWeightedMeanMa(samples),
                avgFpsScreenOn = samples.filter { it["screen_on"] == "true" }.mapNotNull { it.double("fps") }
                    .takeIf { it.isNotEmpty() }?.average(),
                maxPssMb = samples.mapNotNull { it.long("pss_kb") }.maxOrNull()?.div(1024.0),
                events = kinds.filter { it != "sample" }.groupingBy { it }.eachCount(),
                invalidReasons = reasons,
                conditions = conditions(rows),
            )
        }

        /** Each sample's |current| is weighted by the time to the next sample; the last one has no weight. */
        private fun timeWeightedMeanMa(samples: List<Row>): Double? {
            val points = samples.mapNotNull { r ->
                val t = r.long("epoch_ms") ?: return@mapNotNull null
                val ua = r.long("current_ua") ?: return@mapNotNull null
                t to abs(ua) / 1000.0
            }.sortedBy { it.first }
            var weighted = 0.0
            var total = 0L
            for (i in 0 until points.size - 1) {
                val dt = points[i + 1].first - points[i].first
                weighted += points[i].second * dt
                total += dt
            }
            return if (total > 0) weighted / total else null
        }

        private fun conditions(rows: List<Row>): List<String> {
            fun range(values: List<Double>, digits: Int, unit: String = ""): String =
                if (values.isEmpty()) {
                    "n/a"
                } else {
                    val fmt = "%.${digits}f"
                    "${fmt.format(Locale.US, values.min())}–${fmt.format(Locale.US, values.max())}$unit"
                }
            fun distinct(col: String) = rows.map { it[col] }.filter { it.isNotEmpty() }.distinct()
                .joinToString("/").ifEmpty { "n/a" }
            val powerSave = rows.map { it["power_save"] }.filter { it.isNotEmpty() }
            return listOf(
                "brightness: ${range(rows.mapNotNull { it.double("brightness") }, 0)}" +
                    " (mode ${distinct("brightness_mode")})",
                "refresh_hz: ${distinct("refresh_hz")}",
                "power_save ever on: ${if (powerSave.isEmpty()) "n/a" else ("true" in powerSave).toString()}",
                "batt temp: ${range(rows.mapNotNull { it.double("batt_temp_dc")?.div(10.0) }, 1, " °C")}",
                "voltage: ${range(rows.mapNotNull { it.double("voltage_mv") }, 0, " mV")}",
            )
        }
    }

    /** A CSV row read by column name; missing columns or cells read as "". */
    private class Row(private val cells: List<String>, private val idx: Map<String, Int>) {
        operator fun get(col: String): String = idx[col]?.let { cells.getOrNull(it) }?.trim().orEmpty()
        fun long(col: String) = get(col).toLongOrNull()
        fun int(col: String) = get(col).toIntOrNull()
        fun double(col: String) = get(col).toDoubleOrNull()
    }
}
