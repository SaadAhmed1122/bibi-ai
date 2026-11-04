package io.github.saadahmed1122.bibi.core.common.usage

import io.github.saadahmed1122.bibi.core.common.crash.ExitReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset

class UsageReportTest {
    @get:Rule val tmp = TemporaryFolder()

    private val device = DeviceInfo(appVersion = "0.3.0-p3", model = "vivo V2333", android = "15")

    @Test
    fun reportHasAFixedShape() {
        val data = UsageData.EMPTY.copy(
            firstUse = LocalDate.of(2026, 11, 4),
            activeDays = setOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 11)),
            sessions = 10,
            crashedSessions = 1,
            crashes = 1,
            anrs = 0,
            lowMemoryKills = 2,
            features = mapOf(Feature.MESSAGE to 12, Feature.TALK to 3),
            actions = mapOf("TOGGLE_FLASHLIGHT" to 2, "SET_TIMER" to 1),
        )
        val expected = """
            Bibi AI usage numbers
            App version: 0.3.0-p3
            Phone: vivo V2333, Android 15
            First use: 2026-11-04
            Active days: 2 (2 in the first 14 days)
            Used on day 7 or later: yes
            Sessions: 10
            Crash-free sessions: 9
            Crashes: 1
            ANRs: 0
            Low-memory kills: 2
            Messages: 12
            Talks: 3
            Memories saved: 0
            Actions: SET_TIMER 1, TOGGLE_FLASHLIGHT 2
            Only these numbers are in this report: no messages, voice or memories.
        """.trimIndent()
        assertEquals(expected, UsageReport.format(data, device))
    }

    @Test
    fun emptyReportSaysNotUsedYet() {
        val text = UsageReport.format(UsageData.EMPTY, device)
        assertEquals(true, text.contains("First use: not used yet"))
        assertEquals(true, text.contains("Actions: none"))
    }

    @Test
    fun nothingTypedOrSaidCanReachTheReport() {
        // The tracker API takes no text: messages, memories and talks are counted, never stored.
        val marker = "SECRET-MARKER-7f3a"
        val store = FileUsageStore(File(tmp.root, "usage/usage.txt"))
        val t = UsageTracker(store, { 1_793_793_600_000L }, { ZoneOffset.UTC })
        t.begin()
        listOf("my sister is $marker", "remember $marker").forEach { _ -> t.count(Feature.MESSAGE) }
        t.count(Feature.MEMORY)
        t.count(Feature.TALK)
        t.recordExit(1_793_793_600_500L, ExitReason.CRASH)
        val text = UsageReport.format(t.snapshot(), device)
        assertFalse(text.contains(marker))
        assertFalse(File(tmp.root, "usage/usage.txt").readText().contains(marker))
    }

    @Test
    fun fileStoreRoundTrips() {
        val file = File(tmp.root, "usage/usage.txt")
        val data = UsageData.EMPTY.copy(
            firstUse = LocalDate.of(2026, 11, 4),
            activeDays = setOf(LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 5)),
            sessions = 3,
            crashedSessions = 1,
            crashes = 2,
            anrs = 1,
            lowMemoryKills = 4,
            features = mapOf(Feature.MESSAGE to 5, Feature.MEMORY to 1),
            actions = mapOf("SET_ALARM" to 1),
            recent = listOf(Session(10, 20, false), Session(100, null, true)),
            backgroundSince = 150L,
        )
        FileUsageStore(file).save(data)
        assertEquals(data, FileUsageStore(file).load())
    }

    @Test
    fun missingOrDamagedFileLoadsEmpty() {
        val file = File(tmp.root, "usage/usage.txt")
        assertEquals(UsageData.EMPTY, FileUsageStore(file).load())
        file.parentFile!!.mkdirs()
        file.writeText("sessions=not-a-number\nrecent=garbage")
        assertEquals(UsageData.EMPTY, FileUsageStore(file).load())
    }
}
