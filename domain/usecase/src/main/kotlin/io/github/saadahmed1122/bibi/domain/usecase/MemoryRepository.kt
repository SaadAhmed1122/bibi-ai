package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import kotlinx.coroutines.flow.Flow

/** Bibi's memory, stored on the device (SPEC-memory.md). Single profile in MVP. */
interface MemoryRepository {
    /** Pinned first, then newest first; emits again after every change. */
    fun observeAll(): Flow<List<MemoryItem>>

    /** Keyword search (FTS); [limit] best matches. Blank or symbol-only text finds nothing. */
    suspend fun search(text: String, limit: Int): List<MemoryItem>

    suspend fun pinned(): List<MemoryItem>

    /** Saves a new item and returns its id. */
    suspend fun add(kind: MemoryItem.Kind, text: String, now: Long): Long

    suspend fun updateText(id: Long, text: String, now: Long)

    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    suspend fun delete(id: Long)

    /** Retention: deletes unpinned items last changed before [cutoff]. Returns how many went. */
    suspend fun deleteUnpinnedOlderThan(cutoff: Long): Int

    suspend fun deleteAll()

    suspend fun count(): Int
}
