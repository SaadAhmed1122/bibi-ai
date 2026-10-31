package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.MemoryItem

/**
 * Things Bibi never stores (PRD §14.4, SPEC-memory.md): card numbers, passwords and PINs, and
 * government ID numbers. Pattern-based, English; it errs toward refusing.
 */
object NeverStore {
    enum class Reason { CARD, PASSWORD, ID }

    private val OPT = setOf(RegexOption.IGNORE_CASE)

    /** 13–19 digits, optionally grouped by spaces or dashes. */
    private val CARD_CANDIDATE = Regex("(?<!\\d)\\d(?:[ -]?\\d){12,18}(?!\\d)")

    /** A secret word followed by a value: "password is x", "PIN: 1234", "passcode blue-sky". */
    private val SECRET = Regex("\\b(password|passwd|pwd|passcode|pin|pin code|login code)\\b\\s*(?:is|=|:)?\\s*\\S+", OPT)

    /** The secret word alone ("password reset email") isn't a value; these words after it aren't either. */
    private val NOT_A_VALUE = setOf("reset", "manager", "for", "to", "of", "and", "or", "please", "again", "change", "email")

    private val IDS = listOf(
        Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"), // US SSN
        Regex("\\b\\d{5}-\\d{7}-\\d\\b"), // Pakistan CNIC
        Regex("\\b(passport|ssn|social security|cnic|national id|id card|driving licen[cs]e|iban)\\b(?:\\s+(?:number|no\\.?|#))?\\s*(?:is|:)?\\s*[A-Z0-9-]{5,}", OPT),
    )

    fun check(text: String): Reason? {
        if (CARD_CANDIDATE.findAll(text).any { luhn(it.value.filter(Char::isDigit)) }) return Reason.CARD
        if (IDS.any { it.containsMatchIn(text) }) return Reason.ID
        SECRET.findAll(text).forEach { m ->
            val value = m.value.substring(m.groups[1]!!.value.length).trim().trimStart('=', ':').trim()
                .removePrefix("is ").trim().split(" ").firstOrNull().orEmpty().lowercase()
            if (value.isNotEmpty() && value !in NOT_A_VALUE) return Reason.PASSWORD
        }
        return null
    }

    private fun luhn(digits: String): Boolean {
        var sum = 0
        digits.reversed().forEachIndexed { i, c ->
            var d = c - '0'
            if (i % 2 == 1) d = (d * 2).let { if (it > 9) it - 9 else it }
            sum += d
        }
        return sum % 10 == 0
    }
}

/** "Remember that …" style requests in a typed message (zero model cost, PRD §14.4). */
object RememberPhrase {
    private val OPT = setOf(RegexOption.IGNORE_CASE)
    private val LEAD = Regex("^(?:(?:hey\\s+)?bibi\\s*,?\\s*)?(?:please\\s+|can you\\s+|could you\\s+)*", OPT)
    private val REMEMBER = Regex("^(?:remember|don'?t forget|do not forget|note)(?:\\s+that|\\s*:)?\\s+(.+)$", OPT)
    private val MY_NAME = Regex("^(my name is\\s+[\\p{L}' -]{1,40})$", OPT)

    /** Words that make "remember …" a question or a story, not a request. */
    private val NOT_A_FACT = Regex("^(when|what|who|how|if|why|where|the time)\\b", OPT)

    /** The fact to keep, tidied (first letter upper case, no trailing period), or null. */
    fun fact(text: String): String? {
        val s = LEAD.replaceFirst(text.trim(), "").trim()
        if (s.endsWith("?")) return null
        val fact = REMEMBER.matchEntire(s)?.groupValues?.get(1)?.trim()?.takeUnless { NOT_A_FACT.containsMatchIn(it) }
            ?: MY_NAME.matchEntire(s.trimEnd('.', '!'))?.groupValues?.get(1)?.takeIf { it.split(" ").size <= 6 }
            ?: return null
        val clean = fact.trimEnd('.', '!', ' ')
        return clean.takeIf { it.length >= 3 }?.replaceFirstChar { it.uppercase() }
    }
}

/** Saves a fact unless [NeverStore] refuses it (SPEC-memory.md). */
class Remember(private val repository: MemoryRepository, private val clock: () -> Long = System::currentTimeMillis) {
    sealed interface Result {
        data class Saved(val id: Long) : Result
        data class Blocked(val reason: NeverStore.Reason) : Result
        data object Empty : Result
    }

    suspend operator fun invoke(text: String): Result {
        val fact = text.trim()
        if (fact.isEmpty()) return Result.Empty
        NeverStore.check(fact)?.let { return Result.Blocked(it) }
        return Result.Saved(repository.add(MemoryItem.Kind.FACT, fact, clock()))
    }
}
