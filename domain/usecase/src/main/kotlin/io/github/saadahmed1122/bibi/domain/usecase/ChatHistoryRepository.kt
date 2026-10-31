package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.Message
import kotlinx.coroutines.flow.Flow

/** The single P1 conversation, stored on the device (SPEC-chat-history.md). */
interface ChatHistoryRepository {
    /** All messages ordered by `createdAt`, then `id`; emits again after every change. */
    fun observeAll(): Flow<List<Message>>

    /** Saves [message] (its `id` is ignored) and returns the new id. */
    suspend fun insert(message: Message): Long

    suspend fun update(id: Long, text: String, status: Message.Status)

    suspend fun deleteAll()
}
