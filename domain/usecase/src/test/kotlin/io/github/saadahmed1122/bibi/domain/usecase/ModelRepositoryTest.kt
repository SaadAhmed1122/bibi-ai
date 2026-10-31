package io.github.saadahmed1122.bibi.domain.usecase

import io.github.saadahmed1122.bibi.domain.model.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

class ModelRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(1000) { it.toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun importer() = ImportModel(Dispatchers.Unconfined, "m.bin", bytes.size.toLong(), sha, bufferSize = 100)

    private fun repo() = ModelRepository(tmp.root, importer(), "m.bin", bytes.size.toLong())

    private fun source(data: ByteArray) = object : ModelSource {
        override val size: Long = data.size.toLong()
        override fun open(): InputStream = data.inputStream()
    }

    @Test
    fun startsMissingWithoutAFile() {
        assertEquals(ModelState.Missing, repo().state.value)
    }

    @Test
    fun aValidFileOnRestartIsReadyWithoutHashing() {
        // Right size, wrong bytes: Ready proves startup only checks the size.
        tmp.newFile("m.bin").writeBytes(ByteArray(bytes.size))
        assertEquals(ModelState.Ready(tmp.root.resolve("m.bin").path), repo().state.value)
    }

    @Test
    fun aFileWithTheWrongSizeOnRestartIsInvalid() {
        tmp.newFile("m.bin").writeBytes(ByteArray(10))
        assertEquals(ModelState.Invalid("size"), repo().state.value)
    }

    @Test
    fun importThenRemove() = runTest {
        val r = repo()
        r.import(source(bytes))
        assertEquals(ModelState.Ready(tmp.root.resolve("m.bin").path), r.state.value)

        r.remove()
        assertEquals(ModelState.Missing, r.state.value)
        assertFalse(tmp.root.resolve("m.bin").exists())
    }

    @Test
    fun aDamagedFileIsRefused() = runTest {
        val r = repo()
        r.import(source(bytes.copyOf().also { it[5] = 99 }))
        assertEquals(ModelState.Invalid("checksum"), r.state.value)
    }

    @Test
    fun aReadErrorIsReported() = runTest {
        val r = repo()
        r.import(object : ModelSource {
            override val size: Long? = null
            override fun open(): InputStream = throw IOException("gone")
        })
        assertEquals(ModelState.Invalid("read"), r.state.value)
    }
}
