package io.github.saadahmed1122.bibi.core.database

import android.content.Context
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomChatHistoryRepository(private val dao: MessageDao) : ChatHistoryRepository {
    companion object {
        /** The app's history, stored in `bibi.db`. Keeps Room types out of the callers' classpath. */
        fun create(context: Context): ChatHistoryRepository =
            RoomChatHistoryRepository(BibiDatabase.create(context).messageDao())
    }

    override fun observeAll(): Flow<List<Message>> = dao.observeAll().map { rows -> rows.map { it.toMessage() } }

    override suspend fun insert(message: Message): Long = dao.insert(message.toEntity().copy(id = 0))

    override suspend fun update(id: Long, text: String, status: Message.Status) = dao.update(id, text, status.name)

    override suspend fun deleteAll() = dao.deleteAll()
}

internal fun MessageEntity.toMessage() =
    Message(id, Message.Role.valueOf(role), text, createdAt, Message.Status.valueOf(status))

internal fun Message.toEntity() = MessageEntity(id, role.name, text, createdAt, status.name)
