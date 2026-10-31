package io.github.saadahmed1122.bibi.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A row of the single P1 conversation. [role] and [status] hold the domain enum names. */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,
    val text: String,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val status: String,
)
