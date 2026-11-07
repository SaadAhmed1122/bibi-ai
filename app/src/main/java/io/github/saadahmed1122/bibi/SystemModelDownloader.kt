package io.github.saadahmed1122.bibi

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import io.github.saadahmed1122.bibi.domain.model.ModelFile
import io.github.saadahmed1122.bibi.domain.usecase.DownloadStatus
import io.github.saadahmed1122.bibi.domain.usecase.ModelDownloader
import io.github.saadahmed1122.bibi.domain.usecase.ModelSource
import java.io.File
import java.io.InputStream

/**
 * [ModelDownloader] on Android's DownloadManager (SPEC-model-download.md): it resumes after network
 * drops, keeps going when Bibi is closed and shows its own notification. The file lands in the
 * app's external files folder and is read back through the system, then copied and checked.
 */
class SystemModelDownloader(context: Context) : ModelDownloader {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val prefs = appContext.getSharedPreferences("model_download", Context.MODE_PRIVATE)

    override val activeId: Long?
        get() = prefs.getLong(KEY_ID, -1L).takeIf { it >= 0 }

    override fun start(url: String): Long {
        // DownloadManager never overwrites: a leftover from a killed attempt would get a "-1" name.
        appContext.getExternalFilesDir(null)?.let { File(it, FILE).delete() }
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(appContext.getString(R.string.model_download_notification))
            .setDescription(ModelFile.NAME)
            .setDestinationInExternalFilesDir(appContext, null, FILE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        return manager.enqueue(request).also { id -> prefs.edit { putLong(KEY_ID, id) } }
    }

    override fun status(id: Long): DownloadStatus? =
        manager.query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 }
            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.Done
                DownloadManager.STATUS_FAILED -> DownloadStatus.Failed(reason)
                DownloadManager.STATUS_PAUSED -> DownloadStatus.Running(done.coerceAtLeast(0), total, waiting = true)
                else -> DownloadStatus.Running(done.coerceAtLeast(0), total)
            }
        }

    override fun source(id: Long): ModelSource = object : ModelSource {
        override val size: Long? by lazy { manager.openDownloadedFile(id).use { it.statSize.takeIf { s -> s >= 0 } } }

        override fun open(): InputStream = ParcelFileDescriptor.AutoCloseInputStream(manager.openDownloadedFile(id))
    }

    override fun remove(id: Long) {
        manager.remove(id)
        if (activeId == id) prefs.edit { remove(KEY_ID) }
    }

    private companion object {
        const val KEY_ID = "id"
        const val FILE = "${ModelFile.NAME}.download"
    }
}
