package io.github.saadahmed1122.bibi.spike.s1

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Appends one CSV row per sample or event to files/s1_log.csv.
 * Samples carry frame counts; events (start, stop, restart, screen_on, ...) carry a detail string.
 */
class SpikeLog(context: Context) {

    private val appContext = context.applicationContext
    val file = File(appContext.filesDir, "s1_log.csv")
    private val battery = appContext.getSystemService(BatteryManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun sample(frames: Int, intervalMs: Long, screenOn: Boolean, calm: Boolean, mode: String) {
        val fps = if (intervalMs > 0) frames * 1000.0 / intervalMs else 0.0
        write("sample", "", frames.toString(), "%.2f".format(Locale.US, fps), screenOn, calm, mode)
    }

    fun event(kind: String, detail: String, screenOn: Boolean, calm: Boolean, mode: String) =
        write(kind, detail, "", "", screenOn, calm, mode)

    @Synchronized
    private fun write(kind: String, detail: String, frames: String, fps: String, screenOn: Boolean, calm: Boolean, mode: String) {
        val now = System.currentTimeMillis()
        val charge = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val current = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val row = listOf(
            time.format(Date(now)),
            now.toString(),
            kind,
            detail.replace(',', ';').replace('\n', ' '),
            battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toString(),
            if (charge == Int.MIN_VALUE) "" else charge.toString(), // MIN_VALUE = not supported
            if (current == Int.MIN_VALUE) "" else current.toString(),
            battery.isCharging.toString(),
            screenOn.toString(),
            power.currentThermalStatus.toString(),
            frames,
            fps,
            Debug.getPss().toString(),
            calm.toString(),
            mode,
        ).joinToString(",")
        if (!file.exists()) file.writeText(HEADER + "\n")
        file.appendText(row + "\n")
    }

    fun read(): String = if (file.exists()) file.readText() else ""

    fun clear() {
        file.delete()
    }

    /** Logs process exits Android recorded since the last check (OS kills show up here). */
    fun logNewExitReasons(prefs: Prefs) {
        val am = appContext.getSystemService(ActivityManager::class.java)
        val reasons = am.getHistoricalProcessExitReasons(appContext.packageName, 0, 10)
        val lastSeen = prefs.lastExitTimestamp
        val fresh = reasons.filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }
        for (info in fresh) {
            val detail = "${reasonName(info.reason)}|${time.format(Date(info.timestamp))}|${info.description ?: ""}"
            write("exit_reason", detail, "", "", power.isInteractive, prefs.calm, "-")
        }
        fresh.maxOfOrNull { it.timestamp }?.let { prefs.lastExitTimestamp = it }
    }

    companion object {
        const val HEADER =
            "time,epoch_ms,kind,detail,battery_pct,charge_uah,current_ua,charging,screen_on,thermal,frames,fps,pss_kb,calm,mode"

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
