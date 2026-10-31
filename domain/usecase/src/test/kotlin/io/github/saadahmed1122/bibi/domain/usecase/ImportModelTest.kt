package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.ModelState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

class ImportModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val bytes = ByteArray(64) { it.toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private class FakeSource(private val data: ByteArray, override val size: Long? = data.size.toLong()) : ModelSource {
        var opened = false
        override fun open(): InputStream {
            opened = true
            return ByteArrayInputStream(data)
        }
    }

    private fun TestScope.importer() = ImportModel(
        dispatcher = StandardTestDispatcher(testScheduler),
        fileName = "m.bin",
        expectedSize = bytes.size.toLong(),
        expectedSha256 = sha,
        bufferSize = 8,
    )

    private fun File.names() = list().orEmpty().toSet()

    @Test
    fun correctBytes_ready_noPartLeft() = runTest {
        val dir = tmp.newFolder()
        val state = importer()(FakeSource(bytes), dir)
        val target = File(dir, "m.bin")
        assertEquals(ModelState.Ready(target.path), state)
        assertTrue(target.readBytes().contentEquals(bytes))
        assertEquals(setOf("m.bin"), dir.names())
    }

    @Test
    fun oneByteChanged_invalidChecksum_fileDeleted() = runTest {
        val dir = tmp.newFolder()
        val bad = bytes.copyOf().also { it[10] = (it[10] + 1).toByte() }
        val state = importer()(FakeSource(bad), dir)
        assertEquals(ModelState.Invalid("checksum"), state)
        assertEquals(emptySet<String>(), dir.names())
    }

    @Test
    fun wrongKnownSize_invalidSize_withoutReading() = runTest {
        val dir = tmp.newFolder()
        val source = FakeSource(bytes + 1)
        val state = importer()(source, dir)
        assertEquals(ModelState.Invalid("size"), state)
        assertFalse(source.opened)
        assertEquals(emptySet<String>(), dir.names())
    }

    @Test
    fun wrongUnknownSize_invalidSize_fileDeleted() = runTest {
        val dir = tmp.newFolder()
        val state = importer()(FakeSource(bytes + 1, size = null), dir)
        assertEquals(ModelState.Invalid("size"), state)
        assertEquals(emptySet<String>(), dir.names())
    }

    @Test
    fun cancelMidCopy_partDeleted() = runTest {
        val dir = tmp.newFolder()
        val import = importer()
        lateinit var job: Job
        var reports = 0
        job = launch {
            import(FakeSource(bytes), dir) { if (++reports == 2) job.cancel() }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertEquals(2, reports)
        assertEquals(emptySet<String>(), dir.names())
    }

    @Test
    fun progress_increases_endsAtOne() = runTest {
        val dir = tmp.newFolder()
        val progress = mutableListOf<Float>()
        importer()(FakeSource(bytes), dir) { progress += it }
        assertEquals(bytes.size / 8, progress.size)
        assertTrue(progress.zipWithNext().all { (a, b) -> b > a })
        assertEquals(1f, progress.last())
    }
}
