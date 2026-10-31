package io.github.saadahmed1122.bibi.core.database

import android.content.Context
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import io.github.saadahmed1122.bibi.domain.usecase.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomMemoryRepository(private val dao: MemoryDao) : MemoryRepository {
    companion object {
        /** The app's memory in `bibi.db`, sharing the process's database instance. */
        fun create(context: Context): MemoryRepository = RoomMemoryRepository(BibiDatabase.get(context).memoryDao())
    }

    override fun observeAll(): Flow<List<MemoryItem>> = dao.observeAll().map { rows -> rows.map { it.toItem() } }

    override suspend fun search(text: String, limit: Int): List<MemoryItem> =
        ftsQuery(text)?.let { q -> dao.search(q, limit).map { it.toItem() } }.orEmpty()

    override suspend fun pinned(): List<MemoryItem> = dao.pinned().map { it.toItem() }

    override suspend fun add(kind: MemoryItem.Kind, text: String, now: Long): Long =
        dao.insert(MemoryItemEntity(kind = kind.name, text = text, pinned = false, createdAt = now, updatedAt = now))

    override suspend fun updateText(id: Long, text: String, now: Long) = dao.updateText(id, text, now)

    override suspend fun setPinned(id: Long, pinned: Boolean, now: Long) = dao.setPinned(id, pinned, now)

    override suspend fun delete(id: Long) = dao.delete(id)

    override suspend fun deleteUnpinnedOlderThan(cutoff: Long): Int = dao.deleteUnpinnedOlderThan(cutoff)

    override suspend fun deleteAll() = dao.deleteAll()

    override suspend fun count(): Int = dao.count()
}

internal fun MemoryItemEntity.toItem() =
    MemoryItem(id, MemoryItem.Kind.valueOf(kind), text, pinned, createdAt, updatedAt)
