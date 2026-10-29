package io.github.saadahmed1122.bibi.spike.s4

import android.content.Intent
import android.content.pm.PackageManager

/**
 * Precomputes the launcher app list once (PRD 0.7 S4 fallback: "precompute the app list"), so
 * `open_app` never queries PackageManager on the timed path. Visibility comes from the manifest's
 * `<queries>` entry for MAIN/LAUNCHER, not QUERY_ALL_PACKAGES (developer.android.com,
 * "Declaring package visibility needs" and the `<queries>` element reference).
 * Call [load] off the main thread: loadLabel() reads each app's resources.
 */
object AppIndex {
    fun load(pm: PackageManager): List<AppEntry> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
            .map { AppEntry(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
}
