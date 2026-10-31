package io.github.saadahmed1122.bibi.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.saadahmed1122.bibi.domain.model.Message
import io.github.saadahmed1122.bibi.domain.model.Message.Role
import io.github.saadahmed1122.bibi.domain.model.Message.Status
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomChatHistoryRepositoryTest {
    private lateinit var db: BibiDatabase
    private lateinit var repo: RoomChatHistoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BibiDatabase::class.java)
            .build()
        repo = RoomChatHistoryRepository(db.messageDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun mappingRoundTripsEveryRoleAndStatus() {
        var id = 1L
        for (role in Role.entries) for (status in Status.entries) {
            val message = Message(id, role, "t$id", createdAt = id * 10, status)
            val entity = message.toEntity()
            assertEquals(MessageEntity(id, role.name, "t$id", id * 10, status.name), entity)
            assertEquals(message, entity.toMessage())
            id++
        }
    }

    @Test
    fun insertIgnoresGivenIdAndObserveReturnsMessages() = runTest {
        val newId = repo.insert(Message(99, Role.BIBI, "hello", createdAt = 7, Status.STOPPED))

        assertEquals(listOf(Message(newId, Role.BIBI, "hello", 7, Status.STOPPED)), repo.observeAll().first())
    }

    @Test
    fun updateAndDeleteAllGoThroughToStorage() = runTest {
        val id = repo.insert(Message(0, Role.BIBI, "part", createdAt = 1, Status.DONE))

        repo.update(id, "whole", Status.ERROR)
        assertEquals(listOf(Message(id, Role.BIBI, "whole", 1, Status.ERROR)), repo.observeAll().first())

        repo.deleteAll()
        assertEquals(emptyList<Message>(), repo.observeAll().first())
    }
}
