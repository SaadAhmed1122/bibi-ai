package io.github.saadahmed1122.bibi.core.common.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExitInfoCollectorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private class FakeStore(override var lastSeenMillis: Long = 0) : LastSeenStore

    private val writer by lazy { CrashLogWriter(File(tmp.root, "crash.log"), appVersion = "1") }

    private fun exit(ts: Long, reason: ExitReason?, desc: String = "d$ts") =
        ExitRecord(timestampMillis = ts, reason = reason, processName = "proc", description = desc)

    @Test
    fun keepsOnlyCrashNativeCrashAnrAndLowMemory() {
        val exits = listOf(
            exit(1, ExitReason.CRASH),
            exit(2, ExitReason.CRASH_NATIVE),
            exit(3, ExitReason.ANR),
            exit(4, ExitReason.LOW_MEMORY),
            exit(5, null, desc = "user-swiped-away"),
        )

        val added = ExitInfoCollector({ exits }, FakeStore(), writer).collect()

        assertEquals(4, added)
        val log = writer.read()
        listOf("exit=CRASH", "exit=CRASH_NATIVE", "exit=ANR", "exit=LOW_MEMORY").forEach {
            assertTrue("$it missing", log.contains(it))
        }
        assertFalse(log.contains("user-swiped-away"))
    }

    @Test
    fun reportsEachNewLoggedExitOnceInOrder() {
        val exits = listOf(exit(3, ExitReason.ANR), exit(1, ExitReason.CRASH), exit(1, ExitReason.CRASH), exit(2, null))
        val seen = mutableListOf<Long>()
        val store = FakeStore()

        ExitInfoCollector({ exits }, store, writer) { seen += it.timestampMillis }.collect()
        ExitInfoCollector({ exits }, store, writer) { seen += it.timestampMillis }.collect()

        assertEquals(listOf(1L, 3L), seen)
    }

    @Test
    fun skipsRecordsAlreadySeenAndStoresTheNewestTimestamp() {
        val store = FakeStore(lastSeenMillis = 20)
        val exits = listOf(exit(30, ExitReason.ANR), exit(10, ExitReason.CRASH), exit(20, ExitReason.CRASH))

        val added = ExitInfoCollector({ exits }, store, writer).collect()

        assertEquals(1, added)
        assertEquals(30, store.lastSeenMillis)
        assertTrue(writer.read().contains("d30"))
        assertFalse(writer.read().contains("d10"))
    }

    @Test
    fun duplicateRecordsFromTheSystemAreLoggedOnce() {
        // vivo Y17s, 2026-10-31: one crash came back twice from getHistoricalProcessExitReasons (#55).
        val exits = listOf(exit(7, ExitReason.CRASH, desc = "same"), exit(7, ExitReason.CRASH, desc = "same"))

        val added = ExitInfoCollector({ exits }, FakeStore(), writer).collect()

        assertEquals(1, added)
        assertEquals(1, Regex("exit=CRASH").findAll(writer.read()).count())
    }

    @Test
    fun secondRunAddsNothing() {
        val store = FakeStore()
        val exits = listOf(exit(5, ExitReason.CRASH), exit(7, ExitReason.ANR))
        val collector = ExitInfoCollector({ exits }, store, writer)

        assertEquals(2, collector.collect())
        assertEquals(0, collector.collect())
        assertEquals(2, writer.read().lines().count { it.startsWith(CrashLogWriter.RECORD_START) })
    }

    @Test
    fun writesOldestFirst() {
        val exits = listOf(exit(9, ExitReason.ANR, "late"), exit(3, ExitReason.CRASH, "early"))

        ExitInfoCollector({ exits }, FakeStore(), writer).collect()

        val log = writer.read()
        assertTrue(log.indexOf("early") < log.indexOf("late"))
    }

    @Test
    fun mapsPlatformReasonCodes() {
        // Values from android.app.ApplicationExitInfo (android-36 android.jar).
        assertEquals(ExitReason.LOW_MEMORY, ExitReason.fromPlatform(3))
        assertEquals(ExitReason.CRASH, ExitReason.fromPlatform(4))
        assertEquals(ExitReason.CRASH_NATIVE, ExitReason.fromPlatform(5))
        assertEquals(ExitReason.ANR, ExitReason.fromPlatform(6))
        assertEquals(null, ExitReason.fromPlatform(10))
    }
}
