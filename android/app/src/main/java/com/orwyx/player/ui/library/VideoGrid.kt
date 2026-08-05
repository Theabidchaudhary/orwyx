package com.orwyx.player.ui.library

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.orwyx.player.domain.model.LibraryLayout
import com.orwyx.player.domain.model.Video
import com.orwyx.player.ui.components.VideoCard
import com.orwyx.player.ui.player.PlayerActivity

/**
 * Shared paged grid/list used by Home (search results), folder, and vault
 * screens. Layout and visible card fields come from the global, persisted
 * display settings, so they stay in sync everywhere.
 *
 * Long-press selects a video instead of opening a per-item menu; once
 * anything is selected, tapping other cards adds/removes them from the
 * selection instead of opening the player. [SelectionActionsBar] (rendered by
 * the caller, since it belongs in the Scaffold's bottomBar slot) drives the
 * actual move/copy/rename/delete/properties actions.
 */
@Composable
fun VideoGrid(
    items: LazyPagingItems<Video>,
    viewModel: LibraryViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsState()
    val selected by viewModel.selectedVideos.collectAsState()
    val context = LocalContext.current
    val selectionMode = selected.isNotEmpty()

    val onClick: (Video) -> Unit = { video ->
        if (selectionMode) {
            viewModel.toggleVideoSelection(video.id)
        } else {
            context.startActivity(PlayerActivity.intent(context, video))
        }
    }
    val onLongClick: (Video) -> Unit = { video -> viewModel.toggleVideoSelection(video.id) }

    if (settings.libraryLayout == LibraryLayout.LIST) {
        LazyColumn(contentPadding = contentPadding, modifier = modifier.fillMaxSize()) {
            items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                items[index]?.let { video ->
                    VideoCard(
                        video, settings.libraryLayout, settings.videoCardFields,
                        onClick = { onClick(video) },
                        onLongClick = { onLongClick(video) },
                        selected = video.id in selected,
                        selectionMode = selectionMode,
                    )
                }
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            contentPadding = contentPadding,
            modifier = modifier.fillMaxSize(),
        ) {
            gridItems(items) { video ->
                VideoCard(
                    video, settings.libraryLayout, settings.videoCardFields,
                    onClick = { onClick(video) },
                    onLongClick = { onLongClick(video) },
                    selected = video.id in selected,
                    selectionMode = selectionMode,
                )
            }
        }
    }
}

private fun LazyGridScope.gridItems(
    items: LazyPagingItems<Video>,
    itemContent: @Composable (Video) -> Unit,
) {
    items(
        count = items.itemCount,
        key = items.itemKey { it.id },
    ) { index ->
        items[index]?.let { itemContent(it) }
    }
}

/** One place to hook the consent + share event stream from [LibraryViewModel]. */
@Composable
fun LibraryEventHandler(viewModel: LibraryViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<PendingAction?>(null) }
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val action = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK && action != null) {
            viewModel.onConsentGranted(action)
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is LibraryEvent.RequestConsent -> {
                    pending = event.pendingAction
                    consentLauncher.launch(IntentSenderRequest.Builder(event.sender).build())
                }
                is LibraryEvent.LaunchShare -> context.startActivity(event.intent)
                is LibraryEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }
}
