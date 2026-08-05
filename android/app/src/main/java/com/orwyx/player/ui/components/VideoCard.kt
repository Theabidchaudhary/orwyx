package com.orwyx.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.orwyx.player.core.util.Formatters
import com.orwyx.player.domain.model.LibraryLayout
import com.orwyx.player.domain.model.Video
import com.orwyx.player.domain.model.VideoCardField

/**
 * Library cell: thumbnail with duration/HDR/resolution badges, watch
 * progress, and a metadata line built only from the fields the user enabled
 * in the display settings sheet. Thumbnails decode through Coil's video
 * frame decoder with disk caching.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: Video,
    layout: LibraryLayout,
    fields: Set<String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
) {
    if (layout == LibraryLayout.LIST) {
        VideoListCard(video, fields, selected, selectionMode, onClick, onLongClick, modifier)
    } else {
        VideoGridCard(video, fields, selected, selectionMode, onClick, onLongClick, modifier)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridCard(
    video: Video,
    fields: Set<String>,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .glassCard()
            .let { if (selected) it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else it }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box {
            Thumbnail(video, fields, Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            if (selectionMode) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(6.dp))
        }

        Text(
            text = video.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        val line = metadataLine(video, fields)
        if (line.isNotEmpty()) {
            Text(
                text = line,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
            )
        }
        if (VideoCardField.PATH.key in fields) {
            Text(
                text = video.folderPath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoListCard(
    video: Video,
    fields: Set<String>,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .let { if (selected) it.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)) else it }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
    ) {
        Box {
            Thumbnail(video, fields, Modifier.width(120.dp).aspectRatio(16f / 9f))
            if (selectionMode) SelectionMark(selected, Modifier.align(Alignment.TopStart).padding(4.dp))
        }
        Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val line = metadataLine(video, fields)
            if (line.isNotEmpty()) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (VideoCardField.PATH.key in fields) {
                Text(
                    text = video.folderPath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(video: Video, fields: Set<String>, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (VideoCardField.THUMBNAIL.key in fields) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(video.uri)
                    .memoryCacheKey(video.uri)
                    .diskCacheKey("${video.uri}:${video.dateModifiedMs}")
                    .build(),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Movie,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }

        if (VideoCardField.LENGTH.key in fields) {
            Badge(
                text = Formatters.duration(video.durationMs),
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        ) {
            if (VideoCardField.RESOLUTION.key in fields) video.resolutionClass.badge?.let { Badge(it) }
            video.hdrType.badge?.let { Badge(it, accent = true) }
            if (VideoCardField.FILE_EXTENSION.key in fields) {
                Badge(video.path.substringAfterLast('.', "").uppercase())
            }
        }

        if (video.isFavorite) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF6B81),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(16.dp),
            )
        }

        if (video.isInProgress) {
            LinearProgressIndicator(
                progress = { video.watchedFraction },
                trackColor = Color(0x66FFFFFF),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp),
            )
        }
    }
}

private fun metadataLine(video: Video, fields: Set<String>): String =
    listOfNotNull(
        if (VideoCardField.FRAME_RATE.key in fields) Formatters.frameRate(video.frameRate) else null,
        if (VideoCardField.SIZE.key in fields) Formatters.fileSize(video.sizeBytes) else null,
        if (VideoCardField.DATE.key in fields) Formatters.date(video.dateAddedMs) else null,
        if (VideoCardField.PLAYED_TIME.key in fields && video.lastPlayedAtMs > 0) {
            "Played ${Formatters.date(video.lastPlayedAtMs)}"
        } else {
            null
        },
    ).joinToString(" · ")

/** Checked/unchecked circle shown on every card once selection mode is active. */
@Composable
fun SelectionMark(selected: Boolean, modifier: Modifier = Modifier) {
    Icon(
        if (selected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
        contentDescription = if (selected) "Selected" else "Not selected",
        tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
        modifier = modifier
            .size(22.dp)
            .background(Color(0x66000000), CircleShape)
            .padding(1.dp),
    )
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Surface(
        color = if (accent) MaterialTheme.colorScheme.primary else Color(0xCC15171B),
        contentColor = if (accent) MaterialTheme.colorScheme.onPrimary else Color.White,
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
