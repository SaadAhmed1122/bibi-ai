package io.github.saadahmed1122.bibi.spike.s1

import android.content.Context

/** Small persisted settings shared by the activity and the service. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("s1", Context.MODE_PRIVATE)

    /** Mode of the last started run, reused when the system restarts the service (START_STICKY). */
    var runMode: RunMode?
        get() = RunMode.fromLogName(sp.getString("run_mode", null))
        set(value) = sp.edit().putString("run_mode", value?.logName).apply()

    var x: Int
        get() = sp.getInt("x", -1)
        set(value) = sp.edit().putInt("x", value).apply()

    var y: Int
        get() = sp.getInt("y", -1)
        set(value) = sp.edit().putInt("y", value).apply()

    var lastExitTimestamp: Long
        get() = sp.getLong("last_exit_ts", 0L)
        set(value) = sp.edit().putLong("last_exit_ts", value).apply()
}
