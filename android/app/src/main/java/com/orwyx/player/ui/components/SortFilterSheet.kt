package com.orwyx.player.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.orwyx.player.domain.model.LibraryLayout
import com.orwyx.player.domain.model.SortBy
import com.orwyx.player.domain.model.SortDirection
import com.orwyx.player.domain.model.VideoCardField
import com.orwyx.player.domain.model.VideoFilter

/** Quick filter chip row shown above a folder's video grid. */
@Composable
fun FilterChipRow(
    selected: VideoFilter,
    onSelect: (VideoFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        VideoFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

/** Which listing the display-settings sheet is configuring. */
enum class DisplayMode { FOLDERS, VIDEOS }

/**
 * The single sort/view sheet shared by the folder dashboard and every
 * folder's video list. Every choice here is global and persisted — there is
 * no per-folder override, matching MX Player's unified display settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplaySettingsSheet(
    mode: DisplayMode,
    layout: LibraryLayout,
    sortBy: SortBy,
    direction: SortDirection,
    enabledFields: Set<String>,
    onDismiss: () -> Unit,
    onLayout: (LibraryLayout) -> Unit,
    onSort: (SortBy) -> Unit,
    onDirection: (SortDirection) -> Unit,
    onToggleField: (String) -> Unit,
) {
    val sortOptions = if (mode == DisplayMode.FOLDERS) {
        SortBy.entries.filter { it.appliesToFolders }
    } else {
        SortBy.entries.toList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Layout", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                SegmentedButton(
                    selected = layout == LibraryLayout.LIST,
                    onClick = { onLayout(LibraryLayout.LIST) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Filled.ViewList, null) },
                ) { Text("List", modifier = Modifier.padding(horizontal = 6.dp)) }
                SegmentedButton(
                    selected = layout == LibraryLayout.GRID,
                    onClick = { onLayout(LibraryLayout.GRID) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Filled.GridView, null) },
                ) { Text("Grid", modifier = Modifier.padding(horizontal = 6.dp)) }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Text("Sort", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            ) {
                sortOptions.forEach { sort ->
                    FilterChip(
                        selected = sortBy == sort,
                        onClick = { onSort(sort) },
                        label = { Text(sort.label) },
                    )
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.padding(top = 12.dp, bottom = 16.dp)) {
                SegmentedButton(
                    selected = direction == SortDirection.ASCENDING,
                    onClick = { onDirection(SortDirection.ASCENDING) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Filled.ArrowDownward, null) },
                ) { Text("A to Z", modifier = Modifier.padding(horizontal = 6.dp)) }
                SegmentedButton(
                    selected = direction == SortDirection.DESCENDING,
                    onClick = { onDirection(SortDirection.DESCENDING) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.Filled.ArrowUpward, null) },
                ) { Text("Z to A", modifier = Modifier.padding(horizontal = 6.dp)) }
            }

            if (mode == DisplayMode.VIDEOS) {
                HorizontalDivider()
                Text(
                    "Fields",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                VideoCardField.entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { field ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f),
                            ) {
                                Checkbox(
                                    checked = field.key in enabledFields,
                                    onCheckedChange = { onToggleField(field.key) },
                                )
                                Text(field.label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}
