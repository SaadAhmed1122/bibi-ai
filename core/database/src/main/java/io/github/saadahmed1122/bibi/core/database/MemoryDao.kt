package io.github.saadahmed1122.bibi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY pinned DESC, updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<MemoryItemEntity>>

    /** [match] is an FTS query built by [ftsQuery]; best matches first. */
    @Query(
        """
        SELECT memory_items.* FROM memory_items
        JOIN memory_items_fts ON memory_items.id = memory_items_fts.rowid
        WHERE memory_items_fts MATCH :match
        ORDER BY memory_items.pinned DESC, memory_items.updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(match: String, limit: Int): List<MemoryItemEntity>

    @Query("SELECT * FROM memory_items WHERE pinned = 1 ORDER BY updatedAt DESC")
    suspend fun pinned(): List<MemoryItemEntity>

    @Insert
    suspend fun insert(item: MemoryItemEntity): Long

    @Query("UPDATE memory_items SET text = :text, updatedAt = :now WHERE id = :id")
    suspend fun updateText(id: Long, text: String, now: Long)

    @Query("UPDATE memory_items SET pinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean, now: Long)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM memory_items WHERE pinned = 0 AND updatedAt < :cutoff")
    suspend fun deleteUnpinnedOlderThan(cutoff: Long): Int

    @Query("DELETE FROM memory_items")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM memory_items")
    suspend fun count(): Int
}

/**
 * Turns free text into a safe FTS MATCH query: lowercase words of 2+ letters or digits, each with a
 * prefix star, joined by OR. Only letters and digits survive, and FTS operators (AND, OR, NOT,
 * NEAR) count only in uppercase, so nothing the user types can change the query. Null when
 * nothing searchable is left.
 */
fun ftsQuery(text: String): String? {
    val words = Regex("[\\p{L}\\p{N}]{2,}").findAll(text.lowercase()).map { it.value }.distinct().take(MAX_TERMS).toList()
    return if (words.isEmpty()) null else words.joinToString(" OR ") { "$it*" }
}

private const val MAX_TERMS = 12
