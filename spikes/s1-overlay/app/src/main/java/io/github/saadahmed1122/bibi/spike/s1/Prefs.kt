package io.github.saadahmed1122.bibi.spike.s1

import android.content.Context

/** Small persisted settings shared by the activity and the service. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("s1", Context.MODE_PRIVATE)

    /** Mode of the last started run, reused when the system restarts the service (START_STICKY). */
    var runMode: RunMode?
        get() = RunMode.fromLogName(sp.getString("run_mode", null))
        set(value) = sp.edit().putString("run_mode", value?.logName).apply()

    /** File name (in files/runs/) of the current run; a START_STICKY restart appends to it. */
    var runFile: String?
        get() = sp.getString("run_file", null)
        set(value) {
            sp.edit().putString("run_file", value).commit() // a restart right after start must find it
        }

    /** Id of the current run (start epoch ms as a string), written in the `run_id` column. */
    var runId: String?
        get() = sp.getString("run_id", null)
        set(value) {
            sp.edit().putString("run_id", value).commit()
        }

    var x: Int
        get() = sp.getInt("x", -1)
        set(value) = sp.edit().putInt("x", value).apply()

    var y: Int
        get() = sp.getInt("y", -1)
        set(value) = sp.edit().putInt("y", value).apply()

    /** Newest exit timestamp already copied to exit_reasons.csv; guarded by [SpikeLog]'s exit lock. */
    var lastExitTimestamp: Long
        get() = sp.getLong("last_exit_ts", 0L)
        set(value) = sp.edit().putLong("last_exit_ts", value).apply()
}
