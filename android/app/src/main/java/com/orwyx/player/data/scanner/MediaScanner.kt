package com.orwyx.player.data.scanner

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.orwyx.player.core.util.Formatters
import com.orwyx.player.core.util.MediaFormats
import com.orwyx.player.data.db.OrwyxDatabase
import com.orwyx.player.data.db.VideoDao
import com.orwyx.player.data.db.VideoEntity
import com.orwyx.player.data.settings.SettingsRepository
import com.orwyx.player.domain.model.HdrType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val found: Int) : ScanState
    data class Done(val total: Int, val removed: Int) : ScanState
}

/**
 * Library indexer.
 *
 * Two phases keep the UI instant:
 *  1. **Fast pass** — a single MediaStore query (plus SAF trees the user added)
 *     upserts name/size/duration/resolution for every video.
 *  2. **Enrichment pass** — [MetadataExtractor] resolves codec/fps/HDR in small
 *     throttled batches, so battery cost stays negligible and the grid fills in live.
 */
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: OrwyxDatabase,
    private val dao: VideoDao,
    private val settings: SettingsRepository,
    private val metadataExtractor: MetadataExtractor,
) {
    private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
    val state: StateFlow<ScanState> = _state

    private val scanMutex = Mutex()

    /** Limits concurrent MediaExtractor sessions so enrichment never spikes CPU. */
    private val enrichmentSemaphore = Semaphore(2)

    /**
     * [silent] scans — the background ContentObserver signal, the resume-time
     * catch-up — never touch [state], so no progress bar ever appears for them;
     * only an explicit user action (pull-to-refresh, "Rescan library", adding a
     * folder) shows one. This is how MX Player/VLC read as "always up to date":
     * the sync itself is invisible.
     *
     * The stale-mark/upsert/sweep diff runs inside one Room transaction so the
     * "videos" table's Flow observers (the folder grid, search results) only
     * ever see the before/after result — never the intermediate state where
     * every row is briefly marked stale, which used to blank the whole grid
     * for a frame before it repopulated.
     */
    suspend fun scan(silent: Boolean = false) = withContext(Dispatchers.IO) {
        if (scanMutex.isLocked) return@withContext
        scanMutex.withLock {
            if (!silent) _state.value = ScanState.Scanning(0)

            val prefs = settings.settings.first()
            val ignored = prefs.ignoredFolders

            var found = 0
            var removed = 0
            database.withTransaction {
                dao.markAllStale()
                found += scanMediaStore(ignored) {
                    if (!silent) _state.value = ScanState.Scanning(found + it)
                }
                for (tree in prefs.safFolders) {
                    found += scanSafTree(Uri.parse(tree), ignored)
                    if (!silent) _state.value = ScanState.Scanning(found)
                }
                removed = dao.sweepStale()
            }
            if (!silent) _state.value = ScanState.Done(found, removed)
            if (!prefs.hasScannedOnce) settings.setHasScannedOnce(true)

            enrichMetadata()
            if (!silent) _state.value = ScanState.Idle
        }
    }

    private suspend fun scanMediaStore(ignored: Set<String>, onProgress: (Int) -> Unit): Int {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
        )
        var count = 0
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, null,
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            val batch = ArrayList<VideoEntity>(BATCH_SIZE)
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol) ?: continue
                val folder = File(path).parent ?: continue
                if (ignored.any { path.startsWith(it) }) continue
                val name = cursor.getString(nameCol) ?: File(path).name
                if (!MediaFormats.isVideoFile(name)) continue

                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    cursor.getLong(idCol),
                ).toString()

                batch += VideoEntity(
                    uri = uri,
                    path = path,
                    title = Formatters.titleFromFileName(name),
                    folderPath = folder,
                    folderName = File(folder).name,
                    sizeBytes = cursor.getLong(sizeCol),
                    durationMs = cursor.getLong(durCol),
                    width = cursor.getInt(wCol),
                    height = cursor.getInt(hCol),
                    frameRate = 0f,
                    bitrate = 0,
                    videoCodec = null,
                    audioCodec = null,
                    hdrType = HdrType.NONE,
                    metadataResolved = false,
                    dateAddedMs = cursor.getLong(addedCol) * 1000,
                    dateModifiedMs = cursor.getLong(modCol) * 1000,
                )
                if (batch.size >= BATCH_SIZE) {
                    count += upsert(batch)
                    batch.clear()
                    onProgress(count)
                }
            }
            count += upsert(batch)
        }
        return count
    }

    /** Indexes a user-picked SAF tree (covers files MediaStore doesn't see, e.g. SD cards). */
    private suspend fun scanSafTree(treeUri: Uri, ignored: Set<String>): Int {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var count = 0
        val stack = ArrayDeque(listOf(root))
        while (stack.isNotEmpty()) {
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
            val batch = mutableListOf<VideoEntity>()
            for (child in children) {
                val name = child.name ?: continue
                when {
                    child.isDirectory -> if (ignored.none { name == File(it).name }) stack.addLast(child)
                    child.isFile && MediaFormats.isVideoFile(name) -> {
                        val folderLabel = dir.name ?: "Storage"
                        batch += VideoEntity(
                            uri = child.uri.toString(),
                            path = child.uri.path ?: child.uri.toString(),
                            title = Formatters.titleFromFileName(name),
                            folderPath = dir.uri.toString(),
                            folderName = folderLabel,
                            sizeBytes = child.length(),
                            durationMs = 0,
                            width = 0,
                            height = 0,
                            frameRate = 0f,
                            bitrate = 0,
                            videoCodec = null,
                            audioCodec = null,
                            hdrType = HdrType.NONE,
                            metadataResolved = false,
                            dateAddedMs = child.lastModified(),
                            dateModifiedMs = child.lastModified(),
                        )
                    }
                }
            }
            count += upsert(batch)
        }
        return count
    }

    /** Insert new rows; refresh mutable columns on rows that already exist. */
    private suspend fun upsert(batch: List<VideoEntity>): Int {
        if (batch.isEmpty()) return 0
        val inserted = dao.insertAll(batch)
        batch.forEachIndexed { i, entity ->
            if (inserted[i] == -1L) {
                dao.refreshSeen(
                    uri = entity.uri,
                    sizeBytes = entity.sizeBytes,
                    dateModifiedMs = entity.dateModifiedMs,
                    title = entity.title,
                    path = entity.path,
                    folderPath = entity.folderPath,
                    folderName = entity.folderName,
                )
            }
        }
        return batch.size
    }

    /** Second pass: resolve codec/fps/HDR for rows that still need it. */
    private suspend fun enrichMetadata() {
        while (true) {
            val pending = dao.needingMetadata(BATCH_SIZE)
            if (pending.isEmpty()) return
            for (entity in pending) {
                enrichmentSemaphore.withPermit {
                    val meta = metadataExtractor.extract(Uri.parse(entity.uri))
                    dao.applyMetadata(
                        id = entity.id,
                        width = meta?.width?.takeIf { it > 0 } ?: entity.width,
                        height = meta?.height?.takeIf { it > 0 } ?: entity.height,
                        frameRate = meta?.frameRate ?: 0f,
                        bitrate = meta?.bitrate ?: 0,
                        videoCodec = meta?.videoCodec,
                        audioCodec = meta?.audioCodec,
                        hdrType = (meta?.hdrType ?: HdrType.NONE).name,
                        durationMs = meta?.durationMs?.takeIf { it > 0 } ?: entity.durationMs,
                    )
                }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 200
    }
}
