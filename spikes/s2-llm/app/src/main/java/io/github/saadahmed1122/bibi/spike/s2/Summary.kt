package io.github.saadahmed1122.bibi.spike.s2

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Gate S2 results for one run file (written by [InferenceService]) plus exit_reasons.csv.
 * Kotlin stdlib only, so it is tested on a plain JVM (tools/summary-test). Columns are read by name.
 *
 * Gate (PRD 0.7, interpretation in SPEC.md §2): p95 time to first token ≤ 3 s, median decode
 * speed ≥ 5 tokens/s, zero OOM kills of `:inference` in the 50-prompt soak.
 *
 * Two run modes are judged with the same checks, each on its own: `soak` (a fresh conversation
 * per prompt) and `chat` (one conversation with the system prompt prefilled once, untimed, then 50
 * turns; SPEC.md §2.1). Chat verdicts are labelled "chat mode".
 */
class Summary private constructor(
    val model: String,
    val mode: String,
    val device: String,
    val planned: Int?,
    val completed: Int,
    val loadMs: Double?,
    val ttftP50Ms: Double?,
    val ttftP95Ms: Double?,
    val tpsMedian: Double?,
    val tpsP10: Double?,
    val maxPssMb: Double?,
    val maxRssPeakMb: Double?,
    val minAvailMemMb: Double?,
    val maxThermal: Int?,
    val startPct: Int?,
    val endPct: Int?,
    val meanCurrentMa: Double?,
    val maxPrefillTokens: Int?,
    val maxDecodeTokens: Int?,
    val oomKills: Int,
    val invalidReasons: List<String>,
    /** Chat mode: the first preface prefill (app clock) and its token count. */
    val prefaceMs: Double?,
    val prefaceTokens: Int?,
    /** Chat mode: conversations reset before the KV cache would overflow, and the slowest re-prefill. */
    val resets: Int,
    val maxResetMs: Double?,
    /** Chat mode: the system prompt asset used for the preface. */
    val systemPrompt: String,
) {

    val chat: Boolean get() = mode == MODE_CHAT

    private val label: String get() = if (chat) "$model, chat mode" else model

    val valid: Boolean get() = invalidReasons.isEmpty()

    val ttftOk: Boolean get() = ttftP95Ms != null && ttftP95Ms <= TTFT_MAX_MS
    val speedOk: Boolean get() = tpsMedian != null && tpsMedian >= MIN_TPS
    val oomOk: Boolean get() = oomKills == 0

    /** null = no verdict (run not valid). */
    val pass: Boolean? get() = if (!valid) null else ttftOk && speedOk && oomOk

    fun validityLine(): String =
        if (valid) "VALID for gate: yes" else "VALID for gate: no — ${invalidReasons.joinToString("; ")}"

    fun gateLine(): String = when (pass) {
        null -> "GATE S2 ($label): no verdict, run not valid"
        true -> "GATE S2 ($label): PASS"
        false -> "GATE S2 ($label): FAIL — " + listOfNotNull(
            if (!ttftOk) "p95 TTFT ${f(ttftP95Ms, 0)} ms > ${TTFT_MAX_MS.toInt()} ms" else null,
            if (!speedOk) "median ${f(tpsMedian, 2)} tok/s < ${MIN_TPS.toInt()}" else null,
            if (!oomOk) "$oomKills OOM kill(s)" else null,
        ).joinToString("; ")
    }

    fun describe(): String {
        if (mode.isEmpty()) return "No run yet."
        return buildString {
            appendLine("Model:           $model ($mode)")
            appendLine("Device:          $device")
            appendLine("Prompts:         $completed of ${planned ?: "?"} finished")
            appendLine("Load:            ${f(loadMs, 0)} ms")
            appendLine("TTFT p50 / p95:  ${f(ttftP50Ms, 0)} / ${f(ttftP95Ms, 0)} ms   (gate: p95 ≤ ${TTFT_MAX_MS.toInt()})")
            appendLine("Decode median:   ${f(tpsMedian, 2)} tok/s   (gate: ≥ ${MIN_TPS.toInt()})")
            appendLine("Decode p10:      ${f(tpsP10, 2)} tok/s   (slowest 10 %; informational)")
            appendLine("OOM kills:       $oomKills   (gate: 0)")
            appendLine("Max PSS:         ${f(maxPssMb, 0)} MB (:inference, between prompts)")
            appendLine("Peak RSS:        ${f(maxRssPeakMb, 0)} MB (VmHWM of :inference)")
            appendLine("Min avail RAM:   ${f(minAvailMemMb, 0)} MB (system)")
            appendLine("Max thermal:     ${maxThermal ?: "n/a"} (0 none … 3 severe)")
            appendLine("Battery:         ${startPct ?: "?"}% → ${endPct ?: "?"}%, mean ${f(meanCurrentMa, 0)} mA")
            appendLine("Max tokens:      prefill ${maxPrefillTokens ?: "n/a"}, decode ${maxDecodeTokens ?: "n/a"} (cap $MAX_NEW)")
            if (chat) {
                appendLine("Preface:         ${f(prefaceMs, 0)} ms, ${prefaceTokens ?: "n/a"} tokens, system=$systemPrompt (untimed)")
                appendLine("Conv resets:     $resets   (slowest re-prefill ${f(maxResetMs, 0)} ms, untimed)")
            }
            appendLine(validityLine())
            appendLine(gateLine())
            if (pass == false) {
                appendLine()
                append(FAIL_ADVICE)
            }
        }
    }

    enum class Outcome(val text: String) {
        PASS("S2 passes on the floor device: proceed with this model (P1 may start once S1 passes too)."),
        RULE_0_5(
            "PRD 0.5 decision rule: (a) recommended spec becomes ≥ 6 GB RAM and a recent chip; " +
                "(b) the floor device keeps typed chat with the smallest model only, voice marked " +
                "\"not supported on this device\". Record it in project_status.md → Decisions.",
        ),
        FALLBACK(
            "Fails on the better device too: PRD 0.7 fallback — smaller model, shorter answers, typed " +
                "chat only — then re-run S2. Quality targets are not lowered silently (PRD 0.5).",
        ),
        NEED_BETTER_DEVICE(
            "Floor device fails: run the same model on a better device (≥ 6 GB, recent chip) to decide " +
                "between the PRD 0.5 rule and the PRD 0.7 fallback.",
        ),
    }

    companion object {
        const val TTFT_MAX_MS = 3000.0
        const val MIN_TPS = 5.0
        const val SOAK_PROMPTS = 50
        const val MAX_NEW = 128
        const val MIN_START_PCT = 60

        /** Run modes that can give a verdict; must match InferenceService. */
        const val MODE_SOAK = "soak"
        const val MODE_CHAT = "chat"

        /** Kills of `:inference` within this long after the run's last row still belong to it. */
        const val EXIT_GRACE_MS = 120_000L

        val FAIL_ADVICE =
            "If it fails (PRD 0.7): smaller model, shorter answers, typed chat only. If this is the floor " +
                "device and the same model passes on a better device, apply the PRD 0.5 rule (see SPEC.md §2)."

        /** Which PRD 0.5 / 0.7 outcome applies, from the floor device's and a better device's verdicts. */
        fun outcome(floorPass: Boolean, betterPass: Boolean?): Outcome = when {
            floorPass -> Outcome.PASS
            betterPass == true -> Outcome.RULE_0_5
            betterPass == false -> Outcome.FALLBACK
            else -> Outcome.NEED_BETTER_DEVICE
        }

        /** Nearest-rank percentile (p in 0..100) of [values]; null if empty. */
        fun percentile(values: List<Double>, p: Double): Double? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val rank = ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
            return sorted[rank - 1]
        }

        fun median(values: List<Double>): Double? {
            if (values.isEmpty()) return null
            val s = values.sorted()
            val mid = s.size / 2
            return if (s.size % 2 == 1) s[mid] else (s[mid - 1] + s[mid]) / 2.0
        }

        fun from(runCsv: String, exitCsv: String = ""): Summary {
            val rows = parse(runCsv).filter { it.long("epoch_ms") != null }
            val kinds = rows.map { it["kind"] }
            val start = rows.firstOrNull { it["kind"] == "start" }
            val startDetail = start?.get("detail").orEmpty()
            val planned = Regex("prompts=(\\d+)").find(startDetail)?.groupValues?.get(1)?.toIntOrNull()
            val device = Regex("device=(.*?) android=").find(startDetail)?.groupValues?.get(1) ?: "?"
            val prompts = rows.filter { it["kind"] == "prompt" }
            val errors = rows.filter { it["kind"] == "error" }
            val mode = rows.map { it["mode"] }.lastOrNull { it.isNotEmpty() }.orEmpty()

            // OOM kills: exits of :inference during the run, as recorded by ActivityManager.
            val first = rows.mapNotNull { it.long("epoch_ms") }.minOrNull() ?: 0L
            val last = rows.mapNotNull { it.long("epoch_ms") }.maxOrNull() ?: 0L
            val exits = parse(exitCsv).filter {
                val t = it.long("epoch_ms") ?: return@filter false
                it["process"].endsWith(":inference") && t >= first && t <= last + EXIT_GRACE_MS
            }
            val oomExits = exits.filter { isOom(it["reason"], it["status"]) }
            val otherExits = exits - oomExits.toSet()
            val oomErrors = errors.count { it["detail"].contains("OutOfMemory") }
            val oomKills = oomExits.size + oomErrors
            val died = "inference_died" in kinds

            val reasons = mutableListOf<String>()
            if (mode != MODE_SOAK && mode != MODE_CHAT) reasons += "${mode.ifEmpty { "unknown" }} run (only soak and chat runs count)"
            if (kinds.firstOrNull() != "start") reasons += "no start row"
            val preface = rows.firstOrNull { it["kind"] == "preface" }
            val resetRows = rows.filter { it["kind"] == "conv_reset" }
            if (mode == MODE_CHAT && preface == null && oomKills == 0) reasons += "no preface row"
            if (planned != null && planned != SOAK_PROMPTS) reasons += "planned $planned prompts, the gate needs $SOAK_PROMPTS"
            if (oomKills == 0) {
                if (planned != null && prompts.size < planned) reasons += "only ${prompts.size} of $planned prompts finished"
                if (kinds.lastOrNull() != "stop") reasons += "no clean stop (${kinds.lastOrNull() ?: "empty"})"
                if (died && otherExits.isEmpty()) reasons += "inference process died; reason not in exit_reasons.csv yet"
            }
            if (otherExits.isNotEmpty()) {
                reasons += "inference process died: ${otherExits.joinToString("/") { it["reason"] }} (not an OOM kill)"
            }
            val otherErrors = errors.size - oomErrors
            if (otherErrors > 0) reasons += "$otherErrors error row(s)"
            if (rows.any { it["screen_on"] == "false" }) reasons += "screen off during run"
            if (rows.any { it["charging"] == "true" }) reasons += "charging during run"
            if (rows.any { it["airplane"] == "false" }) reasons += "airplane mode off"
            if (rows.any { it["power_save"] == "true" }) reasons += "battery saver on"
            val startPct = rows.firstNotNullOfOrNull { it.int("battery_pct") }
            if (startPct != null && startPct < MIN_START_PCT) reasons += "battery $startPct % at start (< $MIN_START_PCT %)"

            val ttfts = prompts.mapNotNull { it.double("ttft_ms") }
            val tps = prompts.mapNotNull { it.double("decode_tps") }
            return Summary(
                model = rows.map { it["model"] }.firstOrNull { it.isNotEmpty() } ?: "?",
                mode = mode,
                device = device,
                planned = planned,
                completed = prompts.size,
                loadMs = rows.firstOrNull { it["kind"] == "load" }?.double("load_ms"),
                ttftP50Ms = percentile(ttfts, 50.0),
                ttftP95Ms = percentile(ttfts, 95.0),
                tpsMedian = median(tps),
                tpsP10 = percentile(tps, 10.0),
                maxPssMb = rows.mapNotNull { it.double("pss_kb") }.maxOrNull()?.div(1024.0),
                maxRssPeakMb = rows.mapNotNull { it.double("rss_peak_kb") }.maxOrNull()?.div(1024.0),
                minAvailMemMb = rows.mapNotNull { it.double("avail_mem_mb") }.minOrNull(),
                maxThermal = rows.mapNotNull { it.int("thermal") }.maxOrNull(),
                startPct = startPct,
                endPct = rows.lastOrNull { it.int("battery_pct") != null }?.int("battery_pct"),
                meanCurrentMa = prompts.mapNotNull { it.double("current_ua") }.takeIf { it.isNotEmpty() }
                    ?.map { abs(it) / 1000.0 }?.average(),
                maxPrefillTokens = prompts.mapNotNull { it.int("prefill_tokens") }.maxOrNull(),
                maxDecodeTokens = prompts.mapNotNull { it.int("decode_tokens") }.maxOrNull(),
                oomKills = oomKills,
                invalidReasons = reasons,
                prefaceMs = preface?.double("total_ms"),
                prefaceTokens = preface?.int("prefill_tokens"),
                resets = resetRows.size,
                maxResetMs = resetRows.mapNotNull { it.double("total_ms") }.maxOrNull(),
                systemPrompt = preface?.get("detail")?.substringAfter("system=", "")?.substringBefore(' ').orEmpty(),
            )
        }

        /**
         * LOW_MEMORY or MEMORY_LIMITER, or SIGNALED with SIGKILL (9): ApplicationExitInfo docs say
         * devices that can't report LOW_MEMORY report a memory-pressure kill that way. Counting
         * SIGKILL is conservative; a kill by the user would also count, so don't swipe the app away.
         */
        fun isOom(reason: String, status: String): Boolean =
            reason == "LOW_MEMORY" || reason == "MEMORY_LIMITER" || (reason == "SIGNALED" && status == "9")

        private fun parse(csv: String): List<Row> {
            val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
            val idx = lines.firstOrNull()?.split(",").orEmpty().withIndex().associate { it.value.trim() to it.index }
            return lines.drop(1).map { Row(it.split(","), idx) }
        }

        private fun f(v: Double?, digits: Int) = v?.let { "%.${digits}f".format(Locale.US, it) } ?: "n/a"
    }

    /** A CSV row read by column name; missing columns or cells read as "". */
    private class Row(private val cells: List<String>, private val idx: Map<String, Int>) {
        operator fun get(col: String): String = idx[col]?.let { cells.getOrNull(it) }?.trim().orEmpty()
        fun long(col: String) = get(col).toLongOrNull()
        fun int(col: String) = get(col).toIntOrNull()
        fun double(col: String) = get(col).toDoubleOrNull()
    }
}
