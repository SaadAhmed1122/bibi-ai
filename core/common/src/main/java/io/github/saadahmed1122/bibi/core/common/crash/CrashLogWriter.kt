package io.github.saadahmed1122.bibi.core.common.crash

import java.io.File
import java.time.Clock
import java.time.Instant

/**
 * Appends crash and exit records to a local text file (SPEC-app-shell.md, crash log).
 * The file never leaves the device. Over [maxBytes] the oldest records are dropped.
 *
 * Synchronized within a process; the `:inference` and main processes may both write, which is
 * rare enough (two crashes at once) that a cross-process lock is not worth it.
 */
class CrashLogWriter(
    private val file: File,
    private val appVersion: String,
    private val clock: Clock = Clock.systemUTC(),
    private val maxBytes: Int = MAX_BYTES,
) {
    @Synchronized
    fun writeException(processName: String, threadName: String, error: Throwable) =
        append(clock.instant(), processName, "thread=$threadName", error.stackTraceToString())

    @Synchronized
    fun writeExit(timestampMillis: Long, processName: String, reasonLabel: String, description: String?) =
        append(Instant.ofEpochMilli(timestampMillis), processName, "exit=$reasonLabel", description ?: "(no description)")

    @Synchronized
    fun read(): String = if (file.isFile) file.readText() else ""

    @Synchronized
    fun clear() {
        file.delete()
    }

    private fun append(time: Instant, processName: String, detail: String, body: String) {
        val record = "$RECORD_START$time | process=$processName | $detail | version=$appVersion\n${body.trimEnd()}\n"
        file.parentFile?.mkdirs()
        file.appendText(record)
        if (file.length() > maxBytes) file.writeText(trimToCap(file.readText()))
    }

    /** Drops whole records from the front; a single record still too big is cut at the end. */
    private fun trimToCap(text: String): String {
        var kept = text
        while (kept.utf8Size() > maxBytes) {
            val next = kept.indexOf("\n$RECORD_START")
            if (next < 0) {
                // Keep the trailing newline so the next record still starts on its own line.
                kept = kept.take(maxBytes - 1)
                while (kept.utf8Size() > maxBytes - 1) kept = kept.dropLast(1)
                kept += "\n"
                break
            }
            kept = kept.substring(next + 1)
        }
        return kept
    }

    private fun String.utf8Size() = toByteArray(Charsets.UTF_8).size

    companion object {
        const val MAX_BYTES = 256 * 1024
        const val RECORD_START = "=== "
    }
}
