package io.github.saadahmed1122.bibi

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.edit
import io.github.saadahmed1122.bibi.domain.usecase.DownloadStatus
import io.github.saadahmed1122.bibi.domain.usecase.ModelDownloader
import io.github.saadahmed1122.bibi.domain.usecase.ModelSource
import java.io.File
import java.io.InputStream

/**
 * [ModelDownloader] on Android's DownloadManager (SPEC-model-download.md): it resumes after network
 * drops, keeps going when Bibi is closed and shows its own notification. Files land in the app's
 * external files folder and are read back through the system, then copied and checked.
 * One instance per pack: [pack] keeps its saved ids and file names apart ("chat", "router", "voice").
 */
class SystemModelDownloader(context: Context, private val pack: String, private val title: String) : ModelDownloader {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(DownloadManager::class.java)
    private val prefs = appContext.getSharedPreferences("model_download", Context.MODE_PRIVATE)

    override val activeIds: List<Long>
        get() = prefs.getString(pack, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    override fun start(urls: List<String>): List<Long> {
        val ids = urls.mapIndexed { i, url ->
            val file = "$pack-$i.download"
            // DownloadManager never overwrites: a leftover from a killed attempt would get a "-1" name.
            appContext.getExternalFilesDir(null)?.let { File(it, file).delete() }
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(title)
                .setDescription(Uri.parse(url).lastPathSegment)
                .setDestinationInExternalFilesDir(appContext, null, file)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            manager.enqueue(request)
        }
        save(ids)
        return ids
    }

    private fun save(ids: List<Long>) = prefs.edit { if (ids.isEmpty()) remove(pack) else putString(pack, ids.joinToString(",")) }

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
        save(activeIds - id)
    }
}
