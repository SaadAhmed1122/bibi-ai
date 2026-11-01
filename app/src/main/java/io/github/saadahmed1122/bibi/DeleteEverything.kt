package io.github.saadahmed1122.bibi

/**
 * "Delete everything" (SPEC-privacy.md, FR-181): chats, memories, the action and crash logs, and the
 * settings, then a fresh conversation. The imported models stay (they hold nothing personal and are
 * removed on the model screen). Each step is a function so this is JVM-tested.
 */
class DeleteEverything(
    private val steps: List<suspend () -> Unit>,
) {
    suspend operator fun invoke() {
        // Every step runs even if one fails, so a partial failure doesn't leave the rest behind.
        var first: Throwable? = null
        for (step in steps) {
            try {
                step()
            } catch (e: Exception) {
                if (first == null) first = e
            }
        }
        first?.let { throw it }
    }
}

/** What the Privacy dashboard shows (SPEC-privacy.md). */
data class StoredData(
    val messages: Int,
    val memories: Int,
    val actionLogBytes: Long,
    val crashLogBytes: Long,
)
