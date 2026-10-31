package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.usecase.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory [MemoryRepository]; search is a plain case-insensitive word match. */
class FakeMemory : MemoryRepository {
    private val rows = MutableStateFlow<List<MemoryItem>>(emptyList())
    val items: List<MemoryItem> get() = rows.value

    override fun observeAll(): StateFlow<List<MemoryItem>> = rows
    override suspend fun search(text: String, limit: Int): List<MemoryItem> {
        val words = Regex("[\\p{L}\\p{N}]{2,}").findAll(text.lowercase()).map { it.value }.toSet()
        return rows.value.filter { item -> words.any { item.text.lowercase().contains(it) } }.take(limit)
    }
    override suspend fun pinned() = rows.value.filter { it.pinned }
    override suspend fun add(kind: MemoryItem.Kind, text: String, now: Long): Long {
        val id = (rows.value.maxOfOrNull { it.id } ?: 0) + 1
        rows.value = rows.value + MemoryItem(id, kind, text, false, now, now)
        return id
    }
    override suspend fun updateText(id: Long, text: String, now: Long) = edit(id) { it.copy(text = text, updatedAt = now) }
    override suspend fun setPinned(id: Long, pinned: Boolean, now: Long) = edit(id) { it.copy(pinned = pinned, updatedAt = now) }
    override suspend fun delete(id: Long) { rows.value = rows.value.filterNot { it.id == id } }
    override suspend fun deleteUnpinnedOlderThan(cutoff: Long): Int {
        val before = rows.value.size
        rows.value = rows.value.filterNot { !it.pinned && it.updatedAt < cutoff }
        return before - rows.value.size
    }
    override suspend fun deleteAll() { rows.value = emptyList() }
    override suspend fun count() = rows.value.size

    private fun edit(id: Long, change: (MemoryItem) -> MemoryItem) {
        rows.value = rows.value.map { if (it.id == id) change(it) else it }
    }
}
