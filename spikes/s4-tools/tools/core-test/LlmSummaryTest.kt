package io.github.saadahmed1122.bibi.spike.s4.coretest

import io.github.saadahmed1122.bibi.spike.s4.Gate
import io.github.saadahmed1122.bibi.spike.s4.LlmSummary
import io.github.saadahmed1122.bibi.spike.s4.Summary

// Per-action metrics of the LLM benchmark (kind llm_bench rows), next to the unchanged S4 gate.

private data class LlmRow(
    val expected: String,
    val route: String = expected,
    val callTool: String = if (expected == "none") "" else expected,
    val outcome: String = if (expected == "none") "no_action" else "call",
    val jsonOk: Boolean = expected != "none",
    val argsOk: Boolean = expected != "none",
    val argsMatch: Boolean = expected != "none",
    val repaired: Boolean = false,
    val e2eMs: Double = 1000.0,
    val pssKb: Long = 1_500_000,
    val kind: String = "llm_bench",
    val source: String = "structured",
)

private fun csvRow(r: LlmRow, i: Int): String {
    val cols = mapOf(
        "time" to "2026-10-30 10:00:00", "epoch_ms" to (1_760_000_000_000L + i).toString(), "run_id" to "1",
        "kind" to r.kind, "action" to r.callTool.ifEmpty { "none" }, "path" to "llm", "mode" to "dry_run", "ok" to (r.outcome == "call").toString(),
        "total_ms" to "", "parse_ms" to "", "policy_ms" to "", "build_ms" to "", "dispatch_ms" to "", "detail" to "x",
        "expected" to r.expected, "dm_label" to r.route, "dm_conf" to "0.900", "dm_ms" to "40.000", "route" to r.route,
        "llm_ttft_ms" to "300.000", "llm_ms" to "900.000", "tool_name" to r.callTool, "args_json" to "{\"tool\":\"x\";\"args\":{}}",
        "source" to r.source, "json_ok" to r.jsonOk.toString(), "args_ok" to r.argsOk.toString(),
        "repaired" to r.repaired.toString(), "call_tool" to r.callTool, "args_match" to r.argsMatch.toString(),
        "outcome" to r.outcome, "decision" to if (r.outcome == "call") "allow" else "", "e2e_ms" to "%.3f".format(java.util.Locale.US, r.e2eMs),
        "pss_kb" to r.pssKb.toString(),
    )
    return LlmSummary.FULL_HEADER.split(",").joinToString(",") { cols.getValue(it) }
}

private fun llmCsv(rows: List<LlmRow>) =
    (listOf(LlmSummary.FULL_HEADER) + rows.mapIndexed { i, r -> csvRow(r, i) }).joinToString("\n") + "\n"

fun llmSummaryTests() {
    checkEq("full header starts with the gate header", true, LlmSummary.FULL_HEADER.startsWith(Summary.HEADER + ","))
    checkEq("full header has no duplicate columns", LlmSummary.FULL_HEADER.split(",").size, LlmSummary.FULL_HEADER.split(",").toSet().size)

    val rows = buildList {
        // set_alarm: 20 prompts. 18 routed right; of those 16 correct calls (2 repaired), 1 bad args, 1 no call.
        // 2 routed to start_timer (wrong tool called). e2e 1..20 × 100 ms.
        repeat(16) { k -> add(LlmRow("set_alarm", repaired = k < 2, argsOk = k >= 2, e2eMs = (k + 1) * 100.0, argsMatch = k != 5)) }
        add(LlmRow("set_alarm", outcome = "ask_user", callTool = "", argsOk = false, argsMatch = false, e2eMs = 1700.0))
        add(LlmRow("set_alarm", outcome = "no_tool_call", callTool = "", jsonOk = false, argsOk = false, argsMatch = false, e2eMs = 1800.0))
        add(LlmRow("set_alarm", route = "start_timer", callTool = "start_timer", argsMatch = false, e2eMs = 1900.0))
        add(LlmRow("set_alarm", route = "start_timer", callTool = "start_timer", argsMatch = false, e2eMs = 2000.0, pssKb = 2_100_000))
        // none: 10 prompts. 8 routed none; 1 routed to flashlight but the model answered in text; 1 called the flashlight.
        repeat(8) { add(LlmRow("none", e2eMs = 50.0)) }
        add(LlmRow("none", route = "flashlight", callTool = "", outcome = "no_tool_call", jsonOk = false, e2eMs = 800.0))
        add(LlmRow("none", route = "flashlight", callTool = "flashlight", outcome = "call", jsonOk = true, argsOk = true, e2eMs = 900.0))
        // Rows of other kinds are ignored.
        add(LlmRow("set_alarm", kind = "llm_live", e2eMs = 99_999.0, pssKb = 9_999_999))
    }
    val s = LlmSummary.from(llmCsv(rows))
    val alarm = s.groups.single { it.expected == "set_alarm" }
    checkEq("alarm n", 20, alarm.n)
    checkEq("alarm routed right (DM accuracy)", 18, alarm.routedRight)
    checkEq("alarm LLM ran", 20, alarm.llmRan)
    checkEq("alarm json ok", 19, alarm.jsonOk)
    checkEq("alarm correct tool (call with the expected tool)", 16, alarm.correctTool)
    checkEq("alarm valid args on the first answer", 16, alarm.argsValid)
    checkEq("alarm args match the expected values", 15, alarm.argsMatch)
    checkEq("alarm repaired", 2, alarm.repaired)
    checkEq("alarm p50 e2e (nearest rank)", 1000.0, alarm.p50Ms)
    checkEq("alarm p95 e2e (nearest rank)", 1900.0, alarm.p95Ms)
    checkEq("alarm max PSS", 2_100_000L, alarm.maxPssKb)
    checkEq("alarm DM accuracy %", 90.0, alarm.pct(alarm.routedRight))
    checkEq("alarm correct-tool %", 80.0, alarm.pct(alarm.correctTool))

    val none = s.groups.single { it.expected == "none" }
    checkEq("none n", 10, none.n)
    checkEq("none routed right", 8, none.routedRight)
    checkEq("none LLM ran", 2, none.llmRan)
    checkEq("none correct = no call reached the engine", 9, none.correctTool)
    checkEq("none p95", 900.0, none.p95Ms)

    checkEq("peak PSS over all benchmark rows (live rows ignored)", 2_100_000L, s.peakPssKb)
    checkEq("groups: only actions that have rows", listOf("set_alarm", "none"), s.groups.map { it.expected })

    val text = s.describe()
    check("describe has the set_alarm row", text.contains("set_alarm")) { text }
    check("describe shows DM accuracy 90%", text.contains("90%")) { text }
    check("describe shows peak PSS in MB", text.contains("2051 MB")) { text }
    check("describe says the gate is unchanged", text.contains("does not change the S4 shortcut gate")) { text }

    checkEq("no slot rows → no slot groups", 0, s.slotGroups.size)

    // Pass 2 = slot extraction (Decision 2026-10-30): source=slots rows get their own table.
    val slotRows = buildList {
        // dial: 20 prompts, all routed right; 17 slots complete and valid, 16 match; 3 asked the user.
        repeat(17) { k -> add(LlmRow("dial", source = "slots", argsMatch = k != 0, e2eMs = 500.0 + k, pssKb = 300_000)) }
        repeat(3) { add(LlmRow("dial", source = "slots", outcome = "ask_user", callTool = "", jsonOk = false, argsOk = false, argsMatch = false, e2eMs = 520.0, pssKb = 310_000)) }
        repeat(10) { add(LlmRow("none", source = "slots", e2eMs = 480.0, pssKb = 305_000)) }
    }
    val both = LlmSummary.from(llmCsv(rows + slotRows))
    checkEq("slot rows do not change the LLM groups", s.groups, both.groups)
    checkEq("LLM peak PSS ignores slot rows", 2_100_000L, both.peakPssKb)
    val dialSlots = both.slotGroups.single { it.expected == "dial" }
    checkEq("slots dial n", 20, dialSlots.n)
    checkEq("slots dial DM acc", 20, dialSlots.routedRight)
    checkEq("slots dial slot ok (complete)", 17, dialSlots.jsonOk)
    checkEq("slots dial args match", 16, dialSlots.argsMatch)
    checkEq("slots dial correct tool", 17, dialSlots.correctTool)
    checkEq("slots dial p95", 520.0, dialSlots.p95Ms)
    checkEq("slots peak PSS", 310_000L, both.slotPeakPssKb)
    checkEq("slot groups order", listOf("dial", "none"), both.slotGroups.map { it.expected })
    val bothText = both.describe()
    check("describe has the slot-extraction table", bothText.contains("slot extraction")) { bothText }
    check("describe shows slot ok 85%", bothText.contains("85%")) { bothText }
    check("describe shows the slot peak PSS", bothText.contains("303 MB")) { bothText }
    check("describe keeps the LLM tool-call table", bothText.contains("Gemma tool call")) { bothText }
    val slotsOnly = LlmSummary.from(llmCsv(slotRows)).describe()
    check("slots-only describe has no LLM tool-call table", !slotsOnly.contains("Gemma tool call")) { slotsOnly }

    checkEq("empty LLM summary", 0, LlmSummary.from("").groups.size)
    check("empty describe says how to run it", LlmSummary.from("").describe().contains("LLM benchmark")) { LlmSummary.from("").describe() }

    // The shortcut gate still reads files with the extra LLM columns, and LLM rows never count.
    val gateRows = listOf("open_app", "set_alarm", "start_timer", "flashlight", "dial").flatMap { a ->
        (1..20).map { k ->
            val cols = mapOf(
                "time" to "t", "epoch_ms" to "$k", "run_id" to "1", "kind" to "bench", "action" to a, "path" to "shortcut",
                "mode" to if (a == "flashlight") "torch" else "dry_run", "ok" to "true", "total_ms" to "12.000",
                "parse_ms" to "", "policy_ms" to "", "build_ms" to "", "dispatch_ms" to "", "detail" to "x",
            )
            LlmSummary.FULL_HEADER.split(",").joinToString(",") { cols[it] ?: "" }
        }
    }
    val mixed = (listOf(LlmSummary.FULL_HEADER) + gateRows + rows.mapIndexed { i, r -> csvRow(r, i) }).joinToString("\n")
    checkEq("gate PASS with LLM columns and rows present", Gate.PASS, Summary.from(mixed).gate)
    check("LLM rows are not gate groups", Summary.from(mixed).groups.none { it.path == "llm" })
}
