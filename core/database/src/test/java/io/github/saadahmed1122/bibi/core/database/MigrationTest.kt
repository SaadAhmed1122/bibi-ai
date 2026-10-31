package io.github.saadahmed1122.bibi.core.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Version 1 → 2 on a real file: the P1 database is built from the committed `1.json` SQL, then
 * opened by the current Room database, which runs the auto migration and validates the schema
 * against `2.json`. (MigrationTestHelper's Android constructor needs instrumentation, so this
 * builds version 1 by hand.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file: File = context.getDatabasePath("migration-test.db")

    @After
    fun tearDown() {
        context.deleteDatabase(file.name)
    }

    private fun createVersion1(): Unit = file.also { it.parentFile?.mkdirs() }.let { SQLiteDatabase.openOrCreateDatabase(it, null) }.use { db ->
        // From core/database/schemas/…/1.json, unchanged.
        db.execSQL("CREATE TABLE IF NOT EXISTS `messages` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `role` TEXT NOT NULL, `text` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `status` TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '523530ec6aff66459b8d2c7311151c32')")
        db.execSQL("INSERT INTO messages (role, text, createdAt, status) VALUES ('USER', 'Hi Bibi', 1, 'DONE')")
        db.execSQL("INSERT INTO messages (role, text, createdAt, status) VALUES ('BIBI', 'Hi! How are you?', 2, 'DONE')")
        db.execSQL("INSERT INTO messages (role, text, createdAt, status) VALUES ('BIBI', 'Once upon', 3, 'STOPPED')")
        db.version = 1
    }

    @Test
    fun migrationKeepsEveryP1MessageAndAddsWorkingMemory() = runTest {
        createVersion1()
        val db = Room.databaseBuilder(context, BibiDatabase::class.java, file.path).build()
        try {
            val messages = RoomChatHistoryRepository(db.messageDao()).observeAll().first()
            assertEquals(listOf("Hi Bibi", "Hi! How are you?", "Once upon"), messages.map { it.text })
            assertEquals("STOPPED", messages.last().status.name)

            // The FTS sync triggers exist after the migration, not only on a fresh install.
            val memory = RoomMemoryRepository(db.memoryDao())
            memory.add(MemoryItem.Kind.FACT, "My sister is called Sara", now = 10)
            assertEquals(1, memory.search("sister", 3).size)
            assertEquals(2, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }
}
