package io.github.saadahmed1122.bibi.spike.s2

import java.io.File
import kotlin.math.abs

// JVM-only tests for Summary, PromptSet and Metrics (no Android). Run with tools/summary-test/run.sh.

/** Copied from BenchLog.HEADER; update both together. */
const val HEADER =
    "time,epoch_ms,run_id,kind,detail,model,mode," +
        "prompt_idx,prompt_id,prompt_chars,prefill_tokens,decode_tokens,tokens_source,chunks," +
        "load_ms,ttft_ms,decode_tps,total_ms,eng_ttft_ms,eng_decode_tps," +
        "pss_kb,rss_kb,rss_peak_kb,avail_mem_mb,low_memory," +
        "thermal,battery_pct,current_ua,charging,batt_temp_dc,screen_on,airplane,brightness,power_save,ctx_tokens"
val COLS = HEADER.split(",")

const val EXIT_HEADER = "time,epoch_ms,reason,status,detail,process,importance,pss_kb,rss_kb"
const val T0 = 1_760_000_000_000L
const val PKG = "io.github.saadahmed1122.bibi.spike.s2"

fun row(t: Long, kind: String, mode: String = "soak", vararg cells: Pair<String, String>): String {
    val v = mutableMapOf(
        "time" to "2026-10-29 10:00:00", "epoch_ms" to (T0 + t).toString(), "run_id" to T0.toString(),
        "kind" to kind, "model" to "gemma3-1b-it-int4", "mode" to mode,
        "pss_kb" to "700000", "rss_kb" to "750000", "rss_peak_kb" to "800000", "avail_mem_mb" to "1500",
        "low_memory" to "false", "thermal" to "0", "battery_pct" to "80", "current_ua" to "-900000",
        "charging" to "false", "batt_temp_dc" to "320", "screen_on" to "true", "airplane" to "true",
        "brightness" to "128", "power_save" to "false",
    )
    v += cells
    return COLS.joinToString(",") { v[it] ?: "" }
}

fun startRow(mode: String = "soak", planned: Int = 50) =
    row(0, "start", mode, "detail" to "file=g.litertlm bytes=584417280 device=vivo V2310 android=15 api=35 prompts=$planned max_new=128")

/**
 * A run: start, load, then [n] prompts 10 s apart with TTFT [ttft](i) and speed [tps](i), then
 * [end] (null = no final row). [tweak] may replace a prompt row by index.
 */
fun run(
    n: Int = 50,
    planned: Int = 50,
    mode: String = "soak",
    ttft: (Int) -> Double = { 1000.0 + it * 10 },
    tps: (Int) -> Double = { 7.0 + (it % 5) * 0.1 },
    end: String? = "stop",
    tweak: (Int, Long) -> String? = { _, _ -> null },
): String {
    val rows = mutableListOf(HEADER, startRow(mode, planned), row(5_000, "load", mode, "load_ms" to "4200"))
    for (i in 1..n) {
        val t = 5_000L + i * 10_000L
        rows += tweak(i, t) ?: row(
            t, "prompt", mode, "prompt_idx" to "$i", "prompt_id" to "p%02d".format(i),
            "prefill_tokens" to "60", "decode_tokens" to "100", "ttft_ms" to "${ttft(i)}", "decode_tps" to "${tps(i)}",
        )
    }
    if (end != null) rows += row(5_000L + (n + 1) * 10_000L, end, mode)
    return rows.joinToString("\n") + "\n"
}

/**
 * A chat-mode run: [run] with mode "chat", a `preface` row after load, and [resets] `conv_reset`
 * rows (each re-prefilling the preface) spread between the prompts.
 */
fun chatRun(
    n: Int = 50,
    resets: Int = 1,
    preface: Boolean = true,
    prefaceCtx: String = "310",
    resetCtx: String = "310",
    ttft: (Int) -> Double = { 400.0 + it * 2 },
    tps: (Int) -> Double = { 6.5 },
    end: String? = "stop",
): String {
    val lines = run(n = n, mode = "chat", ttft = ttft, tps = tps, end = end).trimEnd().lines().toMutableList()
    if (preface) {
        lines.add(3, row(6_000, "preface", "chat", "detail" to "system=system_bibi.txt", "total_ms" to "15000.0", "prefill_tokens" to prefaceCtx, "ctx_tokens" to prefaceCtx))
    }
    repeat(resets) { r ->
        val at = 4 + (r + 1) * 20 // after prompt 20, 40, ...
        lines.add(at.coerceAtMost(lines.size), row(6_000L + (r + 1) * 200_000L, "conv_reset", "chat", "detail" to "used=3950", "total_ms" to "${16000.0 + r * 1000}", "prefill_tokens" to resetCtx, "ctx_tokens" to resetCtx))
    }
    return lines.joinToString("\n") + "\n"
}

fun exitRow(t: Long, reason: String, status: String = "0", process: String = "$PKG:inference") =
    listOf("x", (T0 + t).toString(), reason, status, "d", process, "100", "0", "0").joinToString(",")

fun exits(vararg r: String) = (listOf(EXIT_HEADER) + r).joinToString("\n") + "\n"

fun check(name: String, ok: Boolean, detail: String) {
    if (!ok) throw AssertionError("FAIL $name: $detail")
    println("PASS $name")
}

fun near(a: Double?, b: Double, eps: Double = 1e-6) = a != null && abs(a - b) < eps

fun main(args: Array<String>) {
    // --- pass case
    val a = Summary.from(run())
    check("pass: valid", a.valid, a.validityLine())
    check("pass: PASS", a.pass == true, a.gateLine())
    check("pass: 50 of 50", a.completed == 50 && a.planned == 50, "${a.completed}/${a.planned}")
    // TTFT 1010..1500 step 10: p50 = rank 25 -> 1250, p95 = rank 48 -> 1480.
    check("pass: p50 TTFT 1250", near(a.ttftP50Ms, 1250.0), "${a.ttftP50Ms}")
    check("pass: p95 TTFT 1480", near(a.ttftP95Ms, 1480.0), "${a.ttftP95Ms}")
    // tps 7.0..7.4 ten times each: median 7.2, p10 rank 5 -> 7.0.
    check("pass: median tps 7.2", near(a.tpsMedian, 7.2), "${a.tpsMedian}")
    check("pass: p10 tps 7.0", near(a.tpsP10, 7.0), "${a.tpsP10}")
    check("pass: device parsed", a.device == "vivo V2310", a.device)
    check("pass: load 4200", near(a.loadMs, 4200.0), "${a.loadMs}")
    check("pass: mean current 900 mA", near(a.meanCurrentMa, 900.0), "${a.meanCurrentMa}")

    // --- slow decode
    val b = Summary.from(run(tps = { 3.5 }))
    check("slow tps: valid but FAIL", b.valid && b.pass == false, b.gateLine())
    check("slow tps: names the speed", b.gateLine().contains("median 3.50 tok/s < 5"), b.gateLine())
    check("slow tps: describe has PRD advice", b.describe().contains("PRD 0.5"), b.describe())

    // --- slow first token: 3 slow prompts of 50 put p95 (rank 48) at 4000 ms; 2 slow ones do not
    val c = Summary.from(run(ttft = { if (it > 47) 4000.0 else 1200.0 }))
    check("slow ttft: FAIL on p95", c.pass == false && c.gateLine().contains("p95 TTFT 4000 ms"), c.gateLine())
    val c2 = Summary.from(run(ttft = { if (it > 48) 4000.0 else 1200.0 }))
    check("2 slow of 50: p95 still passes", c2.pass == true, c2.gateLine() + " p95=${c2.ttftP95Ms}")

    // --- OOM kill at prompt 20: UI wrote inference_died, Android recorded LOW_MEMORY for :inference
    val oomRun = run(n = 20, end = "inference_died")
    val d = Summary.from(oomRun, exits(exitRow(5_000 + 21 * 10_000L - 500, "LOW_MEMORY")))
    check("oom: counted", d.oomKills == 1, "oom=${d.oomKills}")
    check("oom: valid (incomplete is expected)", d.valid, d.validityLine())
    check("oom: FAIL names OOM", d.pass == false && d.gateLine().contains("1 OOM kill"), d.gateLine())

    // SIGKILL counts as OOM (devices without LMK reporting); in the window only, :inference only.
    val e = Summary.from(oomRun, exits(exitRow(205_000, "SIGNALED", status = "9")))
    check("sigkill: counted as OOM", e.oomKills == 1 && e.pass == false, e.gateLine())
    val f = Summary.from(
        run(),
        exits(exitRow(-3_600_000, "LOW_MEMORY"), exitRow(100_000, "LOW_MEMORY", process = PKG)),
    )
    check("oom: old kill and UI-process kill ignored", f.oomKills == 0 && f.pass == true, f.gateLine())

    // Java OutOfMemoryError in an error row also counts.
    val g = Summary.from(run(n = 10, end = "aborted") { i, t -> if (i == 10) row(t, "error", "soak", "detail" to "OutOfMemoryError: x") else null })
    check("java OOM error: counted", g.oomKills == 1 && g.pass == false, g.gateLine() + " " + g.validityLine())

    // --- native crash: not an OOM, run invalid
    val h = Summary.from(run(n = 20, end = "inference_died"), exits(exitRow(205_000, "CRASH_NATIVE", status = "6")))
    check("crash: invalid, no verdict", !h.valid && h.pass == null && h.validityLine().contains("CRASH_NATIVE"), h.validityLine())
    val h2 = Summary.from(run(n = 20, end = "inference_died"))
    check("died, no exit row yet: invalid", !h2.valid && h2.validityLine().contains("reason not in exit_reasons"), h2.validityLine())

    // --- other invalid runs
    val i = Summary.from(run(n = 3, planned = 3, mode = "smoke"))
    check("smoke: invalid", !i.valid && i.validityLine().contains("smoke run"), i.validityLine())
    val j = Summary.from(run(n = 30, end = "cancelled"))
    check("cancelled: invalid", !j.valid && j.validityLine().contains("only 30 of 50") && j.validityLine().contains("no clean stop (cancelled)"), j.validityLine())
    val k = Summary.from(run { n, t -> if (n == 7) row(t, "prompt", "soak", "charging" to "true", "ttft_ms" to "1000", "decode_tps" to "7") else null })
    check("charging: invalid", !k.valid && k.validityLine().contains("charging"), k.validityLine())
    val l = Summary.from(run { n, t -> if (n == 7) row(t, "prompt", "soak", "screen_on" to "false", "ttft_ms" to "1000", "decode_tps" to "7") else null })
    check("screen off: invalid", !l.valid && l.validityLine().contains("screen off"), l.validityLine())
    val m = Summary.from(run { n, t -> if (n == 7) row(t, "prompt", "soak", "airplane" to "false", "ttft_ms" to "1000", "decode_tps" to "7") else null })
    check("airplane off: invalid", !m.valid && m.validityLine().contains("airplane"), m.validityLine())
    val lowBattery = run().replace(",80,-900000,", ",55,-900000,")
    val n = Summary.from(lowBattery)
    check("battery < 60 %: invalid", !n.valid && n.validityLine().contains("battery 55 %"), n.validityLine())
    val o = Summary.from(run { idx, t -> if (idx == 9) row(t, "error", "soak", "detail" to "TimeoutException: no onDone") else null })
    check("error row: invalid", !o.valid && o.validityLine().contains("1 error row"), o.validityLine())
    check("empty run: no crash", Summary.from("").describe() == "No run yet.", Summary.from("").describe())

    // --- percentile / median helpers
    check("percentile nearest rank", Summary.percentile(listOf(5.0, 1.0, 3.0, 2.0, 4.0), 95.0) == 5.0, "")
    check("median even", Summary.median(listOf(4.0, 1.0, 3.0, 2.0)) == 2.5, "")

    // --- PRD 0.5 outcome table
    check("outcome: floor passes", Summary.outcome(true, null) == Summary.Outcome.PASS, "")
    check("outcome: floor fails, better passes -> 0.5 rule", Summary.outcome(false, true) == Summary.Outcome.RULE_0_5, "")
    check("outcome: both fail -> fallback", Summary.outcome(false, false) == Summary.Outcome.FALLBACK, "")
    check("outcome: floor fails, no better device yet", Summary.outcome(false, null) == Summary.Outcome.NEED_BETTER_DEVICE, "")

    // --- Metrics
    check("ttft ms", Metrics.ttftMs(1_000_000_000L, 2_500_000_000L) == 1500.0, "")
    check("decode tps: 101 tokens over 20 s = 5.0", near(Metrics.decodeTokensPerSecond(101, 0L, 20_000_000_000L), 5.0), "")
    check("decode tps: 1 token -> null", Metrics.decodeTokensPerSecond(1, 0L, 1L) == null, "")
    val status = "Name:\tx\nVmHWM:\t  812345 kB\nVmRSS:\t  700000 kB\n"
    check("proc status VmHWM", Metrics.procStatusKb(status, "VmHWM") == 812345L, "")
    check("proc status missing", Metrics.procStatusKb(status, "VmSwap") == null, "")

    // --- existing per-prompt soak output is unchanged by chat mode
    check("soak: describe unchanged", a.describe() == SOAK_DESCRIBE, a.describe())
    check("soak: no chat lines", !a.describe().contains("Preface") && !a.describe().contains("chat mode"), a.describe())

    // --- chat mode: same gate checks, own label, preface and resets reported
    val ch = Summary.from(chatRun(resets = 2))
    check("chat: valid", ch.valid, ch.validityLine())
    check("chat: PASS labelled chat mode", ch.pass == true && ch.gateLine() == "GATE S2 (gemma3-1b-it-int4, chat mode): PASS", ch.gateLine())
    check("chat: 50 of 50, preface/reset rows not counted as prompts", ch.completed == 50, "${ch.completed}")
    check("chat: preface 15000 ms, 310 tokens", near(ch.prefaceMs, 15000.0) && ch.prefaceTokens == 310, "${ch.prefaceMs} ${ch.prefaceTokens}")
    check("chat: 2 resets, max re-prefill 17000 ms", ch.resets == 2 && near(ch.maxResetMs, 17000.0), "${ch.resets} ${ch.maxResetMs}")
    check("chat: system prompt named", ch.systemPrompt == "system_bibi.txt", ch.systemPrompt)
    val chText = ch.describe()
    check("chat: describe has preface and resets", chText.contains("Preface:") && chText.contains("15000 ms") && chText.contains("Conv resets:     2"), chText)
    check("chat: describe header says chat", chText.contains("(chat)"), chText)
    val chSlow = Summary.from(chatRun(ttft = { if (it > 47) 3500.0 else 500.0 }))
    check("chat: FAIL on p95, chat label", chSlow.pass == false && chSlow.gateLine().startsWith("GATE S2 (gemma3-1b-it-int4, chat mode): FAIL — p95 TTFT 3500 ms"), chSlow.gateLine())
    val chSpeed = Summary.from(chatRun(tps = { 4.0 }))
    check("chat: FAIL on speed", chSpeed.pass == false && chSpeed.gateLine().contains("median 4.00 tok/s < 5"), chSpeed.gateLine())
    val chOom = Summary.from(chatRun(n = 20, resets = 0, end = "inference_died"), exits(exitRow(205_000, "LOW_MEMORY")))
    check("chat: OOM counted, valid, FAIL", chOom.valid && chOom.oomKills == 1 && chOom.pass == false && chOom.gateLine().contains("chat mode"), chOom.gateLine())
    val chCancel = Summary.from(chatRun(n = 30, end = "cancelled"))
    check("chat: cancelled invalid, no verdict, chat label", !chCancel.valid && chCancel.gateLine() == "GATE S2 (gemma3-1b-it-int4, chat mode): no verdict, run not valid", chCancel.gateLine())
    val chNoPreface = Summary.from(chatRun(preface = false))
    check("chat: no preface row is invalid", !chNoPreface.valid && chNoPreface.validityLine().contains("no preface row"), chNoPreface.validityLine())
    // Device run 2026-10-30 01:41: the preface row showed ctx_tokens=0, so the system prompt was
    // prefilled inside prompt 1's timed TTFT. Such a run must not get a chat-mode verdict.
    val chNotPrefilled = Summary.from(chatRun(prefaceCtx = "0"))
    check("chat: preface ctx 0 is invalid", !chNotPrefilled.valid && chNotPrefilled.pass == null && chNotPrefilled.validityLine().contains("preface not prefilled"), chNotPrefilled.validityLine())
    val chCtxUnknown = Summary.from(chatRun(prefaceCtx = ""))
    check("chat: preface ctx unknown is invalid", !chCtxUnknown.valid && chCtxUnknown.validityLine().contains("preface not prefilled"), chCtxUnknown.validityLine())
    val chResetNotPrefilled = Summary.from(chatRun(resets = 1, resetCtx = "0"))
    check("chat: conv_reset ctx 0 is invalid", !chResetNotPrefilled.valid && chResetNotPrefilled.validityLine().contains("preface not prefilled"), chResetNotPrefilled.validityLine())
    val chCharging = Summary.from(chatRun().replace(",false,320,true,true,", ",true,320,true,true,"))
    check("chat: charging invalid", !chCharging.valid && chCharging.validityLine().contains("charging"), chCharging.validityLine())
    check("soak: no preface needed", a.valid && a.resets == 0 && a.prefaceMs == null, a.validityLine())

    // --- chat-mode context budget (reset before a turn could overflow the KV cache)
    // 60 chars -> 20 + 16 template = 36 tokens, + 128 new = 164.
    check("budget: turn estimate", Metrics.turnTokenEstimate(60) == 36, "${Metrics.turnTokenEstimate(60)}")
    check("budget: fits exactly", !Metrics.needsContextReset(usedTokens = 3932, turnsInConversation = 5, promptChars = 60, maxNewTokens = 128, contextTokens = 4096), "")
    check("budget: one over -> reset", Metrics.needsContextReset(usedTokens = 3933, turnsInConversation = 5, promptChars = 60, maxNewTokens = 128, contextTokens = 4096), "")
    check("budget: fresh conversation never resets", !Metrics.needsContextReset(usedTokens = 4000, turnsInConversation = 0, promptChars = 60, maxNewTokens = 128, contextTokens = 4096), "")
    check("budget: long prompt resets earlier", Metrics.needsContextReset(usedTokens = 3800, turnsInConversation = 1, promptChars = 600, maxNewTokens = 128, contextTokens = 4096), "")

    // --- the shipped prompt set
    val set = PromptSet.parse(File(args[0]).readText())
    check("prompts: 50", set.prompts.size == 50, "${set.prompts.size}")
    check("prompts: system line", set.system.startsWith("You are Bibi"), set.system)
    // ≤ 200 tokens: English is about 1.3–1.5 tokens per word [VERIFY per tokenizer]; 110 words
    // leaves room for the chat template. The app logs real prefill_tokens per prompt.
    val longest = set.prompts.maxBy { it.text.split(Regex("\\s+")).size }
    val words = longest.text.split(Regex("\\s+")).size + set.system.split(Regex("\\s+")).size
    check("prompts: longest + system ≤ 110 words", words <= 110, "${longest.id}: $words words")
    var threw = false
    try { PromptSet.parse("system\ta\np1\tx\np1\ty") } catch (e: IllegalArgumentException) { threw = true }
    check("prompts: duplicate id rejected", threw, "")

    // The chat-mode system prompt: persona + remembered facts, ~250-350 tokens. At about
    // 1.3-1.5 tokens per English word [VERIFY per tokenizer] that is roughly 170-260 words; the app
    // logs the real count as the preface row's prefill_tokens.
    val bibi = File(args[1]).readText().trim()
    val bibiWords = bibi.split(Regex("\\s+")).size
    check("chat system prompt: 170-260 words", bibiWords in 170..260, "$bibiWords words")
    check("chat system prompt: is Bibi", bibi.startsWith("You are Bibi"), bibi.take(40))

    println()
    println(a.describe())
    println()
    println(d.describe())
    println()
    println(ch.describe())
    println("ALL PASS")
}

/** Summary.describe() of the default passing soak, captured before chat mode was added. */
val SOAK_DESCRIBE = """
Model:           gemma3-1b-it-int4 (soak)
Device:          vivo V2310
Prompts:         50 of 50 finished
Load:            4200 ms
TTFT p50 / p95:  1250 / 1480 ms   (gate: p95 ≤ 3000)
Decode median:   7.20 tok/s   (gate: ≥ 5)
Decode p10:      7.00 tok/s   (slowest 10 %; informational)
OOM kills:       0   (gate: 0)
Max PSS:         684 MB (:inference, between prompts)
Peak RSS:        781 MB (VmHWM of :inference)
Min avail RAM:   1500 MB (system)
Max thermal:     0 (0 none … 3 severe)
Battery:         80% → 80%, mean 900 mA
Max tokens:      prefill 60, decode 100 (cap 128)
VALID for gate: yes
GATE S2 (gemma3-1b-it-int4): PASS
""".trimStart()
