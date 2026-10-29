package io.github.saadahmed1122.bibi.spike.s1

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.provider.Settings
import android.view.Display
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes one CSV per run to files/runs/ (one row per sample or event) and process exits to
 * files/exit_reasons.csv. The current run's file name and id live in [Prefs], so a START_STICKY
 * restart keeps appending to the same file.
 */
class SpikeLog(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = Prefs(appContext)
    val runsDir = File(appContext.filesDir, "runs")
    val exitFile = File(appContext.filesDir, "exit_reasons.csv")
    private val battery = appContext.getSystemService(BatteryManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val displays = appContext.getSystemService(DisplayManager::class.java)
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Starts a new run file `s1_<yyyyMMdd-HHmm>_<model>_<mode>.csv` and makes it current. */
    @Synchronized
    fun beginRun(mode: RunMode) {
        val now = System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
        val model = Build.MODEL.replace(Regex("[^A-Za-z0-9-]"), "-")
        val base = "s1_${stamp}_${model}_${mode.logName}"
        runsDir.mkdirs()
        var name = "$base.csv"
        var n = 2
        while (File(runsDir, name).exists()) name = "${base}_${n++}.csv" // two starts in one minute
        prefs.runFile = name
        prefs.runId = now.toString()
    }

    /** True if a run file was begun; false e.g. after an update from a build without per-run files. */
    val hasRun: Boolean get() = prefs.runFile != null

    fun sample(frames: Int, intervalMs: Long, screenOn: Boolean, mode: RunMode) {
        val fps = if (intervalMs > 0) frames * 1000.0 / intervalMs else 0.0
        // PSS is costly to read, so only sample rows carry it.
        write("sample", "", frames.toString(), "%.2f".format(Locale.US, fps), Debug.getPss().toString(), screenOn, mode)
    }

    fun event(kind: String, detail: String, screenOn: Boolean, mode: RunMode) =
        write(kind, detail, "", "", "", screenOn, mode)

    /** Appends a row to the current run file; does nothing if no run was begun. */
    @Synchronized
    private fun write(
        kind: String,
        detail: String,
        frames: String,
        fps: String,
        pssKb: String,
        screenOn: Boolean,
        mode: RunMode,
    ) {
        val file = prefs.runFile?.let { File(runsDir, it) } ?: return
        val now = System.currentTimeMillis()
        val charge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val current = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        // Sticky broadcast: a null receiver just returns the last ACTION_BATTERY_CHANGED intent.
        val batteryIntent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val row = listOf(
            time.format(Date(now)),
            now.toString(),
            prefs.runId ?: "",
            kind,
            clean(detail),
            battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString(),
            if (charge == Int.MIN_VALUE) "" else charge.toString(), // MIN_VALUE = not supported
            if (current == Int.MIN_VALUE) "" else current.toString(),
            battery.isCharging.toString(),
            screenOn.toString(),
            power.currentThermalStatus.toString(),
            frames,
            fps,
            pssKb,
            mode.calm.toString(),
            mode.logName,
            batteryExtra(batteryIntent, BatteryManager.EXTRA_VOLTAGE),
            batteryExtra(batteryIntent, BatteryManager.EXTRA_TEMPERATURE),
            systemSetting(Settings.System.SCREEN_BRIGHTNESS),
            systemSetting(Settings.System.SCREEN_BRIGHTNESS_MODE),
            power.isPowerSaveMode.toString(),
            displays.getDisplay(Display.DEFAULT_DISPLAY)?.let { "%.1f".format(Locale.US, it.refreshRate) } ?: "",
        ).joinToString(",")
        runsDir.mkdirs()
        if (!file.exists()) file.writeText(HEADER + "\n")
        file.appendText(row + "\n")
    }

    /** All run files, newest last (by modification time, then name). */
    fun runFiles(): List<File> =
        (runsDir.listFiles { f -> f.isFile && f.name.endsWith(".csv") } ?: emptyArray())
            .sortedWith(compareBy<File>({ it.lastModified() }, { it.name }))

    /** Text of the newest run file, or "" if there is none. Works while the run is still writing. */
    fun readNewestRun(): String = runFiles().lastOrNull()?.readText() ?: ""

    fun readExitReasons(): String = if (exitFile.exists()) exitFile.readText() else ""

    /**
     * Copies process exits Android recorded since the last check (OS kills show up here) to
     * exit_reasons.csv. Called from both the activity and the service; [exitLock] keeps the
     * read-filter-write of [Prefs.lastExitTimestamp] atomic so no exit is logged twice.
     */
    fun logNewExitReasons() = synchronized(exitLock) {
        val am = appContext.getSystemService(ActivityManager::class.java)
        val reasons = am.getHistoricalProcessExitReasons(appContext.packageName, 0, 10)
        val lastSeen = prefs.lastExitTimestamp
        val fresh = reasons.filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }
        if (fresh.isEmpty()) return@synchronized
        if (!exitFile.exists()) exitFile.writeText(EXIT_HEADER + "\n")
        exitFile.appendText(
            fresh.joinToString("") { info ->
                listOf(
                    time.format(Date(info.timestamp)),
                    info.timestamp.toString(),
                    reasonName(info.reason),
                    clean(info.description ?: ""),
                    clean(info.processName ?: ""),
                ).joinToString(",") + "\n"
            },
        )
        prefs.lastExitTimestamp = fresh.last().timestamp
    }

    /**
     * Appends an app-written row (e.g. a blocked start) to exit_reasons.csv, for events that happen
     * when no run is active. A blocked start must not create or touch a run file. The `process`
     * column is [APP_ROW] so these rows can't be confused with exits Android recorded.
     */
    fun diagnostic(kind: String, detail: String) = synchronized(exitLock) {
        val now = System.currentTimeMillis()
        if (!exitFile.exists()) exitFile.writeText(EXIT_HEADER + "\n")
        exitFile.appendText(listOf(time.format(Date(now)), now.toString(), clean(kind), clean(detail), APP_ROW).joinToString(",") + "\n")
    }

    /** An int extra of the battery intent, or "" if the intent or extra is missing. */
    private fun batteryExtra(intent: Intent?, name: String): String =
        intent?.getIntExtra(name, Int.MIN_VALUE)?.takeIf { it != Int.MIN_VALUE }?.toString() ?: ""

    /** A Settings.System int (readable without WRITE_SETTINGS), or "" if unset or unreadable. */
    private fun systemSetting(name: String): String = try {
        Settings.System.getInt(appContext.contentResolver, name, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }?.toString() ?: ""
    } catch (e: SecurityException) {
        ""
    }

    private fun clean(s: String) = s.replace(',', ';').replace('\n', ' ').replace('\r', ' ')

    companion object {
        /** Shared by every [SpikeLog] instance in the process. */
        private val exitLock = Any()

        const val HEADER =
            "time,epoch_ms,run_id,kind,detail,battery_pct,charge_uah,current_ua,charging,screen_on,thermal," +
                "frames,fps,pss_kb,calm,mode," +
                "voltage_mv,batt_temp_dc,brightness,brightness_mode,power_save,refresh_hz"
        const val EXIT_HEADER = "time,epoch_ms,reason,detail,process"

        /** `process` value of rows written by [diagnostic] rather than read from ActivityManager. */
        const val APP_ROW = "app_event"

        fun reasonName(reason: Int): String = when (reason) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
            ApplicationExitInfo.REASON_CRASH -> "CRASH"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfo.REASON_ANR -> "ANR"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
            ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
            ApplicationExitInfo.REASON_FREEZER -> "FREEZER"
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
            else -> "OTHER($reason)"
        }
    }
}
