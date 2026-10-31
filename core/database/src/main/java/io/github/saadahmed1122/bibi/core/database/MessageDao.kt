package io.github.saadahmed1122.bibi.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY createdAt, id")
    fun observeAll(): Flow<List<MessageEntity>>

    /** Returns the new row id. */
    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("UPDATE messages SET text = :text, status = :status WHERE id = :id")
    suspend fun update(id: Long, text: String, status: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
