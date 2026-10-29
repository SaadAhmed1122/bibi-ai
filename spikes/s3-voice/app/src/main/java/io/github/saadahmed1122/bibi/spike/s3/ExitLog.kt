package io.github.saadahmed1122.bibi.spike.s3

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * files/exit_reasons.csv (`time, epoch_ms, reason, detail, process`), never cleared. Copies
 * Android's recorded process exits (ApplicationExitInfo, both `<package>` and
 * `<package>:inference`) and app-written rows (`process` = [APP_ROW]). Main process only, so the
 * SharedPreferences it uses are never shared across processes. Adapted from S1's SpikeLog.
 */
class ExitLog(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("s3", Context.MODE_PRIVATE)
    private val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val file = File(appContext.filesDir, "exit_reasons.csv")

    @Synchronized
    fun logNewExitReasons() {
        val am = appContext.getSystemService(ActivityManager::class.java)
        val lastSeen = prefs.getLong(KEY_LAST_EXIT, 0L)
        val fresh = am.getHistoricalProcessExitReasons(appContext.packageName, 0, 20)
            .filter { it.timestamp > lastSeen }.sortedBy { it.timestamp }
        if (fresh.isEmpty()) return
        ensureHeader()
        file.appendText(
            fresh.joinToString("") { info ->
                listOf(
                    time.format(Date(info.timestamp)), info.timestamp.toString(), reasonName(info.reason),
                    RunLog.clean(info.description ?: ""), RunLog.clean(info.processName),
                ).joinToString(",") + "\n"
            },
        )
        prefs.edit().putLong(KEY_LAST_EXIT, fresh.last().timestamp).apply()
    }

    @Synchronized
    fun diagnostic(kind: String, detail: String) {
        val now = System.currentTimeMillis()
        ensureHeader()
        file.appendText(listOf(time.format(Date(now)), now.toString(), RunLog.clean(kind), RunLog.clean(detail), APP_ROW).joinToString(",") + "\n")
    }

    fun read(): String = if (file.exists()) file.readText() else ""

    private fun ensureHeader() {
        if (!file.exists()) file.writeText(HEADER + "\n")
    }

    companion object {
        const val HEADER = "time,epoch_ms,reason,detail,process"
        const val APP_ROW = "app_event"
        private const val KEY_LAST_EXIT = "last_exit_ts"

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
