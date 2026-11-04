package io.github.saadahmed1122.bibi.core.common.usage

import io.github.saadahmed1122.bibi.core.common.crash.ExitReason
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What is counted besides sessions (SPEC-test-report.md). Counts only; no text is ever passed in. */
enum class Feature { MESSAGE, TALK, MEMORY }

/** One session; [end] is null while it is open (or was cut by process death). */
data class Session(val start: Long, val end: Long?, val crashed: Boolean)

/** Numbers and dates only (SPEC-P3.md shared rules). */
data class UsageData(
    val firstUse: LocalDate?,
    val activeDays: Set<LocalDate>,
    val sessions: Int,
    val crashedSessions: Int,
    val crashes: Int,
    val anrs: Int,
    val lowMemoryKills: Int,
    val features: Map<Feature, Int>,
    /** Action counts by tool enum name. */
    val actions: Map<String, Int>,
    /** The last [UsageTracker.MAX_RECENT] sessions, for matching exits that are read later. */
    val recent: List<Session>,
    /** When the open session went to the background; null while in use. */
    val backgroundSince: Long?,
) {
    /** Retention signal: days used in days 1–14 of use. */
    fun activeDaysInFirst14(): Int = firstUse?.let { f -> activeDays.count { it < f.plusDays(14) } } ?: 0

    /** Retention signal: used on day 7 (first use + 6 days) or later. */
    fun usedOnDay7OrLater(): Boolean = firstUse?.let { f -> activeDays.any { it >= f.plusDays(6) } } ?: false

    companion object {
        val EMPTY = UsageData(null, emptySet(), 0, 0, 0, 0, 0, emptyMap(), emptyMap(), emptyList(), null)
    }
}

interface UsageStore {
    fun load(): UsageData
    fun save(data: UsageData)
}

/**
 * On-phone counters for the P3 gate (SPEC-test-report.md): sessions, crash-free sessions, active days and
 * feature counts. Every change is saved at once, so a crash loses nothing. Thread-safe: UI events come from the
 * main thread, exits from the startup thread.
 *
 * A session runs while any surface is in use ([begin]/[end] nest). Coming back within [GRACE_MS] continues it.
 * A session cut by process death stays open until the next one starts, so a crash in that gap counts against it
 * (the cautious reading for a ≥ 99 % gate).
 */
class UsageTracker(
    private val store: UsageStore,
    private val now: () -> Long,
    private val zone: () -> ZoneId,
) {
    private var data = store.load()

    /** Surfaces in use in this process; starts at 0 in a new process whatever the file says. */
    private var inUse = 0

    @Synchronized fun begin() {
        if (inUse++ > 0) return
        val t = now()
        val open = data.recent.lastOrNull()?.takeIf { it.end == null }
        val bg = data.backgroundSince
        if (open != null && bg != null && t - bg <= GRACE_MS) {
            update(data.copy(backgroundSince = null))
            return
        }
        // Close the open session: at its background time, or (process died while in use) now.
        val recent = if (open != null) data.recent.dropLast(1) + open.copy(end = bg ?: t) else data.recent
        val day = Instant.ofEpochMilli(t).atZone(zone()).toLocalDate()
        update(
            data.copy(
                firstUse = data.firstUse ?: day,
                activeDays = (data.activeDays + day).filterTo(HashSet()) { it > day.minusDays(KEEP_DAYS) },
                sessions = data.sessions + 1,
                recent = (recent + Session(t, null, false)).takeLast(MAX_RECENT),
                backgroundSince = null,
            ),
        )
    }

    @Synchronized fun end() {
        if (inUse == 0) return
        if (--inUse == 0) update(data.copy(backgroundSince = now()))
    }

    @Synchronized fun count(feature: Feature) {
        update(data.copy(features = data.features + (feature to (data.features[feature] ?: 0) + 1)))
    }

    /** [tool] is an enum, so only its constant name is stored. */
    @Synchronized fun countAction(tool: Enum<*>) {
        update(data.copy(actions = data.actions + (tool.name to (data.actions[tool.name] ?: 0) + 1)))
    }

    /** An exit of any Bibi process, read from the system's exit records. */
    @Synchronized fun recordExit(timestampMillis: Long, reason: ExitReason) {
        var d = when (reason) {
            ExitReason.LOW_MEMORY -> return update(data.copy(lowMemoryKills = data.lowMemoryKills + 1))
            ExitReason.ANR -> data.copy(anrs = data.anrs + 1)
            ExitReason.CRASH, ExitReason.CRASH_NATIVE -> data.copy(crashes = data.crashes + 1)
        }
        val i = d.recent.indexOfLast { covers(it, timestampMillis) }
        if (i >= 0 && !d.recent[i].crashed) {
            d = d.copy(
                crashedSessions = d.crashedSessions + 1,
                recent = d.recent.toMutableList().also { it[i] = it[i].copy(crashed = true) },
            )
        }
        update(d)
    }

    @Synchronized fun snapshot(): UsageData = data

    /** Delete everything (SPEC-privacy.md). */
    @Synchronized fun clear() {
        inUse = 0
        update(UsageData.EMPTY)
    }

    private fun covers(s: Session, t: Long): Boolean {
        val last = s.end ?: data.backgroundSince.takeIf { s === data.recent.last() }
        return t >= s.start && (last == null || t <= last + GRACE_MS)
    }

    private fun update(d: UsageData) {
        data = d
        store.save(d)
    }

    companion object {
        /** Assumption [VERIFY against tester use], SPEC-test-report.md. */
        const val GRACE_MS = 30_000L
        const val MAX_RECENT = 50
        const val KEEP_DAYS = 120L
    }
}
