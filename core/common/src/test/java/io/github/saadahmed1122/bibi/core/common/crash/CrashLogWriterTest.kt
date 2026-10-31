package io.github.saadahmed1122.bibi.core.common.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CrashLogWriterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-31T10:15:30Z"), ZoneOffset.UTC)

    private fun writer(file: File = File(tmp.root, "crash/crash.log"), maxBytes: Int = CrashLogWriter.MAX_BYTES) =
        CrashLogWriter(file, appVersion = "0.1.0-p1", clock = clock, maxBytes = maxBytes)

    @Test
    fun exceptionRecordHasTimeProcessThreadVersionAndStack() {
        val w = writer()
        w.writeException("io.github.saadahmed1122.bibi", "main", IllegalStateException("boom"))

        val log = w.read()
        assertEquals(1, log.lines().count { it.startsWith(CrashLogWriter.RECORD_START) })
        val header = log.lines().first()
        assertTrue(header, header.contains("2026-10-31T10:15:30Z"))
        assertTrue(header, header.contains("process=io.github.saadahmed1122.bibi"))
        assertTrue(header, header.contains("thread=main"))
        assertTrue(header, header.contains("version=0.1.0-p1"))
        assertTrue(log, log.contains("java.lang.IllegalStateException: boom"))
        assertTrue(log, log.contains("at io.github.saadahmed1122.bibi.core.common.crash.CrashLogWriterTest"))
    }

    @Test
    fun exitRecordUsesTheExitTimestampAndLabel() {
        val w = writer()
        w.writeExit(Instant.parse("2026-10-30T08:00:00Z").toEpochMilli(), "io.github.saadahmed1122.bibi:inference", "ANR", "Input dispatching timed out")

        val log = w.read()
        assertTrue(log, log.lines().first().contains("2026-10-30T08:00:00Z"))
        assertTrue(log, log.contains("process=io.github.saadahmed1122.bibi:inference"))
        assertTrue(log, log.contains("exit=ANR"))
        assertTrue(log, log.contains("Input dispatching timed out"))
    }

    @Test
    fun recordsAppendInOrder() {
        val w = writer()
        w.writeException("p", "t1", RuntimeException("first"))
        w.writeException("p", "t2", RuntimeException("second"))

        val log = w.read()
        assertTrue(log.indexOf("first") in 0 until log.indexOf("second"))
    }

    @Test
    fun fileStaysUnderCapAndDropsOldestRecords() {
        val file = File(tmp.root, "crash.log")
        val w = writer(file, maxBytes = 4 * 1024)
        repeat(200) { w.writeException("p", "t", shortError("record-$it")) }

        assertTrue("size=${file.length()}", file.length() <= 4 * 1024)
        val log = w.read()
        assertTrue(log.startsWith(CrashLogWriter.RECORD_START))
        assertTrue(log.contains("record-198\n"))
        assertTrue(log.contains("record-199\n"))
        assertFalse(log.contains("record-0\n"))
    }

    @Test
    fun recordAfterAnOversizedOneIsKept() {
        val file = File(tmp.root, "crash.log")
        val w = writer(file, maxBytes = 512)
        w.writeException("p", "t", RuntimeException("x".repeat(5_000)))
        w.writeException("p", "t", shortError("after"))

        assertTrue(w.read().contains("after"))
        assertTrue("size=${file.length()}", file.length() <= 512)
    }

    @Test
    fun defaultCapIs256KiB() {
        val file = File(tmp.root, "crash.log")
        val w = writer(file)
        repeat(2_000) { w.writeException("p", "t", RuntimeException("record-$it")) }

        assertTrue("size=${file.length()}", file.length() <= 256 * 1024)
        assertTrue(w.read().contains("record-1999"))
    }

    @Test
    fun singleOversizedRecordIsTruncatedToCap() {
        val file = File(tmp.root, "crash.log")
        val w = writer(file, maxBytes = 512)
        w.writeException("p", "t", RuntimeException("x".repeat(5_000)))

        assertTrue("size=${file.length()}", file.length() <= 512)
        assertTrue(w.read().startsWith(CrashLogWriter.RECORD_START))
    }

    private fun shortError(message: String) = RuntimeException(message).apply { stackTrace = emptyArray() }

    @Test
    fun clearEmptiesTheLog() {
        val w = writer()
        w.writeException("p", "t", RuntimeException("gone"))
        w.clear()

        assertEquals("", w.read())
    }

    @Test
    fun readWithoutFileIsEmpty() {
        assertEquals("", writer().read())
    }
}
