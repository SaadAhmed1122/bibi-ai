package io.github.saadahmed1122.bibi.spike.s2

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * CSV logging shared by both processes, following S1's SpikeLog.
 *
 * - Run files `files/runs/s2_<yyyyMMdd-HHmm>_<device>_<model>_<mode>.csv` are written by the
 *   `:inference` process while it runs; the UI process appends only an `inference_died` row, after
 *   that process is gone, so the two never write the same file at once.
 * - `files/exit_reasons.csv` is written only by the UI process ([logNewExitReasons]).
 *
 * Every row also records the run conditions (battery, thermal, screen, airplane mode, memory).
 */
class BenchLog(context: Context) {

    private val appContext = context.applicationContext
    val runsDir = File(appContext.filesDir, "runs")
    val exitFile = File(appContext.filesDir, "exit_reasons.csv")
    private val battery = appContext.getSystemService(BatteryManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val activities = appContext.getSystemService(ActivityManager::class.java)
    private val prefs = appContext.getSharedPreferences("s2", Context.MODE_PRIVATE)
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Creates an empty run file (header only) and returns it. */
    fun newRunFile(model: String, mode: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
        val base = "s2_${stamp}_${safe(Build.MODEL)}_${safe(model)}_$mode"
        runsDir.mkdirs()
        var file = File(runsDir, "$base.csv")
        var n = 2
        while (file.exists()) file = File(runsDir, "${base}_${n++}.csv")
        file.writeText(HEADER + "\n")
        return file
    }

    /**
     * Appends one row. [fields] fills measurement columns by name (unknown names are an error);
     * condition columns are read here. Callers read PSS (slow) only between prompts, never while
     * a prompt is being timed.
     */
    fun write(file: File, runId: String, kind: String, detail: String, model: String, mode: String, fields: Map<String, String> = emptyMap()) {
        require(COLUMNS.containsAll(fields.keys)) { "unknown columns ${fields.keys - COLUMNS.toSet()}" }
        val now = System.currentTimeMillis()
        val current = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val memory = ActivityManager.MemoryInfo().also { activities.getMemoryInfo(it) }
        val values = HashMap(fields)
        values["time"] = time.format(Date(now))
        values["epoch_ms"] = now.toString()
        values["run_id"] = runId
        values["kind"] = kind
        values["detail"] = detail
        values["model"] = model
        values["mode"] = mode
        values["battery_pct"] = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString()
        values["current_ua"] = if (current == Int.MIN_VALUE) "" else current.toString() // MIN_VALUE = unsupported
        values["charging"] = battery.isCharging.toString()
        values["batt_temp_dc"] = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeIf { it != Int.MIN_VALUE }?.toString() ?: ""
        values["thermal"] = power.currentThermalStatus.toString()
        values["screen_on"] = power.isInteractive.toString()
        values["power_save"] = power.isPowerSaveMode.toString()
        values["airplane"] = (Settings.Global.getInt(appContext.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1).toString()
        values["brightness"] = systemSetting(Settings.System.SCREEN_BRIGHTNESS)
        values["avail_mem_mb"] = (memory.availMem / MB).toString()
        values["low_memory"] = memory.lowMemory.toString()
        file.appendText(COLUMNS.joinToString(",") { clean(values[it] ?: "") } + "\n")
    }

    /** All run files, newest last. */
    fun runFiles(): List<File> =
        (runsDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") } ?: emptyArray())
            .sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))

    fun readExitReasons(): String = if (exitFile.exists()) exitFile.readText() else ""

    /**
     * UI process only. Copies process exits Android recorded since the last check to
     * exit_reasons.csv, for both the UI process and `:inference`.
     */
    @Synchronized
    fun logNewExitReasons() {
        val reasons = activities.getHistoricalProcessExitReasons(appContext.packageName, 0, 20)
        val lastSeen = prefs.getLong(KEY_LAST_EXIT, 0L)
        val fresh = reasons.filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }
        if (fresh.isEmpty()) return
        if (!exitFile.exists()) exitFile.writeText(EXIT_HEADER + "\n")
        exitFile.appendText(
            fresh.joinToString("") { info ->
                listOf(
                    time.format(Date(info.timestamp)),
                    info.timestamp.toString(),
                    reasonName(info.reason),
                    info.status.toString(),
                    info.description ?: "",
                    info.processName ?: "",
                    info.importance.toString(),
                    info.pss.toString(),
                    info.rss.toString(),
                ).joinToString(",") { clean(it) } + "\n"
            },
        )
        prefs.edit().putLong(KEY_LAST_EXIT, fresh.last().timestamp).apply()
    }

    private fun systemSetting(name: String): String = try {
        Settings.System.getInt(appContext.contentResolver, name, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.toString() ?: ""
    } catch (e: SecurityException) {
        ""
    }

    companion object {
        private const val KEY_LAST_EXIT = "last_exit_ts"
        private const val MB = 1024L * 1024L

        /** Keep in sync with tools/summary-test/SummaryTest.kt. Summary reads columns by name. */
        const val HEADER =
            "time,epoch_ms,run_id,kind,detail,model,mode," +
                "prompt_idx,prompt_id,prompt_chars,prefill_tokens,decode_tokens,tokens_source,chunks," +
                "load_ms,ttft_ms,decode_tps,total_ms,eng_ttft_ms,eng_decode_tps," +
                "pss_kb,rss_kb,rss_peak_kb,avail_mem_mb,low_memory," +
                "thermal,battery_pct,current_ua,charging,batt_temp_dc,screen_on,airplane,brightness,power_save"
        val COLUMNS: List<String> = HEADER.split(",")

        const val EXIT_HEADER = "time,epoch_ms,reason,status,detail,process,importance,pss_kb,rss_kb"

        /** ApplicationExitInfo.REASON_MEMORY_LIMITER (17) is newer than compileSdk 36. */
        private const val REASON_MEMORY_LIMITER = 17

        fun reasonName(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_OTHER -> "OTHER"
            ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
            REASON_MEMORY_LIMITER -> "MEMORY_LIMITER"
            else -> "REASON_$reason"
        }

        fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9.-]"), "-")

        private fun clean(s: String) = s.replace(',', ';').replace('\n', ' ').replace('\r', ' ')
    }
}
