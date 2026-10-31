package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RememberTest {
    @Test
    fun cardNumbersPasswordsAndIdsAreNeverStored() {
        for ((text, reason) in listOf(
            "my card is 4111 1111 1111 1111" to NeverStore.Reason.CARD,
            "visa 4111-1111-1111-1111 exp 12/29" to NeverStore.Reason.CARD,
            "my password is hunter2" to NeverStore.Reason.PASSWORD,
            "wifi passcode: blue-sky-42" to NeverStore.Reason.PASSWORD,
            "the PIN is 4821" to NeverStore.Reason.PASSWORD,
            "my SSN is 123-45-6789" to NeverStore.Reason.ID,
            "CNIC 35202-1234567-1" to NeverStore.Reason.ID,
            "my passport number is AB1234567" to NeverStore.Reason.ID,
        )) assertEquals(text, reason, NeverStore.check(text))
    }

    @Test
    fun ordinaryFactsAndNumbersPass() {
        for (text in listOf(
            "my sister is Sara",
            "my phone number is 0300 1234567", // 11 digits, not a card
            "I was born in 1994 and live at house 12",
            "order 1234 5678 9012 3456 was late", // 16 digits that fail the card checksum
            "I forgot my password reset email", // talks about a password, no value
        )) assertNull(text, NeverStore.check(text))
    }

    @Test
    fun rememberPhrasesGiveTheFactToKeep() {
        for ((text, fact) in listOf(
            "remember that my sister is Sara" to "My sister is Sara",
            "Bibi, please remember my car is parked on level 3." to "My car is parked on level 3",
            "Remember: the gym opens at 6" to "The gym opens at 6",
            "don't forget that mum's birthday is in June" to "Mum's birthday is in June",
            "my name is Saad" to "My name is Saad",
        )) assertEquals(text, fact, RememberPhrase.fact(text))
    }

    @Test
    fun questionsAndStoriesAreNotRememberRequests() {
        for (text in listOf(
            "do you remember my sister?",
            "remember when we talked about cats",
            "remember what I said?",
            "I can't remember my keys",
            "remember",
            "my name is not important, tell me a joke please",
        )) assertNull(text, RememberPhrase.fact(text))
    }

    @Test
    fun rememberSavesAllowedTextAndRefusesTheRest() = runTest {
        val repo = ListMemory()
        val remember = Remember(repo) { 42 }
        assertEquals(Remember.Result.Saved(1), remember("My sister is Sara"))
        assertEquals(Remember.Result.Blocked(NeverStore.Reason.PASSWORD), remember("my password is hunter2"))
        assertEquals(Remember.Result.Empty, remember("   "))
        assertEquals(listOf("My sister is Sara"), repo.items.map { it.text })
        assertEquals(42L, repo.items.single().createdAt)
    }

    /** Just enough of a repository for [Remember]. */
    private class ListMemory : MemoryRepository {
        val items = mutableListOf<MemoryItem>()
        override fun observeAll(): Flow<List<MemoryItem>> = MutableStateFlow(items)
        override suspend fun search(text: String, limit: Int) = emptyList<MemoryItem>()
        override suspend fun pinned() = emptyList<MemoryItem>()
        override suspend fun add(kind: MemoryItem.Kind, text: String, now: Long): Long {
            items += MemoryItem(items.size + 1L, kind, text, false, now, now)
            return items.size.toLong()
        }
        override suspend fun updateText(id: Long, text: String, now: Long) = Unit
        override suspend fun setPinned(id: Long, pinned: Boolean, now: Long) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteUnpinnedOlderThan(cutoff: Long) = 0
        override suspend fun deleteAll() = Unit
        override suspend fun count() = items.size
    }
}
