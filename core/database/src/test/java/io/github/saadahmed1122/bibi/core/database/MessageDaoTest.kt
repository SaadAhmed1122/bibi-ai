package io.github.saadahmed1122.bibi.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageDaoTest {
    private lateinit var db: BibiDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BibiDatabase::class.java)
            .build()
        dao = db.messageDao()
    }

    @After
    fun tearDown() = db.close()

    private fun entity(text: String, createdAt: Long, role: String = "USER", status: String = "DONE") =
        MessageEntity(role = role, text = text, createdAt = createdAt, status = status)

    @Test
    fun insertReturnsIdsAndObserveOrdersByCreatedAtThenId() = runTest {
        val late = dao.insert(entity("late", createdAt = 300))
        val tieA = dao.insert(entity("tieA", createdAt = 100))
        val tieB = dao.insert(entity("tieB", createdAt = 100))
        val mid = dao.insert(entity("mid", createdAt = 200))

        assertEquals(listOf(1L, 2L, 3L, 4L), listOf(late, tieA, tieB, mid))
        assertEquals(listOf("tieA", "tieB", "mid", "late"), dao.observeAll().first().map { it.text })
    }

    @Test
    fun updateChangesTextAndStatusOnly() = runTest {
        val id = dao.insert(entity("partial", createdAt = 5, role = "BIBI", status = "ERROR"))
        dao.insert(entity("other", createdAt = 6))

        dao.update(id, "final", "STOPPED")

        val rows = dao.observeAll().first()
        assertEquals(MessageEntity(id, "BIBI", "final", 5, "STOPPED"), rows[0])
        assertEquals(MessageEntity(2, "USER", "other", 6, "DONE"), rows[1])
    }

    @Test
    fun deleteAllEmptiesTable() = runTest {
        dao.insert(entity("a", createdAt = 1))
        dao.insert(entity("b", createdAt = 2))

        dao.deleteAll()

        assertEquals(emptyList<MessageEntity>(), dao.observeAll().first())
    }

    @Test
    fun observeReEmitsOnInsertUpdateAndDelete() = runTest {
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        backgroundScope.launch(Dispatchers.Default) {
            dao.observeAll().collect { rows -> emissions.send(rows.map { it.text }) }
        }

        assertEquals(emptyList<String>(), emissions.receive())
        val id = dao.insert(entity("hi", createdAt = 1))
        assertEquals(listOf("hi"), emissions.receive())
        dao.update(id, "bye", "DONE")
        assertEquals(listOf("bye"), emissions.receive())
        dao.deleteAll()
        assertEquals(emptyList<String>(), emissions.receive())
    }
}
