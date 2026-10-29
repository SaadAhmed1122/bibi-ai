package io.github.saadahmed1122.bibi.spike.s3

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Device conditions written on every row; filled by the Android side, empty in JVM tests. */
class Conditions(val thermal: String = "", val batteryPct: String = "", val charging: String = "")

/**
 * Appends rows to one run file (`files/runs/s3_<stamp>_<device>_<task>_<model>_t<threads>.csv`).
 * Pure java.io, so both processes and the JVM tests use it. The `:inference` process writes all
 * rows except `process_died`, which the main process appends after the inference process is gone.
 * Every row is appended and closed at once, so an OOM kill loses at most the row being written.
 */
class RunLog(
    val file: File,
    private val runId: String,
    private val device: String,
    private val task: Task,
    private val spec: ModelSpec,
    private val threads: Int,
    private val conditions: () -> Conditions = { Conditions() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun row(
        kind: String,
        item: String = "",
        round: Int? = null,
        chars: Int? = null,
        audioS: Double? = null,
        computeMs: Double? = null,
        rtf: Double? = null,
        pssPeakKb: Long? = null,
        vmHwmKb: Long? = null,
        detail: String = "",
        reference: String = "",
    ) {
        val now = clock()
        val c = conditions()
        val cells = listOf(
            time.format(Date(now)), now.toString(), runId, kind, device, task.logName, spec.id,
            spec.family.logName, threads.toString(), clean(item), round?.toString() ?: "",
            chars?.toString() ?: "", fmt(audioS, 3), fmt(computeMs, 1), fmt(rtf, 4),
            pssPeakKb?.toString() ?: "", vmHwmKb?.toString() ?: "",
            c.thermal, c.batteryPct, c.charging, clean(detail), clean(reference),
        )
        file.parentFile?.mkdirs()
        if (!file.exists()) file.writeText(HEADER + "\n")
        file.appendText(cells.joinToString(",") + "\n")
    }

    companion object {
        const val HEADER =
            "time,epoch_ms,run_id,kind,device,task,model,family,threads,item,round,chars," +
                "audio_s,compute_ms,rtf,pss_peak_kb,vmhwm_kb,thermal,battery_pct,charging,detail,reference"

        /** Row kinds. `warmup` rows are logged but never counted in the statistics. */
        const val START = "start"
        const val LOAD = "load"
        const val WARMUP = "warmup"
        const val ITEM = "item"
        const val ERROR = "error"
        const val STOP = "stop"
        const val PROCESS_DIED = "process_died"

        fun fileName(stampMs: Long, device: String, task: Task, spec: ModelSpec, threads: Int): String {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(stampMs))
            return "s3_${stamp}_${safe(device)}_${task.logName}_${safe(spec.id)}_t$threads.csv"
        }

        fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9._-]"), "-")

        /** CSV cells are never quoted, so commas and line breaks are replaced. */
        fun clean(s: String) = s.replace(',', ';').replace('\n', ' ').replace('\r', ' ')

        private fun fmt(v: Double?, digits: Int) = v?.let { "%.${digits}f".format(Locale.US, it) } ?: ""
    }
}
