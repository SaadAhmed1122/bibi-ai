package io.github.saadahmed1122.bibi.spike.s1

import android.content.Context

/** Small persisted settings shared by the activity and the service. */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("s1", Context.MODE_PRIVATE)

    var calm: Boolean
        get() = sp.getBoolean("calm", false)
        set(value) = sp.edit().putBoolean("calm", value).apply()

    /** True when the last started run was a baseline run (no overlay). */
    var baseline: Boolean
        get() = sp.getBoolean("baseline", false)
        set(value) = sp.edit().putBoolean("baseline", value).apply()

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
