package io.github.saadahmed1122.bibi.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** A memory row (SPEC-memory.md). [kind] holds the domain enum name; [profileId] is 1 in MVP (FR-101). */
@Entity(tableName = "memory_items", indices = [Index("pinned"), Index("updatedAt")])
data class MemoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long = 1,
    val kind: String,
    val text: String,
    val pinned: Boolean,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Keyword index over [MemoryItemEntity.text]. External content: Room keeps it in sync with
 * triggers, and its rowid is the memory row's id. FTS4, not the PRD's FTS5: Room only generates
 * FTS3/4 (Decision 2026-10-31).
 */
@Fts4(contentEntity = MemoryItemEntity::class)
@Entity(tableName = "memory_items_fts")
data class MemoryItemFts(val text: String)
