package com.orwyx.player.data.db

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.orwyx.player.domain.model.LibraryQuery
import com.orwyx.player.domain.model.SortBy
import com.orwyx.player.domain.model.SortDirection
import com.orwyx.player.domain.model.VideoFilter

/**
 * Translates a [LibraryQuery] into parameterized SQL for Room's @RawQuery.
 *
 * All values are bound as arguments (never string-concatenated), and every
 * ORDER BY column is indexed on [VideoEntity].
 */
object LibraryQueryBuilder {

    private const val RECENT_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

    fun build(query: LibraryQuery, hiddenFolders: Set<String>, nowMs: Long): SupportSQLiteQuery {
        val where = StringBuilder("WHERE presentInLastScan = 1")
        val args = mutableListOf<Any>()

        if (query.includePrivate) {
            where.append(" AND isPrivate = 1")
        } else {
            where.append(" AND isPrivate = 0")
        }

        if (query.folderPath != null) {
            where.append(" AND folderPath = ?")
            args += query.folderPath
        } else if (hiddenFolders.isNotEmpty() && !query.includePrivate) {
            where.append(" AND folderPath NOT IN (${placeholders(hiddenFolders.size)})")
            args.addAll(hiddenFolders)
        }

        if (query.search.isNotBlank()) {
            where.append(" AND title LIKE ? ESCAPE '\\'")
            args += "%${escapeLike(query.search.trim())}%"
        }

        when (query.filter) {
            VideoFilter.ALL -> Unit
            VideoFilter.HDR -> where.append(" AND hdrType != 'NONE'")
            VideoFilter.SDR -> where.append(" AND hdrType = 'NONE'")
            VideoFilter.FAVORITES -> where.append(" AND isFavorite = 1")
            VideoFilter.RECENTLY_ADDED -> {
                where.append(" AND dateAddedMs >= ?")
                args += nowMs - RECENT_WINDOW_MS
            }
            VideoFilter.RECENTLY_PLAYED -> where.append(" AND lastPlayedAtMs > 0")
        }

        val dir = if (query.direction == SortDirection.ASCENDING) "ASC" else "DESC"
        val orderBy = when (query.sortBy) {
            SortBy.TITLE -> "title COLLATE NOCASE $dir"
            SortBy.DATE -> "dateAddedMs $dir"
            SortBy.PLAYED_TIME -> "lastPlayedAtMs $dir"
            // Unwatched (0) < in-progress (1) < finished (2).
            SortBy.STATUS -> """CASE
                WHEN positionMs = 0 THEN 0
                WHEN positionMs < durationMs * 0.98 THEN 1
                ELSE 2 END $dir"""
            SortBy.LENGTH -> "durationMs $dir"
            SortBy.SIZE -> "sizeBytes $dir"
            SortBy.RESOLUTION -> "MIN(widthPx, heightPx) $dir, widthPx $dir"
            SortBy.PATH -> "path COLLATE NOCASE $dir"
            SortBy.FRAME_RATE -> "frameRate $dir"
            // No dedicated container/extension column; codec is the closest proxy for "type".
            SortBy.TYPE -> "videoCodec COLLATE NOCASE $dir"
        }
        // Sorting recently-played first when the filter is recently-played reads better.
        val effectiveOrder = if (query.filter == VideoFilter.RECENTLY_PLAYED &&
            query.sortBy == SortBy.DATE
        ) {
            "lastPlayedAtMs DESC"
        } else {
            orderBy
        }

        return SimpleSQLiteQuery(
            "SELECT * FROM videos $where ORDER BY $effectiveOrder, id $dir",
            args.toTypedArray(),
        )
    }

    private fun placeholders(count: Int) = Array(count) { "?" }.joinToString(",")

    private fun escapeLike(input: String) =
        input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
}
