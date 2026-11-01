package io.github.saadahmed1122.bibi

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteEverythingTest {
    @Test
    fun everyStepRuns() = runTest {
        val done = mutableListOf<String>()
        DeleteEverything(listOf({ done += "chat" }, { done += "memory" }, { done += "logs" }, { done += "settings" }))()
        assertEquals(listOf("chat", "memory", "logs", "settings"), done)
    }

    @Test
    fun aFailingStepDoesNotStopTheOthers() = runTest {
        val done = mutableListOf<String>()
        val result = runCatching {
            DeleteEverything(listOf({ done += "chat" }, { error("disk") }, { done += "settings" }))()
        }
        assertEquals(listOf("chat", "settings"), done)
        assertTrue(result.isFailure)
    }
}
