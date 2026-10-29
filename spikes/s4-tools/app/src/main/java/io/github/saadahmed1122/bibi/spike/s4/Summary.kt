package io.github.saadahmed1122.bibi.spike.s4

import java.util.Locale
import kotlin.math.ceil

// Pure Kotlin (no android.*): tested on a plain JVM by tools/core-test/run.sh.

enum class Gate { PASS, FAIL, INCOMPLETE }

data class GroupStats(
    val kind: String,
    val action: String,
    val path: String,
    val mode: String,
    val nOk: Int,
    val nFailed: Int,
    val p50Ms: Double?,
    val p95Ms: Double?,
    val maxMs: Double?,
)

/**
 * Reads one S4 run file (written by SpikeLog; columns found by header name) and computes p50/p95
 * of `total_ms` per (kind, action, path, mode), plus the S4 gate verdict (PRD 0.7: shortcut path
 * under 300 ms, p50 and p95 over 20 runs per action).
 *
 * Gate groups are the benchmark rows of the shortcut path: `dry_run` for open_app, set_alarm,
 * start_timer and dial, `torch` for flashlight. Each needs ≥ [MIN_RUNS] successful runs, no failed
 * attempt, and p95 < [LIMIT_MS] (p50 ≤ p95, so p95 decides). Real launches and the fake-LLM path
 * are reported but never change the verdict.
 */
class Summary private constructor(
    val groups: List<GroupStats>,
    val gate: Gate,
    val gateReasons: List<String>,
) {

    fun describe(): String = buildString {
        if (groups.isEmpty()) {
            appendLine("No actions logged yet. Run the benchmark, then the real launches.")
        } else {
            appendLine("kind  action       path      mode     n  fail   p50 ms   p95 ms   max ms")
            for (g in groups) {
                appendLine(
                    String.format(
                        Locale.US, "%-5s %-12s %-9s %-7s %3d %4d %8s %8s %8s",
                        g.kind, g.action, g.path, g.mode, g.nOk, g.nFailed, f(g.p50Ms), f(g.p95Ms), f(g.maxMs),
                    ),
                )
            }
        }
        appendLine()
        appendLine("S4 shortcut gate: $gate (bench, shortcut path, ≥ $MIN_RUNS runs per action, p95 < ${LIMIT_MS.toInt()} ms)")
        gateReasons.forEach { appendLine("  - $it") }
        appendLine("Timed (total_ms): submit → startActivity() returned for real launches, → resolveActivity() returned")
        append("for dry runs, → onTorchModeChanged for the torch. Real launches (n=3 each) and the LLM path are info only.")
    }

    companion object {
        const val HEADER =
            "time,epoch_ms,run_id,kind,action,path,mode,ok,total_ms,parse_ms,policy_ms,build_ms,dispatch_ms,detail"
        const val MIN_RUNS = 20
        const val LIMIT_MS = 300.0

        /** The gate group's mode per action. */
        val GATE_MODES: Map<String, String> = linkedMapOf(
            "open_app" to "dry_run",
            "set_alarm" to "dry_run",
            "start_timer" to "dry_run",
            "flashlight" to "torch",
            "dial" to "dry_run",
        )

        /** Nearest-rank percentile: the ceil(q·n)-th smallest value; null for no values. */
        fun percentile(values: List<Double>, q: Double): Double? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val rank = ceil(q * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }

        fun from(csv: String): Summary {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val header = lines.firstOrNull()?.split(",").orEmpty()
            val idx = header.withIndex().associate { it.value.trim() to it.index }
            val rows = lines.drop(1).map { Row(it.split(","), idx) }
                .filter { it["kind"] == "bench" || it["kind"] == "live" }

            val groups = rows.groupBy { listOf(it["kind"], it["action"], it["path"], it["mode"]) }
                .map { (key, rs) ->
                    val ok = rs.filter { it["ok"] == "true" }.mapNotNull { it.double("total_ms") }
                    GroupStats(
                        kind = key[0], action = key[1], path = key[2], mode = key[3],
                        nOk = ok.size,
                        nFailed = rs.size - ok.size,
                        p50Ms = percentile(ok, 0.50),
                        p95Ms = percentile(ok, 0.95),
                        maxMs = ok.maxOrNull(),
                    )
                }
                .sortedWith(compareBy({ it.kind }, { it.path }, { it.action }, { it.mode }))

            val fails = mutableListOf<String>()
            val incomplete = mutableListOf<String>()
            for ((action, mode) in GATE_MODES) {
                val g = groups.firstOrNull { it.kind == "bench" && it.path == "shortcut" && it.action == action && it.mode == mode }
                when {
                    g == null -> incomplete += "$action: no $mode benchmark runs"
                    g.nFailed > 0 -> incomplete += "$action: ${g.nFailed} failed attempt(s) (see detail column)"
                    g.nOk < MIN_RUNS -> incomplete += "$action: only ${g.nOk} of $MIN_RUNS runs"
                    g.p95Ms!! >= LIMIT_MS -> fails += "$action: p95 ${f(g.p95Ms)} ms ≥ ${LIMIT_MS.toInt()} ms (p50 ${f(g.p50Ms)} ms)"
                }
            }
            val gate = when {
                fails.isNotEmpty() -> Gate.FAIL
                incomplete.isNotEmpty() -> Gate.INCOMPLETE
                else -> Gate.PASS
            }
            return Summary(groups, gate, fails + incomplete)
        }

        private fun f(v: Double?) = v?.let { String.format(Locale.US, "%.1f", it) } ?: "n/a"
    }

    private class Row(private val cells: List<String>, private val idx: Map<String, Int>) {
        operator fun get(col: String): String = idx[col]?.let { cells.getOrNull(it) }?.trim().orEmpty()
        fun double(col: String) = get(col).toDoubleOrNull()
    }
}
