package io.github.saadahmed1122.bibi.spike.s3

import java.util.Locale
import kotlin.math.ceil

/** PRD 0.7 gates (binding). */
object Gates {
    /** S3a: TTS real-time factor ≤ 1.0. */
    const val TTS_RTF_MAX = 1.0

    /** S3b: STT real-time factor ≤ 0.7. */
    const val STT_RTF_MAX = 0.7

    /** S3b: STT peak memory ≤ 600 MB (checked on the `:inference` process's peak PSS). */
    const val STT_PEAK_MB_MAX = 600.0

    /** Fewer measured items than this (warmup excluded) gives INCOMPLETE. */
    const val MIN_ITEMS = 6
}

enum class Verdict { PASS, BORDERLINE, INCOMPLETE, FAIL }

/** Results of one (device, model, thread count), merged over all its run files. */
class ModelResult(
    val device: String,
    val task: Task,
    val model: String,
    val family: Family?,
    val threads: Int,
    val runs: Int,
    val items: Int,
    val medianRtf: Double?,
    val p95Rtf: Double?,
    val peakPssMb: Double?,
    val peakRssMb: Double?,
    val medianLoadMs: Double?,
    val errors: Int,
    val maxThermal: Int?,
    val verdict: Verdict,
    val reasons: List<String>,
) {
    val gateRtf: Double get() = if (task == Task.TTS) Gates.TTS_RTF_MAX else Gates.STT_RTF_MAX
}

/**
 * Reads run files written by [RunLog] and computes per-model statistics, PASS/FAIL against the
 * PRD 0.7 gates, and the PRD 0.7 "if it fails" recommendation. Pure Kotlin (JVM-testable via
 * tools/summary-test). Columns are found by header name.
 *
 * Verdict rules (SPEC.md §2):
 * - FAIL if the inference process died during a run, if median RTF is above the gate, or (STT)
 *   if peak PSS is above 600 MB.
 * - BORDERLINE [VERIFY] if the median passes but p95 does not, or (STT) PSS passes but the
 *   peak RSS (VmHWM) is above 600 MB.
 * - INCOMPLETE if no clean `stop`, any `error` row, fewer than [Gates.MIN_ITEMS] items, or (STT)
 *   no memory reading.
 * - PASS otherwise.
 */
class Summary private constructor(val results: List<ModelResult>) {

    fun recommendationTts(device: String): String {
        val kokoro = best(device, Family.KOKORO)
        val piper = best(device, Family.PIPER)
        return when {
            kokoro == null && piper == null -> "No TTS runs yet."
            kokoro?.verdict == Verdict.PASS -> "Kokoro passes (${label(kokoro)}): keep Kokoro as the voice on this device."
            piper?.verdict == Verdict.PASS ->
                "Kokoro ${state(kokoro)}; Piper passes (${label(piper)}): switch to the lighter voice on this tier (PRD 0.7)."
            kokoro?.verdict == Verdict.BORDERLINE || piper?.verdict == Verdict.BORDERLINE ->
                "Borderline [VERIFY]: Kokoro ${state(kokoro)}, Piper ${state(piper)}. Repeat runs before deciding."
            kokoro == null || piper == null ->
                "Kokoro ${state(kokoro)}, Piper ${state(piper)}: measure both before deciding."
            kokoro.verdict == Verdict.INCOMPLETE || piper.verdict == Verdict.INCOMPLETE ->
                "Kokoro ${state(kokoro)}, Piper ${state(piper)}: finish the incomplete runs before deciding."
            else ->
                "Neither voice reaches RTF ≤ ${Gates.TTS_RTF_MAX}: no spoken replies on this device tier; " +
                    "S3 failing does not stop the project (PRD 0.7)."
        }
    }

    fun recommendationStt(device: String): String {
        val small = best(device, Family.STREAMING)
        val parakeet = best(device, Family.PARAKEET)
        val parakeetNote = when (parakeet?.verdict) {
            null -> "Parakeet not measured."
            Verdict.PASS -> "Parakeet also passes (${label(parakeet)}): possible upgrade on this device."
            else -> "Parakeet ${state(parakeet)}: keep it off on this device."
        }
        return when (small?.verdict) {
            Verdict.PASS -> "Small streaming recognizer passes (${label(small)}): use it as the default. $parakeetNote"
            Verdict.BORDERLINE -> "Small streaming recognizer borderline [VERIFY]: repeat runs. $parakeetNote"
            null, Verdict.INCOMPLETE -> "Small streaming recognizer ${state(small)}: measure it first. $parakeetNote"
            Verdict.FAIL -> if (parakeet?.verdict == Verdict.PASS) {
                "Small streaming recognizer fails but Parakeet passes [VERIFY, unexpected]: " +
                    "push-to-talk short clips with Parakeet; typed input fallback."
            } else {
                "Small streaming recognizer fails: push-to-talk short clips; typed input fallback (PRD 0.7). $parakeetNote"
            }
        }
    }

    fun devices(): List<String> = results.map { it.device }.distinct()

    fun describe(): String {
        if (results.isEmpty()) return "No runs yet."
        fun f(v: Double?, d: Int = 2) = v?.let { "%.${d}f".format(Locale.US, it) } ?: "n/a"
        return buildString {
            for (device in devices()) {
                appendLine("Device: $device")
                for (r in results.filter { it.device == device }) {
                    appendLine("${r.task.logName.uppercase()} ${r.model} t${r.threads}: ${r.verdict}")
                    appendLine(
                        "  RTF median ${f(r.medianRtf, 3)}, p95 ${f(r.p95Rtf, 3)} (gate ≤ ${r.gateRtf}); " +
                            "items ${r.items} in ${r.runs} run(s)",
                    )
                    appendLine(
                        "  peak PSS ${f(r.peakPssMb, 0)} MB, peak RSS ${f(r.peakRssMb, 0)} MB" +
                            (if (r.task == Task.STT) " (gate ≤ ${Gates.STT_PEAK_MB_MAX.toInt()} MB)" else "") +
                            "; load ${f(r.medianLoadMs?.div(1000.0), 1)} s; errors ${r.errors}; max thermal ${r.maxThermal ?: "n/a"}",
                    )
                    r.reasons.forEach { appendLine("  - $it") }
                }
                appendLine("S3a TTS: ${recommendationTts(device)}")
                appendLine("S3b STT: ${recommendationStt(device)}")
                appendLine()
            }
        }.trimEnd()
    }

    private fun best(device: String, family: Family): ModelResult? =
        results.filter { it.device == device && it.family == family }
            .minWithOrNull(compareBy<ModelResult>({ it.verdict.ordinal }, { it.medianRtf ?: Double.MAX_VALUE }))

    private fun label(r: ModelResult) = "${r.model}, ${r.threads} threads, median RTF ${"%.3f".format(Locale.US, r.medianRtf ?: 0.0)}"

    private fun state(r: ModelResult?) = when (r?.verdict) {
        null -> "not measured"
        Verdict.PASS -> "passes"
        Verdict.BORDERLINE -> "is borderline"
        Verdict.INCOMPLETE -> "is incomplete"
        Verdict.FAIL -> "fails"
    }

    companion object {
        fun from(csvs: List<String>): Summary {
            val runs = csvs.map { parse(it) }.filter { it.isNotEmpty() }
            val byKey = runs.groupBy { rows ->
                val r = rows.first()
                listOf(r["device"], r["task"], r["model"], r["threads"])
            }
            val results = byKey.mapNotNull { (key, group) -> result(key, group) }
                .sortedWith(compareBy({ it.device }, { it.task.ordinal }, { it.model }, { it.threads }))
            return Summary(results)
        }

        private fun result(key: List<String>, runs: List<List<Row>>): ModelResult? {
            val task = Task.entries.firstOrNull { it.logName == key[1] } ?: return null
            val rows = runs.flatten()
            val items = rows.filter { it["kind"] == RunLog.ITEM }
            val rtfs = items.mapNotNull { it.double("rtf") }
            val pss = rows.mapNotNull { it.long("pss_peak_kb") }.maxOrNull()?.div(1024.0)
            val rss = rows.mapNotNull { it.long("vmhwm_kb") }.maxOrNull()?.div(1024.0)
            val errors = rows.count { it["kind"] == RunLog.ERROR }
            val died = rows.any { it["kind"] == RunLog.PROCESS_DIED }
            val unfinished = runs.count { run -> run.none { it["kind"] == RunLog.STOP } }
            val median = median(rtfs)
            val p95 = p95(rtfs)
            val gate = if (task == Task.TTS) Gates.TTS_RTF_MAX else Gates.STT_RTF_MAX

            val fail = mutableListOf<String>()
            val borderline = mutableListOf<String>()
            val incomplete = mutableListOf<String>()
            if (died) fail += "inference process died during a run (OOM kill? check exit_reasons.csv) [VERIFY cause]"
            if (median != null && median > gate) fail += "median RTF above $gate"
            if (median != null && median <= gate && p95 != null && p95 > gate) borderline += "p95 RTF above $gate [VERIFY]"
            if (task == Task.STT) {
                when {
                    pss == null -> incomplete += "no memory reading"
                    pss > Gates.STT_PEAK_MB_MAX -> fail += "peak PSS above ${Gates.STT_PEAK_MB_MAX.toInt()} MB"
                    rss != null && rss > Gates.STT_PEAK_MB_MAX ->
                        borderline += "PSS within ${Gates.STT_PEAK_MB_MAX.toInt()} MB but peak RSS above it [VERIFY]"
                }
            }
            if (!died && unfinished > 0) incomplete += "$unfinished run(s) without a clean stop"
            if (errors > 0) incomplete += "$errors error row(s)"
            if (rtfs.size < Gates.MIN_ITEMS) incomplete += "only ${rtfs.size} measured items (need ${Gates.MIN_ITEMS})"

            val verdict = when {
                fail.isNotEmpty() -> Verdict.FAIL
                incomplete.isNotEmpty() -> Verdict.INCOMPLETE
                borderline.isNotEmpty() -> Verdict.BORDERLINE
                else -> Verdict.PASS
            }
            return ModelResult(
                device = key[0], task = task, model = key[2], family = Family.fromLog(rows.first()["family"]),
                threads = key[3].toIntOrNull() ?: 0, runs = runs.size, items = rtfs.size,
                medianRtf = median, p95Rtf = p95, peakPssMb = pss, peakRssMb = rss,
                medianLoadMs = median(rows.filter { it["kind"] == RunLog.LOAD }.mapNotNull { it.double("compute_ms") }),
                errors = errors, maxThermal = rows.mapNotNull { it.int("thermal") }.maxOrNull(),
                verdict = verdict, reasons = fail + incomplete + borderline,
            )
        }

        fun median(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val s = values.sorted()
            val m = s.size / 2
            return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2.0
        }

        /** Nearest-rank 95th percentile. */
        fun p95(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val s = values.sorted()
            return s[(ceil(0.95 * s.size).toInt() - 1).coerceIn(0, s.size - 1)]
        }

        private fun parse(csv: String): List<Row> {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val header = lines.firstOrNull()?.split(",").orEmpty()
            val idx = header.withIndex().associate { it.value.trim() to it.index }
            if ("kind" !in idx) return emptyList()
            return lines.drop(1).map { Row(it.split(","), idx) }.filter { it.long("epoch_ms") != null }
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
