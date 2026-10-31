package io.github.saadahmed1122.bibi.domain.model

/** One saved chat turn (SPEC-chat-history.md). [createdAt] is epoch milliseconds. */
data class Message(
    val id: Long,
    val role: Role,
    val text: String,
    val createdAt: Long,
    val status: Status,
) {
    enum class Role { USER, BIBI }

    enum class Status { DONE, STOPPED, ERROR }
}
