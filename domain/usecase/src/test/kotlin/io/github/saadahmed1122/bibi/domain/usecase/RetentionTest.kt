package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.core.tools.MiniJson
import io.github.saadahmed1122.bibi.domain.model.MemoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RetentionTest {
    @Test
    fun threeMonthsIsTheDefaultAndUnknownNamesFallBack() {
        assertEquals(Retention.THREE_MONTHS, Retention.DEFAULT)
        assertEquals(Retention.THREE_MONTHS, Retention.from(null))
        assertEquals(Retention.THREE_MONTHS, Retention.from("DECADE"))
        assertEquals(Retention.WEEK, Retention.from("WEEK"))
    }

    @Test
    fun retentionUsesTheRightCutoffAndForeverDeletesNothing() = runTest {
        val repo = CutoffRecorder()
        val day = 86_400_000L
        val apply = ApplyRetention(repo) { 100 * day }
        apply(Retention.WEEK)
        assertEquals(93 * day, repo.cutoffs.single())
        assertEquals(0, apply(Retention.FOREVER))
        assertEquals(1, repo.cutoffs.size)
    }

    @Test
    fun exportRoundTripsThroughAStrictParser() {
        val items = listOf(
            MemoryItem(1, MemoryItem.Kind.FACT, "My sister is \"Sara\"\nshe lives in Lahore \\ Pakistan", true, 10, 20),
            MemoryItem(2, MemoryItem.Kind.SUMMARY, "You asked about tea\t☕ and sleep.", false, 30, 40),
        )
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(MemoryExport.toJson(items, exportedAt = 99)) as Map<String, Any?>
        assertEquals("bibimem", json["format"])
        assertEquals(1L, (json["version"] as Number).toLong())
        val back = (json["items"] as List<Map<String, Any?>>).map {
            MemoryItem(
                0,
                MemoryItem.Kind.valueOf(it["kind"] as String),
                it["text"] as String,
                it["pinned"] as Boolean,
                (it["createdAt"] as Number).toLong(),
                (it["updatedAt"] as Number).toLong(),
            )
        }
        assertEquals(items.map { it.copy(id = 0) }, back)
    }

    @Test
    fun anEmptyExportIsValid() {
        @Suppress("UNCHECKED_CAST")
        val json = MiniJson.parse(MemoryExport.toJson(emptyList(), 1)) as Map<String, Any?>
        assertEquals(emptyList<Any>(), json["items"])
    }

    private class CutoffRecorder : MemoryRepository {
        val cutoffs = mutableListOf<Long>()
        override fun observeAll(): Flow<List<MemoryItem>> = MutableStateFlow(emptyList())
        override suspend fun search(text: String, limit: Int) = emptyList<MemoryItem>()
        override suspend fun pinned() = emptyList<MemoryItem>()
        override suspend fun add(kind: MemoryItem.Kind, text: String, now: Long) = 0L
        override suspend fun updateText(id: Long, text: String, now: Long) = Unit
        override suspend fun setPinned(id: Long, pinned: Boolean, now: Long) = Unit
        override suspend fun delete(id: Long) = Unit
        override suspend fun deleteUnpinnedOlderThan(cutoff: Long): Int { cutoffs += cutoff; return 2 }
        override suspend fun deleteAll() = Unit
        override suspend fun count() = 0
    }
}
