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

    /** Ids are 1, 2, 3… in start order; [status] applies to all unless [statuses] names one. */
    private class FakeDownloader(var file: ByteArray) : ModelDownloader {
        override var activeIds: List<Long> = emptyList()
        var status: DownloadStatus? = DownloadStatus.Running(0, null)
        val statuses = mutableMapOf<Long, DownloadStatus?>()
        val files = mutableMapOf<Long, ByteArray>()
        val started = mutableListOf<String>()
        val removed = mutableListOf<Long>()

        override fun start(urls: List<String>): List<Long> {
            val ids = urls.map { started += it; started.size.toLong() }
            activeIds = ids
            return ids
        }

        override fun status(id: Long) = if (id in statuses) statuses[id] else status

        override fun source(id: Long) = (files[id] ?: file).let { bytes ->
            object : ModelSource {
                override val size: Long = bytes.size.toLong()
                override fun open(): InputStream = bytes.inputStream()
            }
        }

        override fun remove(id: Long) {
            removed += id
            activeIds = activeIds - id
        }
    }

    private fun vm(d: ModelDownloader) = ModelDownloadViewModel("https://example.org/m.bin", bytes.size.toLong(), d, repo, scope, pollMs = 500)

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
        assertTrue(d.activeIds.isEmpty())
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
        val d = FakeDownloader(bytes).apply { activeIds = listOf(7); status = DownloadStatus.Running(500, 1000) }
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
        ModelDownloadViewModel("u", 1L, d2, ModelRepository(tmp.root, fileName = "m.bin", expectedSize = bytes.size.toLong()), scope).start()
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

    // --- packs of several files (the voice pack) ---

    private fun pack(d: ModelDownloader, installed: MutableList<List<String>>) = ModelDownloadViewModel(
        urls = listOf("a", "b", "c"),
        sizes = listOf(100, 200, 700),
        downloader = d,
        isReady = { false },
        install = { sources -> installed += sources.map { it.open().readBytes().decodeToString() } },
        scope = scope,
    )

    @Test
    fun packProgressAddsUpItsFiles() {
        val d = FakeDownloader(bytes)
        val vm = pack(d, mutableListOf())
        vm.start()
        d.statuses[1] = DownloadStatus.Done
        d.statuses[2] = DownloadStatus.Running(50, 200)
        d.statuses[3] = DownloadStatus.Running(0, null, waiting = true)
        scope.runCurrent()
        assertEquals(ModelDownloadState.Downloading(150, 1000, waiting = true), vm.state.value)
    }

    @Test
    fun packInstallsAllFilesInUrlOrderOnceEveryOneIsDone() {
        val d = FakeDownloader(bytes).apply { (1L..3L).forEach { files[it] = "file$it".encodeToByteArray() } }
        val installed = mutableListOf<List<String>>()
        val vm = pack(d, installed)
        vm.start()
        d.statuses[1] = DownloadStatus.Done
        d.statuses[2] = DownloadStatus.Done
        scope.runCurrent()
        assertTrue(installed.isEmpty())
        d.statuses[3] = DownloadStatus.Done
        scope.advanceTimeBy(501)
        assertEquals(listOf(listOf("file1", "file2", "file3")), installed)
        assertEquals(listOf(1L, 2L, 3L), d.removed)
    }

    @Test
    fun oneFailedFileStopsThePack() {
        val d = FakeDownloader(bytes)
        val vm = pack(d, mutableListOf())
        vm.start()
        d.statuses[2] = DownloadStatus.Failed(404)
        scope.runCurrent()
        assertEquals(ModelDownloadState.Failed(404), vm.state.value)
        assertEquals(listOf(1L, 2L, 3L), d.removed)
    }
}
