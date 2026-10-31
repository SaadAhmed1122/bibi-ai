package io.github.saadahmed1122.bibi.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Plain (unencrypted) chat history, P1 assumption approved 2026-10-31 (SPEC-chat-history.md).
 * Schema changes need a migration and a new committed JSON under `core/database/schemas`.
 */
@Database(entities = [MessageEntity::class], version = 1, exportSchema = true)
abstract class BibiDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
}
