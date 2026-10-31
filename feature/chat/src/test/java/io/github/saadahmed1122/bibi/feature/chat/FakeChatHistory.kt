package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.usecase.ChatHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeChatHistory : ChatHistoryRepository {
    val messages = MutableStateFlow<List<Message>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Message>> = messages

    override suspend fun insert(message: Message): Long {
        val id = nextId++
        messages.update { it + message.copy(id = id) }
        return id
    }

    override suspend fun update(id: Long, text: String, status: Message.Status) {
        messages.update { list -> list.map { if (it.id == id) it.copy(text = text, status = status) else it } }
    }

    override suspend fun deleteAll() {
        messages.value = emptyList()
    }
}

/** (role, text, status) of every saved message, for compact asserts. */
fun FakeChatHistory.saved() = messages.value.map { Triple(it.role, it.text, it.status) }
