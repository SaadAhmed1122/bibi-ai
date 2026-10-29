package io.github.saadahmed1122.bibi.spike.s4.coretest

import io.github.saadahmed1122.bibi.spike.s4.AppEntry
import io.github.saadahmed1122.bibi.spike.s4.AppResolver

// open_app target resolution against the precomputed launcher list (PRD 0.7 fallback: precompute
// the app list). Only launcher-visible packages can ever be opened.

fun appResolverTests() {
    val apps = listOf(
        AppEntry("Maps", "com.google.android.apps.maps"),
        AppEntry("Camera", "com.sec.android.app.camera"),
        AppEntry("YouTube", "com.google.android.youtube"),
        AppEntry("YouTube Music", "com.google.android.apps.youtube.music"),
        AppEntry("Settings", "com.android.settings"),
        AppEntry("Clock", "com.sec.android.app.clockpackage"),
        AppEntry("Google", "com.google.android.googlequicksearchbox"),
    )
    val r = AppResolver(apps)
    checkEq("exact label", "com.google.android.apps.maps", r.resolve("maps")?.packageName)
    checkEq("label case-insensitive", "com.google.android.youtube", r.resolve("YOUTUBE")?.packageName)
    checkEq("alias google maps → maps", "com.google.android.apps.maps", r.resolve("Google Maps")?.packageName)
    checkEq("exact package", "com.android.settings", r.resolve("com.android.settings")?.packageName)
    checkEq("package not in launcher list → null", null, r.resolve("com.android.systemui"))
    checkEq("package is case-sensitive", null, r.resolve("COM.ANDROID.SETTINGS"))
    checkEq("unique prefix", "com.sec.android.app.clockpackage", r.resolve("clo")?.packageName)
    checkEq("ambiguous prefix → null", null, r.resolve("you"))
    checkEq("label word match", "com.google.android.apps.youtube.music", r.resolve("music")?.packageName)
    checkEq("unknown → null", null, r.resolve("door"))
    checkEq("empty list → null", null, AppResolver(emptyList()).resolve("maps"))
    val browsers = AppResolver(listOf(AppEntry("Samsung Internet", "com.sec.android.app.sbrowser")))
    checkEq("alias picks the installed package", "com.sec.android.app.sbrowser", browsers.resolve("browser")?.packageName)
    checkEq("alias with no installed target → null", null, AppResolver(listOf(AppEntry("Notes", "com.example.notes"))).resolve("browser"))
    checkEq("leading 'the' and trailing 'app' ignored", "com.sec.android.app.camera", r.resolve("the camera app")?.packageName)
}
