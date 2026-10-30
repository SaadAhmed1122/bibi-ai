package io.github.saadahmed1122.bibi.spike.s4

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One CSV per run in files/runs/ (`s4_<yyyyMMdd-HHmmss>_<model>.csv`), columns
 * [LlmSummary.FULL_HEADER]: the gate columns [Summary.HEADER] unchanged, then the LLM columns
 * [LlmSummary.COLUMNS] (empty on every non-LLM row).
 * A run starts when the app starts and on "New run file". Rows are written after the timed
 * section, so file I/O never counts towards a measurement. Main thread only.
 *
 * - kind `bench`/`live`: one timed action (`action, path, mode, ok, *_ms`); Summary reads these.
 * - kind `llm_bench`/`llm_live`: one LLM-path request with the LLM columns; LlmSummary reads `llm_bench`.
 * - other kinds (`app_index`, `confirm_shown`, `confirm_result`, `router`, `denied`, `no_match`,
 *   `bench_start`, `bench_end`, `bench_aborted`, `device`): events; timings in their columns where useful.
 * Details are redacted (FR-122): numbers keep their last 2 digits.
 */
class SpikeLog(context: Context) {
    val runsDir = File(context.applicationContext.filesDir, "runs")
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    var current: File? = null
        private set
    private var runId = ""

    fun beginRun() {
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9-]"), "-")
        runsDir.mkdirs()
        val file = File(runsDir, "s4_${stamp}_$model.csv")
        file.writeText(LlmSummary.FULL_HEADER + "\n")
        current = file
        runId = now.toString()
        event("device", "${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    }

    fun action(
        kind: String,
        action: String,
        path: String,
        mode: String,
        ok: Boolean,
        totalMs: Double?,
        parseMs: Double?,
        policyMs: Double?,
        buildMs: Double?,
        dispatchMs: Double?,
        detail: String,
    ) = write(kind, action, path, mode, ok.toString(), totalMs, parseMs, policyMs, buildMs, dispatchMs, detail)

    fun event(kind: String, detail: String, totalMs: Double? = null, action: String = "", path: String = "") =
        write(kind, action, path, "", "", totalMs, null, null, null, null, detail)

    /**
     * One LLM-path request. [llm] maps [LlmSummary.COLUMNS] names to values; missing ones stay empty.
     * Values are cleaned like [detail] (commas become `;`), so `args_json` is stored with `;`.
     */
    fun llm(kind: String, action: String, mode: String, ok: Boolean, detail: String, llm: Map<String, String>) =
        write(kind, action, "llm", mode, ok.toString(), null, null, null, null, null, detail, llm)

    private fun write(
        kind: String, action: String, path: String, mode: String, ok: String,
        totalMs: Double?, parseMs: Double?, policyMs: Double?, buildMs: Double?, dispatchMs: Double?, detail: String,
        llm: Map<String, String> = emptyMap(),
    ) {
        val file = current ?: return
        val now = System.currentTimeMillis()
        val row = (
            listOf(
                time.format(Date(now)), now.toString(), runId, kind, action, path, mode, ok,
                ms(totalMs), ms(parseMs), ms(policyMs), ms(buildMs), ms(dispatchMs), clean(detail),
            ) + LLM_COLUMNS.map { clean(llm[it].orEmpty()) }
            ).joinToString(",")
        file.appendText(row + "\n")
    }

    fun runFiles(): List<File> =
        (runsDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") } ?: emptyArray())
            .sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))

    fun readCurrent(): String = current?.takeIf { it.exists() }?.readText() ?: ""

    private fun clean(s: String) = s.replace(',', ';').replace('\n', ' ').replace('\r', ' ')

    companion object {
        private val LLM_COLUMNS = LlmSummary.COLUMNS.split(",")

        fun ms(v: Double?) = v?.let { "%.3f".format(Locale.US, it) } ?: ""
    }
}
