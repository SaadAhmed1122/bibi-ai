package io.github.saadahmed1122.bibi.core.common.crash

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.SharedPreferences

/** The process exit reasons worth logging (SPEC-app-shell.md). */
enum class ExitReason {
    CRASH,
    CRASH_NATIVE,
    ANR,
    LOW_MEMORY,
    ;

    companion object {
        fun fromPlatform(reason: Int): ExitReason? = when (reason) {
            ApplicationExitInfo.REASON_CRASH -> CRASH
            ApplicationExitInfo.REASON_CRASH_NATIVE -> CRASH_NATIVE
            ApplicationExitInfo.REASON_ANR -> ANR
            ApplicationExitInfo.REASON_LOW_MEMORY -> LOW_MEMORY
            else -> null
        }
    }
}

/** A platform-free copy of the fields we log from [ApplicationExitInfo]; [reason] is null for ignored reasons. */
data class ExitRecord(
    val timestampMillis: Long,
    val reason: ExitReason?,
    val processName: String,
    val description: String?,
)

fun interface ExitInfoSource {
    fun exits(): List<ExitRecord>
}

interface LastSeenStore {
    var lastSeenMillis: Long
}

/** Adds exits newer than the last check to the crash log. Call once at main-process startup, off the main thread. */
class ExitInfoCollector(
    private val source: ExitInfoSource,
    private val store: LastSeenStore,
    private val writer: CrashLogWriter,
) {
    /** Returns the number of records written. */
    fun collect(): Int {
        val lastSeen = store.lastSeenMillis
        val fresh = source.exits().filter { it.timestampMillis > lastSeen }
        if (fresh.isEmpty()) return 0
        val logged = fresh.filter { it.reason != null }.sortedBy { it.timestampMillis }
        logged.forEach { writer.writeExit(it.timestampMillis, it.processName, it.reason!!.name, it.description) }
        store.lastSeenMillis = fresh.maxOf { it.timestampMillis }
        return logged.size
    }
}

/** Reads every exit the system kept for [packageName] (pid 0 and maxNum 0 mean all). */
class PlatformExitInfoSource(
    private val activityManager: ActivityManager,
    private val packageName: String,
) : ExitInfoSource {
    override fun exits(): List<ExitRecord> =
        activityManager.getHistoricalProcessExitReasons(packageName, 0, 0).map {
            ExitRecord(it.timestamp, ExitReason.fromPlatform(it.reason), it.processName, it.description)
        }
}

class PrefsLastSeenStore(private val prefs: SharedPreferences) : LastSeenStore {
    override var lastSeenMillis: Long
        get() = prefs.getLong(KEY, 0)
        set(value) = prefs.edit().putLong(KEY, value).apply()

    private companion object {
        const val KEY = "exit_info_last_seen_ms"
    }
}
