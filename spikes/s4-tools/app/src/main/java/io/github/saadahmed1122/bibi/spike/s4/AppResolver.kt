package io.github.saadahmed1122.bibi.spike.s4

// Pure Kotlin (no android.*).

/** One launcher activity's app, from the precomputed launcher list (AppIndex). */
data class AppEntry(val label: String, val packageName: String)

/**
 * Resolves an `open_app` target to an app on the launcher list. Only listed apps can ever be
 * opened, so neither the user nor the model can reach a non-launcher component. Order:
 * 1. a package name → exact (case-sensitive) package match only;
 * 2. exact label (case-insensitive, ignoring a leading "the" and a trailing "app");
 * 3. alias table → the first aliased package that is installed;
 * 4. a unique label prefix;
 * 5. a unique whole-word match in a label.
 * Ambiguous or unknown → null (the UI says it can't find the app). Matching rules are [OPEN] (tools-permissions.md).
 */
class AppResolver(
    private val entries: List<AppEntry>,
    private val aliases: Map<String, List<String>> = DEFAULT_ALIASES,
) {
    private val byLabel = entries.groupBy { it.label.lowercase().trim() }
    private val byPackage = entries.associateBy { it.packageName }

    fun resolve(query: String): AppEntry? {
        val q = query.trim()
        if (q.contains('.')) return byPackage[q]
        val name = q.lowercase().removePrefix("the ").removeSuffix(" app").trim()
        if (name.isEmpty()) return null
        byLabel[name]?.let { return it.first() }
        aliases[name]?.firstNotNullOfOrNull { byPackage[it] }?.let { return it }
        entries.filter { it.label.lowercase().startsWith(name) }.singleOrNull()?.let { return it }
        return entries.filter { e -> e.label.lowercase().split(' ').any { it == name } }.singleOrNull()
    }

    companion object {
        /**
         * Common names → candidate packages (AOSP, Google, Samsung). Package names are from general
         * knowledge, not checked on the test phones [VERIFY]; vivo's own apps are not listed [OPEN].
         */
        val DEFAULT_ALIASES: Map<String, List<String>> = mapOf(
            "maps" to listOf("com.google.android.apps.maps"),
            "google maps" to listOf("com.google.android.apps.maps"),
            "camera" to listOf("com.sec.android.app.camera", "com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2"),
            "browser" to listOf("com.android.chrome", "com.sec.android.app.sbrowser"),
            "internet" to listOf("com.sec.android.app.sbrowser", "com.android.chrome"),
            "clock" to listOf("com.sec.android.app.clockpackage", "com.google.android.deskclock", "com.android.deskclock"),
            "gallery" to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos"),
            "photos" to listOf("com.google.android.apps.photos", "com.sec.android.gallery3d"),
            "phone" to listOf("com.samsung.android.dialer", "com.google.android.dialer", "com.android.dialer"),
            "settings" to listOf("com.android.settings"),
            "youtube" to listOf("com.google.android.youtube"),
        )
    }
}
