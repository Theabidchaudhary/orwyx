package com.orwyx.player.ui.library

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.orwyx.player.core.util.Formatters
import com.orwyx.player.domain.model.VideoFolder

/** Replaces the normal header while a selection is active. [extraActions] holds
 * whatever doesn't fit the bottom bar — e.g. a private-folder or hide-folder toggle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    count: Int,
    onClose: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text("$count selected") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, "Cancel selection")
            }
        },
        actions = extraActions,
    )
}

/** Move / Copy / Rename (single item only) / Properties / Delete. */
@Composable
fun SelectionBottomBar(
    showRename: Boolean,
    onMove: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onDelete: () -> Unit,
) {
    BottomAppBar {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionAction(Icons.Filled.DriveFileMove, "Move", onMove)
            SelectionAction(Icons.Filled.ContentCopy, "Copy", onCopy)
            if (showRename) SelectionAction(Icons.Filled.Edit, "Rename", onRename)
            SelectionAction(Icons.Filled.Info, "Properties", onProperties)
            SelectionAction(Icons.Filled.Delete, "Delete", onDelete)
        }
    }
}

@Composable
private fun SelectionAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Icon(icon, label)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private enum class SelectionOp { MOVE, COPY }

/**
 * The bottom bar plus every dialog/picker its buttons open (move/copy
 * destination, rename, delete confirmation, properties) — shared by the home
 * folder grid and any video grid, since [LibraryViewModel]'s bulk actions
 * already resolve a folder selection down to the videos inside it.
 *
 * [singleFolder] is the one selected folder's own aggregate (path, video
 * count, total size) — passed only when exactly one folder is selected, so
 * Properties shows the folder's real metadata instead of a generic count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionActionsBar(
    viewModel: LibraryViewModel,
    selectedCount: Int,
    showRename: Boolean,
    singleFolder: VideoFolder? = null,
) {
    val context = LocalContext.current
    var pendingOp by remember { mutableStateOf<SelectionOp?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showProperties by rememberSaveable { mutableStateOf(false) }

    val destPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: run { pendingOp = null; return@rememberLauncherForActivityResult }
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        when (pendingOp) {
            SelectionOp.MOVE -> viewModel.moveSelection(uri)
            SelectionOp.COPY -> viewModel.copySelection(uri)
            null -> Unit
        }
        pendingOp = null
    }

    SelectionBottomBar(
        showRename = showRename,
        onMove = { pendingOp = SelectionOp.MOVE; destPicker.launch(null) },
        onCopy = { pendingOp = SelectionOp.COPY; destPicker.launch(null) },
        onRename = { showRenameDialog = true },
        onProperties = { showProperties = true },
        onDelete = { showDeleteConfirm = true },
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (selectedCount == 1) "Delete this item?" else "Delete $selectedCount items?") },
            text = { Text("This permanently deletes the selected videos from your device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSelection(); showDeleteConfirm = false }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }

    if (showRenameDialog) {
        var name by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(Unit) { name = viewModel.selectionSummary().single?.title ?: "" }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename video") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { viewModel.renameSelectedVideo(name); showRenameDialog = false }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }

    if (showProperties) {
        var summary by remember { mutableStateOf<SelectionSummary?>(null) }
        LaunchedEffect(Unit) { summary = viewModel.selectionSummary() }
        AlertDialog(
            onDismissRequest = { showProperties = false },
            title = { Text(singleFolder?.name ?: summary?.single?.title ?: "$selectedCount selected") },
            text = {
                Text(
                    when {
                        singleFolder != null -> buildString {
                            appendLine("Path: ${singleFolder.path}")
                            appendLine("Videos: ${singleFolder.videoCount}")
                            appendLine("Total size: ${Formatters.fileSize(singleFolder.totalSizeBytes)}")
                            append("Last added: ${Formatters.date(singleFolder.latestDateAddedMs)}")
                        }
                        summary?.single != null -> buildString {
                            val v = summary!!.single!!
                            appendLine("Path: ${v.path}")
                            appendLine("Size: ${Formatters.fileSize(v.sizeBytes)}")
                            appendLine("Duration: ${Formatters.duration(v.durationMs)}")
                            appendLine("Resolution: ${Formatters.resolution(v.width, v.height)}")
                            v.videoCodec?.let { appendLine("Video codec: $it") }
                            v.audioCodec?.let { appendLine("Audio codec: $it") }
                            Formatters.frameRate(v.frameRate)?.let { appendLine("Frame rate: $it") }
                            v.hdrType.badge?.let { appendLine("HDR: $it") }
                        }
                        summary != null -> "Total size: ${Formatters.fileSize(summary!!.totalSizeBytes)}"
                        else -> "Loading…"
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showProperties = false }) { Text("Close") } },
        )
    }
}
