package io.github.saadahmed1122.bibi.feature.chat

import io.github.saadahmed1122.bibi.domain.model.ModelDownloadState
import io.github.saadahmed1122.bibi.domain.model.ModelState
import io.github.saadahmed1122.bibi.domain.usecase.DownloadStatus
import io.github.saadahmed1122.bibi.domain.usecase.ImportModel
import io.github.saadahmed1122.bibi.domain.usecase.ModelDownloader
import io.github.saadahmed1122.bibi.domain.usecase.ModelRepository
import io.github.saadahmed1122.bibi.domain.usecase.ModelSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.InputStream
import java.security.MessageDigest

class ModelDownloadViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val bytes = ByteArray(1000) { it.toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private val scope = TestScope(StandardTestDispatcher())

    private val repo by lazy {
        ModelRepository(tmp.root, ImportModel(Dispatchers.Unconfined, "m.bin", bytes.size.toLong(), sha, 100), "m.bin", bytes.size.toLong())
    }

    private class FakeDownloader(var file: ByteArray) : ModelDownloader {
        override var activeId: Long? = null
        var status: DownloadStatus? = DownloadStatus.Running(0, null)
        val started = mutableListOf<String>()
        val removed = mutableListOf<Long>()

        override fun start(url: String): Long {
            started += url
            return (started.size.toLong()).also { activeId = it }
        }

        override fun status(id: Long) = status

        override fun source(id: Long) = object : ModelSource {
            override val size: Long = file.size.toLong()
            override fun open(): InputStream = file.inputStream()
        }

        override fun remove(id: Long) {
            removed += id
            if (activeId == id) activeId = null
        }
    }

    private fun vm(d: ModelDownloader) = ModelDownloadViewModel("https://example.org/m.bin", d, repo, scope, pollMs = 500)

    @Test
    fun startEnqueuesTheUrlAndReportsProgress() {
        val d = FakeDownloader(bytes)
        val vm = vm(d)
        vm.start()
        d.status = DownloadStatus.Running(250, 1000)
        scope.runCurrent()
        assertEquals(listOf("https://example.org/m.bin"), d.started)
        assertEquals(ModelDownloadState.Downloading(250, 1000, waiting = false), vm.state.value)
        assertEquals(0.25f, (vm.state.value as ModelDownloadState.Downloading).progress)
    }

    @Test
    fun pausedForNetworkShowsWaiting() {
        val d = FakeDownloader(bytes)
        val vm = vm(d)
        vm.start()
        d.status = DownloadStatus.Running(10, 1000, waiting = true)
        scope.runCurrent()
        assertTrue((vm.state.value as ModelDownloadState.Downloading).waiting)
    }

    @Test
    fun finishedDownloadIsCheckedImportedAndRemoved() {
        val d = FakeDownloader(bytes)
        val vm = vm(d)
        vm.start()
        scope.runCurrent()
        d.status = DownloadStatus.Done
        scope.advanceTimeBy(501)
        assertEquals(ModelState.Ready(tmp.root.resolve("m.bin").path), repo.state.value)
        assertEquals(ModelDownloadState.Idle, vm.state.value)
        assertEquals(listOf(1L), d.removed)
        assertNull(d.activeId)
    }

    @Test
    fun aDamagedDownloadIsRefusedByTheChecksum() {
        val d = FakeDownloader(bytes.copyOf().also { it[5] = 99 })
        val vm = vm(d)
        vm.start()
        d.status = DownloadStatus.Done
        scope.runCurrent()
        assertEquals(ModelState.Invalid("checksum"), repo.state.value)
        assertEquals(listOf(1L), d.removed)
    }

    @Test
    fun serverErrorIsShownAndTheDownloadForgotten() {
        val d = FakeDownloader(bytes)
        val vm = vm(d)
        vm.start()
        d.status = DownloadStatus.Failed(401)
        scope.runCurrent()
        assertEquals(ModelDownloadState.Failed(401), vm.state.value)
        assertEquals(ModelDownloadState.Kind.NeedsLogin, (vm.state.value as ModelDownloadState.Failed).kind)
        assertEquals(listOf(1L), d.removed)
    }

    @Test
    fun aDownloadFromAnEarlierLaunchIsFollowedAgain() {
        val d = FakeDownloader(bytes).apply { activeId = 7; status = DownloadStatus.Running(500, 1000) }
        val vm = vm(d)
        scope.runCurrent()
        assertEquals(ModelDownloadState.Downloading(500, 1000, false), vm.state.value)
        assertTrue(d.started.isEmpty())
    }

    @Test
    fun cancelStopsAndRemoves() {
        val d = FakeDownloader(bytes)
        val vm = vm(d)
        vm.start()
        scope.runCurrent()
        vm.cancel()
        scope.advanceTimeBy(2_000)
        assertEquals(ModelDownloadState.Idle, vm.state.value)
        assertEquals(listOf(1L), d.removed)
        assertEquals(ModelState.Missing, repo.state.value)
    }

    @Test
    fun startTwiceOrWithAReadyModelDoesNothing() {
        val d = FakeDownloader(bytes)
        val vm = vm(d)
        vm.start()
        vm.start()
        assertEquals(1, d.started.size)

        tmp.newFile("m.bin").writeBytes(bytes)
        val d2 = FakeDownloader(bytes)
        ModelDownloadViewModel("u", d2, ModelRepository(tmp.root, fileName = "m.bin", expectedSize = bytes.size.toLong()), scope).start()
        assertTrue(d2.started.isEmpty())
    }

    @Test
    fun unknownIdGoesBackToIdle() {
        val d = FakeDownloader(bytes)
        val vm = vm(d)
        vm.start()
        d.status = null
        scope.runCurrent()
        assertEquals(ModelDownloadState.Idle, vm.state.value)
    }
}
