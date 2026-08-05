package com.orwyx.player.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.orwyx.player.ui.components.DisplayMode
import com.orwyx.player.ui.components.DisplaySettingsSheet
import com.orwyx.player.ui.components.FilterChipRow
import java.io.File

/**
 * All videos inside one folder: the same quick filters, sort/view sheet, and
 * card actions as everywhere else. Sort/layout/fields are global — changing
 * them here changes them for every folder and the home dashboard too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderVideosScreen(
    folderPath: String,
    onBack: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val snackbar = remember { SnackbarHostState() }
    LibraryEventHandler(viewModel, snackbar)

    LaunchedEffect(folderPath) {
        viewModel.setFolder(folderPath)
    }
    val query by viewModel.query.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val videos = viewModel.videos.collectAsLazyPagingItems()
    var showDisplaySettings by rememberSaveable { mutableStateOf(false) }
    val selected by viewModel.selectedVideos.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (selected.isNotEmpty()) {
                SelectionTopBar(count = selected.size, onClose = viewModel::clearSelection)
            } else {
                TopAppBar(
                    title = { Text(File(folderPath).name) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDisplaySettings = true }) {
                            Icon(Icons.Filled.Tune, "Sort & view")
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                SelectionActionsBar(viewModel = viewModel, selectedCount = selected.size, showRename = selected.size == 1)
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            FilterChipRow(
                selected = query.filter,
                onSelect = viewModel::setFilter,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            VideoGrid(
                items = videos,
                viewModel = viewModel,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }

    if (showDisplaySettings) {
        DisplaySettingsSheet(
            mode = DisplayMode.VIDEOS,
            layout = settings.libraryLayout,
            sortBy = settings.librarySortBy,
            direction = settings.libraryDirection,
            enabledFields = settings.videoCardFields,
            onDismiss = { showDisplaySettings = false },
            onLayout = viewModel::setLayout,
            onSort = viewModel::setSortBy,
            onDirection = viewModel::setDirection,
            onToggleField = viewModel::toggleField,
        )
    }
}
