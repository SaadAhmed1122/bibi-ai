package io.github.saadahmed1122.bibi.core.common.usage

import java.io.File
import java.time.LocalDate
import java.util.Properties

/** App and phone details printed at the top of the report. */
data class DeviceInfo(val appVersion: String, val model: String, val android: String)

/** The plain-text report a tester shares (SPEC-test-report.md). Numbers and dates only. */
object UsageReport {
    fun format(d: UsageData, device: DeviceInfo): String = buildList {
        add("Bibi AI usage numbers")
        add("App version: ${device.appVersion}")
        add("Phone: ${device.model}, Android ${device.android}")
        add("First use: ${d.firstUse ?: "not used yet"}")
        add("Active days: ${d.activeDays.size} (${d.activeDaysInFirst14()} in the first 14 days)")
        add("Used on day 7 or later: ${if (d.usedOnDay7OrLater()) "yes" else "no"}")
        add("Sessions: ${d.sessions}")
        add("Crash-free sessions: ${d.sessions - d.crashedSessions}")
        add("Crashes: ${d.crashes}")
        add("ANRs: ${d.anrs}")
        add("Low-memory kills: ${d.lowMemoryKills}")
        add("Messages: ${d.features[Feature.MESSAGE] ?: 0}")
        add("Talks: ${d.features[Feature.TALK] ?: 0}")
        add("Memories saved: ${d.features[Feature.MEMORY] ?: 0}")
        val actions = d.actions.toSortedMap().entries.joinToString(", ") { "${it.key} ${it.value}" }
        add("Actions: ${actions.ifEmpty { "none" }}")
        add("Only these numbers are in this report: no messages, voice or memories.")
    }.joinToString("\n")
}

/** A small key=value file in app storage. A missing or damaged file reads as empty; writes replace it whole. */
class FileUsageStore(private val file: File) : UsageStore {
    override fun load(): UsageData {
        if (!file.exists()) return UsageData.EMPTY
        return try {
            val p = Properties().apply { file.reader().use(::load) }
            fun int(key: String) = p.getProperty(key)?.toInt() ?: 0
            UsageData(
                firstUse = p.getProperty("firstUse")?.let(LocalDate::parse),
                activeDays = p.list("activeDays").mapTo(HashSet(), LocalDate::parse),
                sessions = int("sessions"),
                crashedSessions = int("crashedSessions"),
                crashes = int("crashes"),
                anrs = int("anrs"),
                lowMemoryKills = int("lowMemoryKills"),
                features = Feature.entries.mapNotNull { f -> p.getProperty("feature.$f")?.let { f to it.toInt() } }.toMap(),
                actions = p.stringPropertyNames().filter { it.startsWith("action.") }
                    .associate { it.removePrefix("action.") to p.getProperty(it).toInt() },
                recent = p.list("recent").map { s ->
                    val (start, end, crashed) = s.split(":")
                    Session(start.toLong(), end.takeIf { it != "-" }?.toLong(), crashed == "1")
                },
                backgroundSince = p.getProperty("backgroundSince")?.toLong(),
            )
        } catch (_: Exception) {
            UsageData.EMPTY
        }
    }

    override fun save(data: UsageData) {
        val p = Properties()
        data.firstUse?.let { p["firstUse"] = it.toString() }
        p["activeDays"] = data.activeDays.sorted().joinToString(",")
        p["sessions"] = data.sessions.toString()
        p["crashedSessions"] = data.crashedSessions.toString()
        p["crashes"] = data.crashes.toString()
        p["anrs"] = data.anrs.toString()
        p["lowMemoryKills"] = data.lowMemoryKills.toString()
        data.features.forEach { (f, n) -> p["feature.$f"] = n.toString() }
        data.actions.forEach { (name, n) -> p["action.$name"] = n.toString() }
        p["recent"] = data.recent.joinToString(",") { "${it.start}:${it.end ?: "-"}:${if (it.crashed) 1 else 0}" }
        data.backgroundSince?.let { p["backgroundSince"] = it.toString() }
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writer().use { p.store(it, null) }
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun Properties.list(key: String): List<String> =
        getProperty(key).orEmpty().split(",").filter { it.isNotEmpty() }
}
