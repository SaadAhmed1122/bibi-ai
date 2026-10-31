package io.github.saadahmed1122.bibi.feature.tools

import android.content.Intent
import android.content.pm.PackageManager
import io.github.saadahmed1122.bibi.core.tools.AppEntry

/**
 * The launcher app list, computed once (PRD 0.7 S4: "precompute the app list"), so `open_app`
 * never queries PackageManager on the action path. Visibility comes from the manifest's
 * `<queries>` entry for MAIN/LAUNCHER, not QUERY_ALL_PACKAGES. Ported from S4.
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
