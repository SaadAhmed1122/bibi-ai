package io.github.saadahmed1122.bibi.domain.model

/**
 * Something Bibi remembers (SPEC-memory.md): a fact the user asked it to keep, or a short summary
 * of past chat. Pinned items never expire and always go into the prompt. Times are epoch ms.
 */
data class MemoryItem(
    val id: Long,
    val kind: Kind,
    val text: String,
    val pinned: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
) {
    enum class Kind { FACT, SUMMARY }
}
