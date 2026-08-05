package com.orwyx.player.ui.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orwyx.player.core.util.Formatters
import com.orwyx.player.data.settings.AppSettings
import com.orwyx.player.player.SleepTimerState
import com.orwyx.player.player.audio.AudioFxController
import com.orwyx.player.ui.components.AppDropdownMenu
import kotlin.math.roundToInt

/**
 * Compact, icon-anchored panels for audio/captions/sleep — [AppDropdownMenu]
 * auto-sizes to its content and positions itself near the anchor, so these
 * stay small and out of the way instead of covering the video like a full
 * bottom sheet.
 */
@Composable
fun AudioQuickMenu(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    tint: Color,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) { expanded = value; onExpandedChange(value) }
    val fx by viewModel.audioFx.state.collectAsState()
    var mono by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { setExpanded(true) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, "Audio", tint = tint)
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            if (state.audioTracks.size > 1) {
                state.audioTracks.forEach { track ->
                    DropdownMenuItem(
                        text = { Text(track.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            if (track.selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        onClick = { viewModel.selectTrack(track); setExpanded(false) },
                        modifier = Modifier.widthIn(max = 240.dp),
                    )
                }
                HorizontalDivider()
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Mono", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = mono, onCheckedChange = { mono = it; viewModel.setMonoAudio(it) })
            }
            if (fx.available) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).width(200.dp)) {
                    Text("Boost", style = MaterialTheme.typography.labelSmall)
                    Slider(
                        value = fx.volumeBoostMb.toFloat(),
                        onValueChange = { viewModel.audioFx.setVolumeBoost(it.toInt()) },
                        valueRange = 0f..AudioFxController.MAX_BOOST_MB.toFloat(),
                    )
                }
            }
        }
    }
}

@Composable
fun CaptionsQuickMenu(
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    settings: AppSettings?,
    tint: Color,
    onExpandedChange: (Boolean) -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) { expanded = value; onExpandedChange(value) }
    val session by viewModel.subtitleManager.session.collectAsState()
    val active = session.track != null || state.textTracks.any { it.selected }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        settings?.let { viewModel.loadSubtitleFromUri(uri, it.subtitleEncoding) }
    }

    Box {
        IconButton(onClick = { setExpanded(true) }) {
            Icon(Icons.Filled.Subtitles, "Captions", tint = if (active) MaterialTheme.colorScheme.primary else tint)
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            DropdownMenuItem(
                text = { Text("Off") },
                leadingIcon = { if (!active) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) },
                onClick = { viewModel.disableTextTracks(); setExpanded(false) },
            )
            state.textTracks.forEach { track ->
                DropdownMenuItem(
                    text = { Text(track.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = {
                        if (track.selected) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    onClick = { viewModel.selectTrack(track); setExpanded(false) },
                    modifier = Modifier.widthIn(max = 240.dp),
                )
            }
            session.availableSidecars.forEach { path ->
                DropdownMenuItem(
                    text = { Text(path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        settings?.let { viewModel.loadSubtitleFromPath(path, it.subtitleEncoding) }
                        setExpanded(false)
                    },
                    modifier = Modifier.widthIn(max = 240.dp),
                )
            }
            DropdownMenuItem(
                text = { Text("Open file…") },
                leadingIcon = { Icon(Icons.Filled.FileOpen, null) },
                onClick = { setExpanded(false); picker.launch(arrayOf("*/*")) },
            )
            if (session.track != null) {
                HorizontalDivider()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Delay ${session.delayMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { viewModel.setSubtitleDelay(-100) }) {
                        Icon(Icons.Filled.Remove, "Earlier")
                    }
                    IconButton(onClick = { viewModel.setSubtitleDelay(100) }) {
                        Icon(Icons.Filled.Add, "Later")
                    }
                }
            }
        }
    }
}

/** Sleep timer: a dotted snap slider (5-120 min, same look as the speed control) instead of a preset list. */
@Composable
fun SleepQuickMenu(viewModel: PlayerViewModel, tint: Color, onExpandedChange: (Boolean) -> Unit = {}) {
    var expanded by remember { mutableStateOf(false) }
    fun setExpanded(value: Boolean) { expanded = value; onExpandedChange(value) }
    val timerState by viewModel.sleepTimerState.collectAsState()
    val active = timerState !is SleepTimerState.Off
    var minutes by remember { mutableFloatStateOf(30f) }

    Box {
        IconButton(onClick = { setExpanded(true) }) {
            Icon(Icons.Filled.Bedtime, "Sleep timer", tint = if (active) MaterialTheme.colorScheme.primary else tint)
        }
        AppDropdownMenu(expanded = expanded, onDismissRequest = { setExpanded(false) }) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp).width(230.dp)) {
                (timerState as? SleepTimerState.Running)?.let { running ->
                    Text(
                        "Pausing in ${Formatters.duration(running.remainingMs)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${minutes.roundToInt()} min",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    DottedSnapSlider(
                        value = minutes,
                        range = 0f..120f,
                        step = 5f,
                        dotStep = 15f,
                        onValueChange = { minutes = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    TextButton(onClick = { viewModel.sleepTimer.startEndOfVideo(); setExpanded(false) }) {
                        Text("End of video")
                    }
                    if (active) {
                        TextButton(onClick = { viewModel.sleepTimer.cancel(); setExpanded(false) }) {
                            Text("Cancel")
                        }
                    }
                    IconButton(onClick = {
                        viewModel.sleepTimer.start(minutes.roundToInt().coerceAtLeast(1) * 60_000L)
                        setExpanded(false)
                    }) {
                        Icon(Icons.Filled.Check, "Start timer", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
