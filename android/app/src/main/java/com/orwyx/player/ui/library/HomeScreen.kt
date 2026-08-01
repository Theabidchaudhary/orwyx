package com.orwyx.player.ui.library

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.compose.collectAsLazyPagingItems
import com.orwyx.player.core.util.Formatters
import com.orwyx.player.data.scanner.ScanState
import com.orwyx.player.domain.model.LibraryLayout
import com.orwyx.player.domain.model.VideoFolder
import com.orwyx.player.ui.components.DisplayMode
import com.orwyx.player.ui.components.DisplaySettingsSheet
import com.orwyx.player.ui.components.EmptyState
import com.orwyx.player.ui.components.FolderCard
import com.orwyx.player.ui.player.PlayerActivity

private val MEDIA_PERMISSION =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Library home: folders only — there is no separate "all videos" tab.
 * Typing in search temporarily swaps the folder grid for matching videos
 * across the whole library. The library is scanned once on first run; after
 * that, [com.orwyx.player.data.scanner.MediaChangeObserver] keeps it current
 * in the background, so opening the app never triggers a rescan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenFolder: (String) -> Unit,
    onOpenVault: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, MEDIA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.scanIfNeeded()
    }
    val safLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        viewModel.addSafFolder(uri.toString())
    }

    LaunchedEffect(hasPermission) { if (hasPermission) viewModel.scanIfNeeded() }
    LibraryEventHandler(viewModel, snackbar)

    // The background MediaChangeObserver only runs while the process is alive;
    // Android routinely kills backgrounded apps, so anything added/removed while
    // this app wasn't running would otherwise sit stale until a manual rescan.
    // Rescanning every time the app returns to the foreground closes that gap —
    // MediaScanner.scan() is a cheap bulk diff, safe to run this often.
    val currentHasPermission = rememberUpdatedState(hasPermission)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && currentHasPermission.value) {
                viewModel.silentRescan()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val query by viewModel.query.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val continueWatching by viewModel.continueWatching.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val videos = viewModel.videos.collectAsLazyPagingItems()

    var searching by rememberSaveable { mutableStateOf(false) }
    var showDisplaySettings by rememberSaveable { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }
    var folderActionsFor by remember { mutableStateOf<VideoFolder?>(null) }
    val resumeVideo = continueWatching.firstOrNull()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        OutlinedTextField(
                            value = query.search,
                            onValueChange = viewModel::setSearch,
                            placeholder = { Text("Search your videos") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            "OX Player",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDisplaySettings = true }) {
                        Icon(Icons.Filled.Tune, "Sort & view")
                    }
                    IconButton(onClick = {
                        if (searching) viewModel.setSearch("")
                        searching = !searching
                    }) { Icon(Icons.Filled.Search, "Search") }
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, "More")
                        }
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text("Add folder") },
                                leadingIcon = { Icon(Icons.Filled.CreateNewFolder, null) },
                                onClick = { showOverflow = false; safLauncher.launch(null) },
                            )
                            DropdownMenuItem(
                                text = { Text("Rescan library") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                onClick = { showOverflow = false; viewModel.rescan() },
                            )
                            DropdownMenuItem(
                                text = { Text("Private folder") },
                                leadingIcon = { Icon(Icons.Filled.Lock, null) },
                                onClick = { showOverflow = false; onOpenVault() },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { showOverflow = false; onOpenSettings() },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = resumeVideo != null && !searching,
                enter = scaleIn(),
                exit = scaleOut(),
            ) {
                if (resumeVideo != null) {
                    FloatingActionButton(
                        onClick = { context.startActivity(PlayerActivity.intent(context, resumeVideo)) },
                    ) {
                        Icon(Icons.Filled.PlayArrow, "Resume ${resumeVideo.title}")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (!hasPermission) {
                EmptyState(
                    title = "No videos yet",
                    body = "Grant media access or add a folder to build your library.",
                    actionLabel = "Grant access",
                    onAction = { permissionLauncher.launch(MEDIA_PERMISSION) },
                )
                return@Column
            }

            (scanState as? ScanState.Scanning)?.let {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            if (query.search.isNotBlank()) {
                if (videos.itemCount == 0 && scanState is ScanState.Idle) {
                    EmptyState(title = "No matches", body = "No videos match \"${query.search}\".")
                } else {
                    VideoGrid(
                        items = videos,
                        viewModel = viewModel,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                return@Column
            }

            if (folders.isEmpty()) {
                EmptyState(
                    title = "No folders yet",
                    body = "Videos will appear here automatically once found.",
                )
            } else if (settings.libraryLayout == LibraryLayout.LIST) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(folders, key = { it.path }) { folder ->
                        FolderCard(
                            folder = folder,
                            layout = LibraryLayout.LIST,
                            onClick = { onOpenFolder(folder.path) },
                            onLongClick = { folderActionsFor = folder },
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 168.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    gridItems(folders, key = { it.path }) { folder ->
                        FolderCard(
                            folder = folder,
                            layout = LibraryLayout.GRID,
                            onClick = { onOpenFolder(folder.path) },
                            onLongClick = { folderActionsFor = folder },
                        )
                    }
                }
            }
        }
    }

    if (showDisplaySettings) {
        DisplaySettingsSheet(
            mode = DisplayMode.FOLDERS,
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

    folderActionsFor?.let { folder ->
        FolderActionsSheet(
            folder = folder,
            onDismiss = { folderActionsFor = null },
            onOpen = { onOpenFolder(folder.path) },
            onHide = { viewModel.hideFolder(folder.path) },
        )
    }
}

/** Long-press actions for a folder: open, properties (path lives only here), hide. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderActionsSheet(
    folder: VideoFolder,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onHide: () -> Unit,
) {
    var showProperties by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        ListItem(
            headlineContent = { Text(folder.name, style = MaterialTheme.typography.titleMedium) },
            supportingContent = { Text("${folder.videoCount} videos") },
        )
        ListItem(
            headlineContent = { Text("Open") },
            modifier = Modifier.fillMaxWidth().clickable { onOpen(); onDismiss() },
        )
        ListItem(
            headlineContent = { Text("Properties") },
            modifier = Modifier.fillMaxWidth().clickable { showProperties = true },
        )
        ListItem(
            headlineContent = { Text("Hide folder") },
            modifier = Modifier.fillMaxWidth().clickable { onHide(); onDismiss() },
        )
    }

    if (showProperties) {
        AlertDialog(
            onDismissRequest = { showProperties = false },
            title = { Text(folder.name) },
            text = {
                Text(
                    buildString {
                        appendLine("Path: ${folder.path}")
                        appendLine("Videos: ${folder.videoCount}")
                        appendLine("Total size: ${Formatters.fileSize(folder.totalSizeBytes)}")
                        append("Last added: ${Formatters.date(folder.latestDateAddedMs)}")
                    },
                )
            },
            confirmButton = { TextButton(onClick = { showProperties = false }) { Text("Close") } },
        )
    }
}
