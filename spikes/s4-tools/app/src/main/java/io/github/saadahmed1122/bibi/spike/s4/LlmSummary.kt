package io.github.saadahmed1122.bibi.spike.s4

import java.util.Locale

// Pure Kotlin (no android.*): tested on a plain JVM by tools/core-test/run.sh.

/** Per-action metrics of the LLM benchmark; counts are out of [n]. */
data class LlmGroupStats(
    /** Tool wire name, or "none". */
    val expected: String,
    val n: Int,
    /** The router's effective choice (after the confidence threshold) equals [expected]. */
    val routedRight: Int,
    /** The LLM was called (route was a tool). */
    val llmRan: Int,
    /** The first answer was a JSON tool call. */
    val jsonOk: Int,
    /** Actions: a validated call of the expected tool reached the Permission Engine. "none": no call reached it. */
    val correctTool: Int,
    /** The first answer passed parsing and validation without a repair. */
    val argsValid: Int,
    /** The final call carried the values the prompt asked for. */
    val argsMatch: Int,
    val repaired: Int,
    val p50Ms: Double?,
    val p95Ms: Double?,
    val maxPssKb: Long?,
) {
    fun pct(count: Int): Double = if (n == 0) 0.0 else 100.0 * count / n
}

/**
 * Reads the LLM columns of an S4 run file (kind `llm_bench` rows, one per benchmark prompt) and
 * summarizes them per expected action, separately for the two pass-2 modes: rows with
 * `source = slots` (slot extraction, the default since Decision 2026-10-30) go to [slotGroups],
 * all others (the experimental Gemma tool call) to [groups]. For slot rows, `json_ok` means the
 * slots were complete. Info only: the S4 shortcut gate is [Summary]'s, unchanged.
 */
class LlmSummary private constructor(
    val groups: List<LlmGroupStats>,
    val peakPssKb: Long?,
    val slotGroups: List<LlmGroupStats>,
    val slotPeakPssKb: Long?,
) {

    fun describe(): String = buildString {
        if (groups.isEmpty() && slotGroups.isEmpty()) {
            appendLine("LLM path: no LLM benchmark rows yet. Load the models, then press LLM benchmark.")
        }
        if (slotGroups.isNotEmpty()) {
            appendLine("Real path (Decision Maker → slot extraction → parser → Permission Engine), dry run:")
            appendLine("expected       n  DM acc  tool ok  slot ok   args =   p50 ms   p95 ms  max PSS")
            for (g in slotGroups) {
                val action = g.expected != ToolRouting.NONE
                appendLine(
                    String.format(
                        Locale.US, "%-12s %3d %7s %8s %8s %8s %8s %8s %8s",
                        g.expected, g.n, p(g, g.routedRight), p(g, g.correctTool),
                        if (action) p(g, g.jsonOk) else "n/a", if (action) p(g, g.argsMatch) else "n/a",
                        f(g.p50Ms), f(g.p95Ms), mb(g.maxPssKb),
                    ),
                )
            }
            appendLine("Peak PSS (slot extraction rows): ${mb(slotPeakPssKb)}")
            appendLine("slot ok = every required slot found, no ambiguity (else the user is asked).")
        }
        if (groups.isNotEmpty()) {
            appendLine("LLM path (Decision Maker → Gemma tool call → parser → Permission Engine), dry run:")
            appendLine("expected       n  DM acc  tool ok  json ok  args ok  args =  rep   p50 ms   p95 ms  max PSS")
            for (g in groups) {
                appendLine(
                    String.format(
                        Locale.US, "%-12s %3d %7s %8s %8s %8s %7s %4d %8s %8s %8s",
                        g.expected, g.n, p(g, g.routedRight), p(g, g.correctTool),
                        if (g.llmRan == 0) "n/a" else String.format(Locale.US, "%d/%d", g.jsonOk, g.llmRan),
                        if (g.expected == ToolRouting.NONE) "n/a" else p(g, g.argsValid),
                        if (g.expected == ToolRouting.NONE) "n/a" else p(g, g.argsMatch),
                        g.repaired, f(g.p50Ms), f(g.p95Ms), mb(g.maxPssKb),
                    ),
                )
            }
            appendLine("Peak PSS with both models loaded: ${mb(peakPssKb)}")
        }
        appendLine("DM acc = router choice (after the confidence threshold) right; tool ok = a valid call of the")
        appendLine("expected tool reached the Permission Engine (none: no call reached it); args ok = first answer")
        appendLine("valid without repair; args = = values as asked; latency = submit → Permission Engine decision.")
        append("Info only: this does not change the S4 shortcut gate.")
    }

    companion object {
        const val COLUMNS =
            "expected,dm_label,dm_conf,dm_ms,route,llm_ttft_ms,llm_ms,tool_name,args_json,source," +
                "json_ok,args_ok,repaired,call_tool,args_match,outcome,decision,e2e_ms,pss_kb"

        /** The run file header: the gate columns first (unchanged), then the LLM columns. */
        const val FULL_HEADER = Summary.HEADER + "," + COLUMNS

        const val BENCH_KIND = "llm_bench"
        const val LIVE_KIND = "llm_live"

        /** `source` value of every row written in slot-extraction mode (also no-action rows). */
        const val SLOTS_SOURCE = "slots"

        fun from(csv: String): LlmSummary {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val header = lines.firstOrNull()?.split(",").orEmpty()
            val idx = header.withIndex().associate { it.value.trim() to it.index }
            fun cell(cells: List<String>, col: String) = idx[col]?.let { cells.getOrNull(it) }?.trim().orEmpty()
            val rows = lines.drop(1).map { it.split(",") }.filter { cell(it, "kind") == BENCH_KIND }
            val (slotRows, llmRows) = rows.partition { cell(it, "source") == SLOTS_SOURCE }

            fun groupsOf(rows: List<List<String>>): List<LlmGroupStats> {
                val order = ToolName.entries.map { it.wire } + ToolRouting.NONE
                return rows.groupBy { cell(it, "expected") }
                    .filterKeys { it in order }
                    .toSortedMap(compareBy { order.indexOf(it) })
                    .map { (expected, rs) ->
                        fun count(pred: (List<String>) -> Boolean) = rs.count(pred)
                        fun yes(r: List<String>, col: String) = cell(r, col) == "true"
                        val latencies = rs.mapNotNull { cell(it, "e2e_ms").toDoubleOrNull() }
                        LlmGroupStats(
                            expected = expected,
                            n = rs.size,
                            routedRight = count { cell(it, "route") == expected },
                            llmRan = count { cell(it, "route").let { r -> r.isNotEmpty() && r != ToolRouting.NONE } },
                            jsonOk = count { yes(it, "json_ok") },
                            correctTool = if (expected == ToolRouting.NONE) {
                                count { cell(it, "outcome") != "call" }
                            } else {
                                count { cell(it, "outcome") == "call" && cell(it, "call_tool") == expected }
                            },
                            argsValid = count { yes(it, "args_ok") },
                            argsMatch = count { yes(it, "args_match") },
                            repaired = count { yes(it, "repaired") },
                            p50Ms = Summary.percentile(latencies, 0.50),
                            p95Ms = Summary.percentile(latencies, 0.95),
                            maxPssKb = rs.mapNotNull { cell(it, "pss_kb").toLongOrNull() }.maxOrNull(),
                        )
                    }
            }
            val llm = groupsOf(llmRows)
            val slots = groupsOf(slotRows)
            return LlmSummary(llm, llm.mapNotNull { it.maxPssKb }.maxOrNull(), slots, slots.mapNotNull { it.maxPssKb }.maxOrNull())
        }

        private fun p(g: LlmGroupStats, count: Int) = String.format(Locale.US, "%.0f%%", g.pct(count))

        private fun f(v: Double?) = v?.let { String.format(Locale.US, "%.0f", it) } ?: "n/a"

        private fun mb(kb: Long?) = kb?.let { String.format(Locale.US, "%.0f MB", it / 1024.0) } ?: "n/a"
    }
}
