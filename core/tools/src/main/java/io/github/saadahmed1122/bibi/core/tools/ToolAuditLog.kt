package io.github.saadahmed1122.bibi.core.tools

import java.io.File
import java.time.Clock

/**
 * Local audit log of actions (FR-122, SPEC-tools.md): one line per outcome, in a capped text file
 * that never leaves the device. Phone numbers keep only their last 2 digits. Over [maxBytes] the
 * oldest lines are dropped.
 */
class ToolAuditLog(
    private val file: File,
    private val clock: Clock = Clock.systemUTC(),
    private val maxBytes: Int = MAX_BYTES,
) {
    @Synchronized
    fun record(call: ToolCall, outcome: String) {
        // A dial line hides the number everywhere: in the args and in the outcome ("done: <number>").
        val mask = { text: String -> if (call is ToolCall.Dial) LogRedaction.maskDigits(text) else text }
        val line = "${clock.instant()} | ${call.tool.wire} | ${mask(ToolCallJson.encode(call))} | ${mask(outcome.replace('\n', ' '))}\n"
        file.parentFile?.mkdirs()
        file.appendText(line)
        if (file.length() > maxBytes) {
            val lines = file.readLines()
            var size = 0
            // Keep the newest lines that fit.
            val kept = lines.asReversed().takeWhile { size += it.toByteArray().size + 1; size <= maxBytes }.asReversed()
            file.writeText(kept.joinToString("") { "$it\n" })
        }
    }

    @Synchronized
    fun read(): String = if (file.isFile) file.readText() else ""

    @Synchronized
    fun clear() {
        file.delete()
    }

    companion object {
        const val MAX_BYTES = 256 * 1024
    }
}
