package io.github.saadahmed1122.bibi.core.common.usage

import io.github.saadahmed1122.bibi.core.common.crash.ExitReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UsageTrackerTest {
    private val store = MemoryStore()
    private var now = T0

    private fun tracker() = UsageTracker(store, { now }, { ZoneOffset.UTC })

    private fun UsageTracker.session(lengthMs: Long = 60_000) {
        begin()
        now += lengthMs
        end()
    }

    @Test
    fun firstBeginStartsASessionAndMarksTheDay() {
        val t = tracker()
        t.begin()
        val d = t.snapshot()
        assertEquals(1, d.sessions)
        assertEquals(LocalDate.of(2026, 11, 4), d.firstUse)
        assertEquals(setOf(LocalDate.of(2026, 11, 4)), d.activeDays)
    }

    @Test
    fun overlappingBeginsAreOneSession() {
        val t = tracker()
        t.begin() // app in front
        now += 5_000
        t.begin() // a talk starts from floating Bibi
        now += 5_000
        t.end()
        t.end()
        assertEquals(1, t.snapshot().sessions)
    }

    @Test
    fun returningWithinTheGraceContinuesTheSession() {
        val t = tracker()
        t.session()
        now += UsageTracker.GRACE_MS // e.g. rotation or a quick app switch
        t.session()
        assertEquals(1, t.snapshot().sessions)
    }

    @Test
    fun returningAfterTheGraceStartsANewSession() {
        val t = tracker()
        t.session()
        now += UsageTracker.GRACE_MS + 1
        t.session()
        assertEquals(2, t.snapshot().sessions)
    }

    @Test
    fun crashDuringASessionMarksItOnce() {
        val t = tracker()
        t.begin()
        t.recordExit(now + 1_000, ExitReason.CRASH)
        t.recordExit(now + 2_000, ExitReason.CRASH_NATIVE) // :inference crashes too
        val d = t.snapshot()
        assertEquals(1, d.sessions)
        assertEquals(1, d.crashedSessions)
        assertEquals(2, d.crashes)
    }

    @Test
    fun crashWithinTheGraceAfterTheEndCountsAndLaterDoesNot() {
        val t = tracker()
        t.session()
        val end = now
        now += UsageTracker.GRACE_MS * 10
        t.session()
        t.recordExit(end + UsageTracker.GRACE_MS, ExitReason.CRASH)
        assertEquals(1, t.snapshot().crashedSessions)

        val t2 = UsageTracker(MemoryStore(), { now }, { ZoneOffset.UTC })
        val start = now
        t2.session()
        t2.recordExit(start + 60_000 + UsageTracker.GRACE_MS + 1, ExitReason.CRASH)
        assertEquals(0, t2.snapshot().crashedSessions)
        assertEquals(1, t2.snapshot().crashes) // still counted as a crash
    }

    @Test
    fun anrBreaksTheSession() {
        val t = tracker()
        t.begin()
        t.recordExit(now, ExitReason.ANR)
        val d = t.snapshot()
        assertEquals(1, d.crashedSessions)
        assertEquals(1, d.anrs)
        assertEquals(0, d.crashes)
    }

    @Test
    fun lowMemoryKillIsCountedButDoesNotBreakTheSession() {
        val t = tracker()
        t.begin()
        t.recordExit(now, ExitReason.LOW_MEMORY)
        val d = t.snapshot()
        assertEquals(0, d.crashedSessions)
        assertEquals(1, d.lowMemoryKills)
    }

    @Test
    fun aSessionCutByProcessDeathStaysOpenUntilTheNextStart() {
        val first = tracker()
        first.begin()
        val crashAt = now + 10 * 60_000 // long after the last event
        now += 60 * 60_000
        // New process: the exit is collected after the next session has started.
        val second = tracker()
        second.begin()
        second.recordExit(crashAt, ExitReason.CRASH)
        val d = second.snapshot()
        assertEquals(2, d.sessions)
        assertEquals(1, d.crashedSessions)
    }

    @Test
    fun exitBeforeAnySessionIsCountedButBreaksNothing() {
        val t = tracker()
        t.recordExit(now - 1, ExitReason.CRASH)
        t.begin()
        val d = t.snapshot()
        assertEquals(1, d.crashes)
        assertEquals(0, d.crashedSessions)
    }

    @Test
    fun retentionCountsActiveDaysInTheFirstTwoWeeksAndDaySeven() {
        val t = tracker()
        for (day in listOf(0, 1, 5, 13, 14, 20)) {
            now = T0 + day * DAY_MS
            t.session()
        }
        val d = t.snapshot()
        assertEquals(6, d.activeDays.size)
        assertEquals(4, d.activeDaysInFirst14())
        assertTrue(d.usedOnDay7OrLater())

        val early = UsageData.EMPTY.copy(
            firstUse = LocalDate.of(2026, 11, 4),
            activeDays = setOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 9)), // day 6
        )
        assertFalse(early.usedOnDay7OrLater())
    }

    @Test
    fun oldDaysAreDroppedButTheFirstUseStays() {
        val t = tracker()
        t.session()
        now = T0 + 200 * DAY_MS
        t.session()
        val d = t.snapshot()
        assertEquals(LocalDate.of(2026, 11, 4), d.firstUse)
        assertEquals(1, d.activeDays.size)
    }

    @Test
    fun recentSessionsAreCapped() {
        val t = tracker()
        repeat(UsageTracker.MAX_RECENT + 10) {
            t.session()
            now += UsageTracker.GRACE_MS + 1
        }
        val d = t.snapshot()
        assertEquals(UsageTracker.MAX_RECENT + 10, d.sessions)
        assertEquals(UsageTracker.MAX_RECENT, d.recent.size)
    }

    @Test
    fun featuresAndActionsAreCountedByName() {
        val t = tracker()
        t.count(Feature.MESSAGE)
        t.count(Feature.MESSAGE)
        t.count(Feature.TALK)
        t.count(Feature.MEMORY)
        t.countAction(SampleTool.FLASHLIGHT)
        t.countAction(SampleTool.FLASHLIGHT)
        val d = t.snapshot()
        assertEquals(2, d.features[Feature.MESSAGE])
        assertEquals(1, d.features[Feature.TALK])
        assertEquals(1, d.features[Feature.MEMORY])
        assertEquals(mapOf("FLASHLIGHT" to 2), d.actions)
    }

    @Test
    fun everyChangeIsSavedAndReloaded() {
        val t = tracker()
        t.begin()
        t.count(Feature.MESSAGE)
        t.recordExit(now, ExitReason.CRASH)
        assertEquals(t.snapshot(), tracker().snapshot())
    }

    @Test
    fun clearEmptiesEverything() {
        val t = tracker()
        t.session()
        t.count(Feature.TALK)
        t.recordExit(now, ExitReason.LOW_MEMORY)
        t.clear()
        assertEquals(UsageData.EMPTY, t.snapshot())
        assertEquals(UsageData.EMPTY, store.load())
    }

    private enum class SampleTool { FLASHLIGHT }

    private class MemoryStore : UsageStore {
        private var data = UsageData.EMPTY
        override fun load() = data
        override fun save(data: UsageData) {
            this.data = data
        }
    }

    private companion object {
        const val T0 = 1_793_793_600_000L // 2026-11-04T12:00:00Z
        const val DAY_MS = 86_400_000L
    }
}
