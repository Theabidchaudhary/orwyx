package com.orwyx.player.data.files

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.orwyx.player.data.db.VideoDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Result of a mutation that may need user consent via the system dialog. */
sealed interface FileOpResult {
    data object Success : FileOpResult
    /** Caller must launch this and retry on RESULT_OK (scoped-storage consent). */
    data class NeedsConsent(val intentSender: IntentSender) : FileOpResult
    data class Failure(val message: String) : FileOpResult
}

/**
 * Scoped-storage-correct delete/rename/share.
 *
 * On Android 11+ MediaStore.createDeleteRequest/createWriteRequest produce a single
 * system consent dialog; on Android 10 the same flow surfaces through
 * [RecoverableSecurityException].
 */
@Singleton
class FileOperations @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: VideoDao,
) {
    suspend fun delete(videoId: Long): FileOpResult = deleteVideos(listOf(videoId))

    /**
     * Batch delete. On API 30+, [MediaStore.createDeleteRequest] accepts every
     * URI at once, so selecting 20 videos and deleting them still surfaces a
     * single system consent dialog instead of one per file.
     */
    suspend fun deleteVideos(videoIds: List<Long>): FileOpResult = withContext(Dispatchers.IO) {
        val entries = videoIds.mapNotNull { id -> dao.byId(id)?.let { id to Uri.parse(it.uri) } }
        if (entries.isEmpty()) return@withContext FileOpResult.Failure("Nothing to delete")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && entries.all { it.second.isMediaStoreUri() }) {
                val pending = MediaStore.createDeleteRequest(context.contentResolver, entries.map { it.second })
                return@withContext FileOpResult.NeedsConsent(pending.intentSender)
            }
            entries.forEach { (id, uri) ->
                context.contentResolver.delete(uri, null, null)
                dao.delete(id)
            }
            FileOpResult.Success
        } catch (e: SecurityException) {
            (e as? RecoverableSecurityException)
                ?.let { FileOpResult.NeedsConsent(it.userAction.actionIntent.intentSender) }
                ?: FileOpResult.Failure(e.message ?: "Permission denied")
        } catch (e: Exception) {
            FileOpResult.Failure(e.message ?: "Delete failed")
        }
    }

    /** Call after the consent dialog returns RESULT_OK to finish a pending delete. */
    suspend fun confirmDeleted(videoIds: List<Long>) = withContext(Dispatchers.IO) {
        videoIds.forEach { dao.delete(it) }
    }

    /** Copies each video's bytes into [destinationTree]; originals are untouched. */
    suspend fun copyVideos(videoIds: List<Long>, destinationTree: Uri): FileOpResult = withContext(Dispatchers.IO) {
        val dest = DocumentFile.fromTreeUri(context, destinationTree)
            ?: return@withContext FileOpResult.Failure("Can't access destination folder")
        runCatching {
            videoIds.forEach { id -> dao.byId(id)?.let { copyOne(it.uri, it.path, dest) } }
        }.fold(
            onSuccess = { FileOpResult.Success },
            onFailure = { FileOpResult.Failure(it.message ?: "Copy failed") },
        )
    }

    /** Copies into [destinationTree], then deletes the originals (may still need delete consent). */
    suspend fun moveVideos(videoIds: List<Long>, destinationTree: Uri): FileOpResult = withContext(Dispatchers.IO) {
        val dest = DocumentFile.fromTreeUri(context, destinationTree)
            ?: return@withContext FileOpResult.Failure("Can't access destination folder")
        val copyFailure = runCatching {
            videoIds.forEach { id -> dao.byId(id)?.let { copyOne(it.uri, it.path, dest) } }
        }.exceptionOrNull()
        if (copyFailure != null) return@withContext FileOpResult.Failure(copyFailure.message ?: "Move failed")
        deleteVideos(videoIds)
    }

    private fun copyOne(sourceUri: String, sourcePath: String, dest: DocumentFile) {
        val name = File(sourcePath).name
        val extension = name.substringAfterLast('.', "")
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "video/*"
        val target = dest.createFile(mime, name) ?: error("Couldn't create $name in destination")
        val input = context.contentResolver.openInputStream(Uri.parse(sourceUri)) ?: error("Couldn't read $name")
        input.use { source ->
            val output = context.contentResolver.openOutputStream(target.uri) ?: error("Couldn't write $name")
            output.use { source.copyTo(it) }
        }
    }

    suspend fun rename(videoId: Long, newTitle: String): FileOpResult = withContext(Dispatchers.IO) {
        val video = dao.byId(videoId) ?: return@withContext FileOpResult.Failure("Video not found")
        val sanitized = newTitle.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
        if (sanitized.isEmpty()) return@withContext FileOpResult.Failure("Name cannot be empty")
        val extension = video.path.substringAfterLast('.', "")
        val newFileName = if (extension.isEmpty()) sanitized else "$sanitized.$extension"
        val uri = Uri.parse(video.uri)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri.isMediaStoreUri()) {
                // Ask for write consent up front; the actual update happens in confirmRename.
                val pending = MediaStore.createWriteRequest(context.contentResolver, listOf(uri))
                return@withContext FileOpResult.NeedsConsent(pending.intentSender)
            }
            applyRename(videoId, uri, sanitized, newFileName, video.path)
        } catch (e: SecurityException) {
            (e as? RecoverableSecurityException)
                ?.let { FileOpResult.NeedsConsent(it.userAction.actionIntent.intentSender) }
                ?: FileOpResult.Failure(e.message ?: "Permission denied")
        } catch (e: Exception) {
            FileOpResult.Failure(e.message ?: "Rename failed")
        }
    }

    /** Completes a rename after write consent was granted. */
    suspend fun confirmRename(videoId: Long, newTitle: String): FileOpResult =
        withContext(Dispatchers.IO) {
            val video = dao.byId(videoId) ?: return@withContext FileOpResult.Failure("Video not found")
            val sanitized = newTitle.trim().replace(Regex("[/\\\\:*?\"<>|]"), "_")
            val extension = video.path.substringAfterLast('.', "")
            val newFileName = if (extension.isEmpty()) sanitized else "$sanitized.$extension"
            runCatching {
                applyRename(videoId, Uri.parse(video.uri), sanitized, newFileName, video.path)
            }.getOrElse { FileOpResult.Failure(it.message ?: "Rename failed") }
        }

    private suspend fun applyRename(
        videoId: Long,
        uri: Uri,
        newTitle: String,
        newFileName: String,
        oldPath: String,
    ): FileOpResult {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, newFileName)
        }
        val updated = context.contentResolver.update(uri, values, null, null)
        if (updated <= 0) return FileOpResult.Failure("Rename rejected")
        val newPath = File(File(oldPath).parentFile, newFileName).absolutePath
        dao.applyRename(videoId, newTitle, newPath)
        return FileOpResult.Success
    }

    /** Share sheet for one video via its content URI (no file copying). */
    fun shareIntent(videoUri: String): Intent =
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "video/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(videoUri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        )

    private fun Uri.isMediaStoreUri() = authority == MediaStore.AUTHORITY
}
