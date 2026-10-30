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

    /**
     * Every name this resolver knows, normalized with [normalizeName] → the app it resolves to: each
     * label, each label without a leading vendor word ("Samsung Notes" → "notes") and each alias
     * whose package is installed. A name that [resolve]s to a different app is left out.
     */
    val names: Map<String, AppEntry> by lazy {
        val out = LinkedHashMap<String, AppEntry>()
        fun add(name: String, entry: AppEntry) {
            val key = normalizeName(name)
            if (key.isNotEmpty() && key !in out && resolve(key)?.packageName == entry.packageName) out[key] = entry
        }
        for (e in entries) add(e.label, e)
        for (e in entries) {
            val key = normalizeName(e.label)
            val vendor = VENDORS.firstOrNull { key.startsWith("$it ") }
            if (vendor != null) add(key.removePrefix("$vendor "), e)
        }
        for (alias in aliases.keys) resolve(alias)?.let { add(alias, it) }
        out
    }

    companion object {
        /** Vendor words users often add or drop in front of an app name ("google drive" for "Drive"). */
        val VENDORS = listOf("google", "samsung", "galaxy")

        /** Lowercase; anything but letters, digits, & ' + becomes a space; spaces collapsed. */
        fun normalizeName(s: String): String =
            s.lowercase().replace(Regex("[^\\p{L}\\p{N}&'+]+"), " ").trim()

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
