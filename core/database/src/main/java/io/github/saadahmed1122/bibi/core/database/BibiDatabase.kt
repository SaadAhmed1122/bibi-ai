package io.github.saadahmed1122.bibi.core.database

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Plain (unencrypted) storage: P1 chat history and P2 memory (owner decision 2026-10-31,
 * SPEC-memory.md). Schema changes need a migration and a new committed JSON under
 * `core/database/schemas`. Version 2 adds the memory tables (auto migration, tested).
 */
@Database(
    entities = [MessageEntity::class, MemoryItemEntity::class, MemoryItemFts::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class BibiDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    abstract fun memoryDao(): MemoryDao

    companion object {
        @Volatile private var instance: BibiDatabase? = null

        /** One instance per process, so every repository sees the others' changes. */
        fun get(context: Context): BibiDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, BibiDatabase::class.java, NAME).build().also { instance = it }
        }

        const val NAME = "bibi.db"
    }
}
