package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.MemoryItem

/**
 * Memories in answers (SPEC-memory.md). Prompt tokens cost time on the phone (prefill about
 * 50 estimated tokens/s on the vivo), so both blocks are small and capped in characters
 * (about 3 characters per estimated token, as in ContextBudget).
 */
object MemoryPrompt {
    /** Pinned notes in the system prompt: about 150 estimated tokens. */
    const val PINNED_MAX_CHARS = 450

    /** Matches added to one user turn: about 80 estimated tokens. */
    const val MATCHES_MAX_CHARS = 240

    /** The pinned block for the system prompt, or "" when nothing is pinned. Newest first, whole items only. */
    fun pinned(items: List<MemoryItem>): String {
        val notes = fit(items.filter { it.pinned }.sortedByDescending { it.updatedAt }.map { it.text }, PINNED_MAX_CHARS)
        return if (notes.isEmpty()) "" else "What you know about the user: ${notes.joinToString(" ") { sentence(toSecondPerson(it)) }}"
    }

    /** The text the model sees for one turn: the user's message, after any matching notes. */
    fun turn(userText: String, matches: List<MemoryItem>): String {
        val notes = fit(matches.map { it.text }, MATCHES_MAX_CHARS)
        return if (notes.isEmpty()) userText else "(Remember: ${notes.joinToString(" ") { sentence(toSecondPerson(it)) }})\n$userText"
    }

    private fun sentence(text: String) = text.trim().let { if (it.last() in ".!?") it else "$it." }

    /**
     * The user's own words turned to "you", so the 1B model can't mix up who "my" is: "My sister is
     * Sara" becomes "Your sister is Sara" (tested on the vivo: with "my" it answered "You're called Sara").
     */
    fun toSecondPerson(text: String): String {
        var s = text
        for ((pattern, with) in SWAPS) s = s.replace(Regex("\\b$pattern\\b", RegexOption.IGNORE_CASE), with)
        return s.replaceFirstChar { it.uppercase() }
    }

    private val SWAPS = listOf(
        "I am" to "you are", "I'm" to "you're", "I was" to "you were", "I've" to "you've", "I'll" to "you'll",
        "I'd" to "you'd", "myself" to "yourself", "mine" to "yours", "my" to "your", "me" to "you", "I" to "you",
    )

    /** Whole notes, in order, while they fit in [max] characters; a single longer note is cut. */
    private fun fit(notes: List<String>, max: Int): List<String> {
        val out = mutableListOf<String>()
        var used = 0
        for (n in notes.map { it.trim() }.filter { it.isNotEmpty() }) {
            if (used + n.length + 1 > max) {
                if (out.isEmpty()) out += n.take(max - 1).trimEnd() + "…"
                break
            }
            out += n
            used += n.length + 1
        }
        return out
    }
}

/**
 * Adds up to [limit] memories that match a message to what the model sees. Pinned matches too: on the
 * vivo the 1B model used a note next to the question far more reliably than the same note in the
 * system prompt ("what is my favourite colour?" → "blue" with the pinned note only).
 */
class Recall(private val repository: MemoryRepository, private val limit: Int = 3) {
    suspend operator fun invoke(userText: String): String = MemoryPrompt.turn(userText, repository.search(userText, limit))
}
