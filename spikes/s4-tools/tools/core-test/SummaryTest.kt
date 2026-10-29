package io.github.saadahmed1122.bibi.spike.s4.coretest

import io.github.saadahmed1122.bibi.spike.s4.Gate
import io.github.saadahmed1122.bibi.spike.s4.Summary

// Summary of one S4 run file: p50/p95 per (action, path, mode) and the gate verdict vs 300 ms.

private const val T0 = 1_760_000_000_000L

private fun row(
    action: String,
    totalMs: Double,
    mode: String = if (action == "flashlight") "torch" else "dry_run",
    path: String = "shortcut",
    kind: String = "bench",
    ok: Boolean = true,
    i: Int = 0,
): String {
    val cols = mapOf(
        "time" to "2026-10-29 10:00:00", "epoch_ms" to (T0 + i).toString(), "run_id" to T0.toString(),
        "kind" to kind, "action" to action, "path" to path, "mode" to mode, "ok" to ok.toString(),
        "total_ms" to "%.3f".format(java.util.Locale.US, totalMs), "parse_ms" to "0.100", "policy_ms" to "0.010",
        "build_ms" to "1.000", "dispatch_ms" to "2.000", "detail" to "x",
    )
    return Summary.HEADER.split(",").joinToString(",") { cols.getValue(it) }
}

private val ACTIONS = listOf("open_app", "set_alarm", "start_timer", "flashlight", "dial")

private fun run(rows: List<String>) = (listOf(Summary.HEADER) + rows).joinToString("\n") + "\n"

private fun bench(n: Int = 20, ms: (String, Int) -> Double = { _, k -> 10.0 + k }): List<String> =
    ACTIONS.flatMap { a -> (1..n).map { k -> row(a, ms(a, k), i = k) } }

fun summaryTests() {
    val values = (1..20).map { it.toDouble() }
    checkEq("p50 nearest rank of 1..20", 10.0, Summary.percentile(values, 0.50))
    checkEq("p95 nearest rank of 1..20", 19.0, Summary.percentile(values, 0.95))
    checkEq("p95 of one value", 7.0, Summary.percentile(listOf(7.0), 0.95))
    checkEq("percentile of empty", null, Summary.percentile(emptyList(), 0.5))

    val pass = Summary.from(run(bench()))
    checkEq("5×20 fast runs → PASS", Gate.PASS, pass.gate)
    val timer = pass.groups.single { it.action == "start_timer" && it.mode == "dry_run" }
    checkEq("group n", 20, timer.nOk)
    checkEq("group p50", 20.0, timer.p50Ms)
    checkEq("group p95", 29.0, timer.p95Ms)

    val slow = Summary.from(run(bench { a, k -> if (a == "dial" && k >= 19) 450.0 else 20.0 }))
    checkEq("dial p95 450 ms → FAIL", Gate.FAIL, slow.gate)
    check("FAIL names dial", slow.gateReasons.any { it.contains("dial") }) { "${slow.gateReasons}" }

    val edge = Summary.from(run(bench { a, k -> if (a == "flashlight" && k >= 19) 300.0 else 20.0 }))
    checkEq("p95 exactly 300 ms → FAIL (gate is under 300)", Gate.FAIL, edge.gate)

    val short = Summary.from(run(bench(n = 19)))
    checkEq("19 runs → INCOMPLETE", Gate.INCOMPLETE, short.gate)

    val missing = Summary.from(run(bench().filterNot { it.contains(",open_app,") }))
    checkEq("missing action → INCOMPLETE", Gate.INCOMPLETE, missing.gate)
    check("INCOMPLETE names open_app", missing.gateReasons.any { it.contains("open_app") }) { "${missing.gateReasons}" }

    val errors = Summary.from(run(bench() + row("set_alarm", 5.0, ok = false, i = 99)))
    checkEq("a failed attempt in a gate group → INCOMPLETE", Gate.INCOMPLETE, errors.gate)
    check("names the failure", errors.gateReasons.any { it.contains("set_alarm") && it.contains("failed") }) { "${errors.gateReasons}" }

    // Rows outside the gate groups are reported but never change the verdict.
    val extra = Summary.from(
        run(
            bench() +
                (1..3).map { row("set_alarm", 900.0, mode = "real", kind = "live", i = it) } +
                (1..5).map { row("flashlight", 900.0, path = "fake_llm", kind = "live", i = it) },
        ),
    )
    checkEq("slow real launches and LLM path don't change the gate", Gate.PASS, extra.gate)
    val real = extra.groups.single { it.action == "set_alarm" && it.mode == "real" }
    checkEq("real launches reported (n=3)", 3, real.nOk)
    check("describe mentions real launches", extra.describe().contains("real")) { extra.describe() }
    check("describe prints the gate line", extra.describe().contains("S4 shortcut gate: PASS")) { extra.describe() }

    // Columns are found by header name.
    val header = Summary.HEADER.split(",")
    val reordered = header.reversed()
    val csv = buildString {
        appendLine(reordered.joinToString(","))
        for (r in bench()) {
            val cells = header.zip(r.split(",")).toMap()
            appendLine(reordered.joinToString(",") { cells.getValue(it) })
        }
    }
    checkEq("reordered columns → same verdict", Gate.PASS, Summary.from(csv).gate)
    checkEq("empty file → INCOMPLETE", Gate.INCOMPLETE, Summary.from("").gate)
}
