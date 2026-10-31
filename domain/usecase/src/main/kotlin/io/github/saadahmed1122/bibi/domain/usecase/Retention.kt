package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.MemoryItem

/** How long unpinned memories are kept (FR-093, SPEC-memory.md). Pinned items never expire. */
enum class Retention(val days: Int?) {
    WEEK(7),
    MONTH(30),
    THREE_MONTHS(90),
    YEAR(365),
    FOREVER(null),
    ;

    companion object {
        val DEFAULT = THREE_MONTHS

        /** A saved name back to a value; unknown or missing means the default. */
        fun from(name: String?): Retention = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** Deletes unpinned memories older than the retention period; returns how many went. */
class ApplyRetention(private val repository: MemoryRepository, private val clock: () -> Long = System::currentTimeMillis) {
    suspend operator fun invoke(retention: Retention): Int {
        val days = retention.days ?: return 0
        return repository.deleteUnpinnedOlderThan(clock() - days * DAY_MS)
    }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

/**
 * The readable export, `bibimem.json` (PRD §14.7, SPEC-memory.md): every memory with its kind, text,
 * pin and times. Plain JSON, not encrypted (encrypted export is out of P2 scope).
 */
object MemoryExport {
    const val FORMAT = "bibimem"
    const val VERSION = 1

    fun toJson(items: List<MemoryItem>, exportedAt: Long): String = buildString {
        append("{\n  \"format\": \"$FORMAT\",\n  \"version\": $VERSION,\n  \"exportedAt\": $exportedAt,\n  \"items\": [")
        items.forEachIndexed { i, it ->
            append(if (i == 0) "\n" else ",\n")
            append("    {\"kind\": \"${it.kind.name}\", \"text\": ${quote(it.text)}, \"pinned\": ${it.pinned}, ")
            append("\"createdAt\": ${it.createdAt}, \"updatedAt\": ${it.updatedAt}}")
        }
        append(if (items.isEmpty()) "]\n}\n" else "\n  ]\n}\n")
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
    }
}
