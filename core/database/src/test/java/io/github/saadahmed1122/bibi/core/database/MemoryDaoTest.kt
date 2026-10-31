package io.github.saadahmed1122.bibi.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.saadahmed1122.bibi.domain.model.MemoryItem.Kind.FACT
import io.github.saadahmed1122.bibi.domain.model.MemoryItem.Kind.SUMMARY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryDaoTest {
    private lateinit var db: BibiDatabase
    private lateinit var repo: RoomMemoryRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), BibiDatabase::class.java).build()
        repo = RoomMemoryRepository(db.memoryDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun searchFindsWordsAndPrefixes() = runTest {
        repo.add(FACT, "My sister is called Sara", now = 1)
        repo.add(FACT, "I like green tea in the morning", now = 2)
        repo.add(SUMMARY, "We talked about a trip to Lahore", now = 3)
        assertEquals(listOf("My sister is called Sara"), repo.search("what's my sister's name?", 3).map { it.text })
        assertEquals(listOf("We talked about a trip to Lahore"), repo.search("trip", 3).map { it.text })
        // Prefix: "lah" finds "Lahore".
        assertEquals(listOf("We talked about a trip to Lahore"), repo.search("lah", 3).map { it.text })
        assertEquals(listOf("I like green tea in the morning"), repo.search("TEA", 3).map { it.text })
    }

    @Test
    fun searchFollowsEditsAndDeletes() = runTest {
        val id = repo.add(FACT, "My cat is Milo", now = 1)
        repo.updateText(id, "My cat is Luna", now = 2)
        assertTrue(repo.search("Milo", 3).isEmpty())
        assertEquals(1, repo.search("Luna", 3).size)
        repo.delete(id)
        assertTrue(repo.search("Luna", 3).isEmpty())
    }

    @Test
    fun symbolsAndOperatorsCannotBreakTheQuery() = runTest {
        repo.add(FACT, "Parking is on level B2", now = 1)
        for (q in listOf("\"", "*", "AND OR NOT", "level\" OR \"x", "-level", "NEAR(level b2)", "", "?!")) repo.search(q, 3)
        assertEquals(1, repo.search("\"level\"", 3).size)
        assertNull(ftsQuery("?! *"))
        assertEquals("and* OR or*", ftsQuery("AND OR a"))
    }

    @Test
    fun pinnedComeFirstAndAreListedApart() = runTest {
        val a = repo.add(FACT, "old fact", now = 1)
        repo.add(FACT, "new fact", now = 5)
        repo.setPinned(a, true, now = 2)
        assertEquals(listOf("old fact", "new fact"), repo.observeAll().first().map { it.text })
        assertEquals(listOf("old fact"), repo.pinned().map { it.text })
    }

    @Test
    fun retentionKeepsPinnedAndTheBoundaryDay() = runTest {
        val day = 86_400_000L
        val cutoff = 100 * day
        repo.add(FACT, "expired", now = cutoff - 1)
        repo.add(FACT, "exactly at the cutoff", now = cutoff)
        repo.add(FACT, "recent", now = cutoff + day)
        val pinned = repo.add(FACT, "old but pinned", now = 1)
        repo.setPinned(pinned, true, now = 1)
        assertEquals(1, repo.deleteUnpinnedOlderThan(cutoff))
        assertEquals(
            setOf("exactly at the cutoff", "recent", "old but pinned"),
            repo.observeAll().first().map { it.text }.toSet(),
        )
        assertTrue(repo.search("expired", 3).isEmpty())
    }

    @Test
    fun deleteAllEmptiesTheIndexToo() = runTest {
        repo.add(FACT, "remember the blue door", now = 1)
        repo.deleteAll()
        assertEquals(0, repo.count())
        assertTrue(repo.search("blue", 3).isEmpty())
    }
}
