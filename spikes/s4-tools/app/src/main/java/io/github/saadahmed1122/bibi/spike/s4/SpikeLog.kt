package io.github.saadahmed1122.bibi.spike.s4

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One CSV per run in files/runs/ (`s4_<yyyyMMdd-HHmmss>_<model>.csv`), columns [Summary.HEADER].
 * A run starts when the app starts and on "New run file". Rows are written after the timed
 * section, so file I/O never counts towards a measurement. Main thread only.
 *
 * - kind `bench`/`live`: one timed action (`action, path, mode, ok, *_ms`); Summary reads these.
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
        file.writeText(Summary.HEADER + "\n")
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

    private fun write(
        kind: String, action: String, path: String, mode: String, ok: String,
        totalMs: Double?, parseMs: Double?, policyMs: Double?, buildMs: Double?, dispatchMs: Double?, detail: String,
    ) {
        val file = current ?: return
        val now = System.currentTimeMillis()
        val row = listOf(
            time.format(Date(now)), now.toString(), runId, kind, action, path, mode, ok,
            ms(totalMs), ms(parseMs), ms(policyMs), ms(buildMs), ms(dispatchMs), clean(detail),
        ).joinToString(",")
        file.appendText(row + "\n")
    }

    fun runFiles(): List<File> =
        (runsDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") } ?: emptyArray())
            .sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))

    fun readCurrent(): String = current?.takeIf { it.exists() }?.readText() ?: ""

    private fun ms(v: Double?) = v?.let { "%.3f".format(Locale.US, it) } ?: ""

    private fun clean(s: String) = s.replace(',', ';').replace('\n', ' ').replace('\r', ' ')
}
