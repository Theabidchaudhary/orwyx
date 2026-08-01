package com.orwyx.player.ui.player

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.orwyx.player.core.util.Formatters
import com.orwyx.player.data.settings.AppSettings
import com.orwyx.player.data.settings.ZoomMode
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onSetBrightness: (Float) -> Unit,
    onEnterPip: () -> Unit,
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }

    // Gesture working values.
    var brightness by remember { mutableFloatStateOf(0.5f) }
    var volumeFraction by remember {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) / max)
    }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubTargetMs by remember { mutableFloatStateOf(0f) }
    // True while any control-row menu (speed panel, audio/captions/sleep dropdown,
    // overflow menu) is open, so auto-hide never yanks the controls away mid-edit.
    var menusInteracting by remember { mutableStateOf(false) }

    var brightnessHud by remember { mutableStateOf<Float?>(null) }
    var volumeHud by remember { mutableStateOf<Float?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    // Netflix-style double-tap seek bursts (rapid taps in the same zone accumulate).
    var leftBurstSeconds by remember { mutableIntStateOf(0) }
    var leftBurstToken by remember { mutableIntStateOf(0) }
    var lastLeftTapAt by remember { mutableFloatStateOf(0f) }
    var rightBurstSeconds by remember { mutableIntStateOf(0) }
    var rightBurstToken by remember { mutableIntStateOf(0) }
    var lastRightTapAt by remember { mutableFloatStateOf(0f) }
    var centerFlashToken by remember { mutableIntStateOf(0) }
    var centerFlashPlaying by remember { mutableStateOf(true) }

    // Auto-hide controls and transient HUDs (paused while actively scrubbing or
    // while any control-row menu is open — never hide out from under an edit).
    LaunchedEffect(state.controlsVisible, state.isPlaying, isScrubbing, menusInteracting) {
        if (state.controlsVisible && state.isPlaying && !isScrubbing && !menusInteracting) {
            delay(3_500)
            viewModel.setControlsVisible(false)
        }
    }
    LaunchedEffect(brightnessHud) { if (brightnessHud != null) { delay(700); brightnessHud = null } }
    LaunchedEffect(volumeHud) { if (volumeHud != null) { delay(700); volumeHud = null } }
    LaunchedEffect(toast) { if (toast != null) { delay(900); toast = null } }
    LaunchedEffect(leftBurstToken) { if (leftBurstToken > 0) { delay(650); leftBurstSeconds = 0 } }
    LaunchedEffect(rightBurstToken) { if (rightBurstToken > 0) { delay(650); rightBurstSeconds = 0 } }
    LaunchedEffect(centerFlashToken) { if (centerFlashToken > 0) { delay(450); centerFlashToken = 0 } }

    val gestureListener = remember(settings) {
        object : PlayerGestureListener {
            override fun onTap() {
                val current = viewModel.state.value
                if (!current.locked) {
                    viewModel.setControlsVisible(!current.controlsVisible)
                }
            }

            override fun onDoubleTap(zone: Int) {
                val stepSec = settings?.seekStepSeconds ?: 10
                val stepMs = stepSec * 1000L
                val now = SystemClock.uptimeMillis().toFloat()
                when (zone) {
                    -1 -> {
                        viewModel.seekBy(-stepMs)
                        leftBurstSeconds = if (now - lastLeftTapAt < 900f) leftBurstSeconds + stepSec else stepSec
                        lastLeftTapAt = now
                        leftBurstToken++
                    }
                    1 -> {
                        viewModel.seekBy(stepMs)
                        rightBurstSeconds = if (now - lastRightTapAt < 900f) rightBurstSeconds + stepSec else stepSec
                        lastRightTapAt = now
                        rightBurstToken++
                    }
                    else -> {
                        viewModel.togglePlayPause()
                        centerFlashPlaying = viewModel.state.value.isPlaying
                        centerFlashToken++
                    }
                }
            }

            override fun onLongPressStart() = viewModel.startHoldSpeed()
            override fun onLongPressDrag(normalizedDeltaX: Float) = viewModel.dragHoldSpeed(normalizedDeltaX)
            override fun onLongPressEnd() = viewModel.endHoldSpeed()

            override fun onDragStart(mode: DragMode) {
                if (mode == DragMode.SEEK) {
                    scrubTargetMs = viewModel.state.value.positionMs.toFloat()
                    isScrubbing = true
                    // Swiping to seek reveals the progress bar even if controls were hidden,
                    // so the scrub is always visible — never a silent gesture.
                    viewModel.setControlsVisible(true)
                }
            }

            override fun onDragDelta(mode: DragMode, normalizedDelta: Float) {
                when (mode) {
                    DragMode.BRIGHTNESS -> {
                        brightness = (brightness + normalizedDelta).coerceIn(0.01f, 1f)
                        onSetBrightness(brightness)
                        brightnessHud = brightness
                    }
                    DragMode.VOLUME -> {
                        volumeFraction = (volumeFraction + normalizedDelta).coerceIn(0f, 1f)
                        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            (volumeFraction * max).toInt(),
                            0,
                        )
                        volumeHud = volumeFraction
                    }
                    DragMode.SEEK -> {
                        // Full screen width sweeps two minutes; sensitivity scales this.
                        // The progress bar itself scrubs live via scrubTargetMs below.
                        scrubTargetMs = (scrubTargetMs + normalizedDelta * 120_000f)
                            .coerceIn(0f, viewModel.state.value.durationMs.toFloat())
                    }
                    DragMode.NONE -> Unit
                }
            }

            override fun onDragEnd(mode: DragMode) {
                if (mode == DragMode.SEEK) {
                    viewModel.seekTo(scrubTargetMs.toLong())
                    isScrubbing = false
                }
            }

            override fun onPinch(zoomFactor: Float, pan: Offset) {
                zoomScale = (zoomScale * zoomFactor).coerceIn(0.5f, 4f)
                zoomOffset += pan
            }

            override fun onTwoFingerTap() {
                viewModel.setLocked(!viewModel.state.value.locked)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .playerGestures(
                listener = gestureListener,
                sensitivity = settings?.gestureSensitivity ?: 1f,
                locked = state.locked,
            ),
    ) {
        // Video surface. PlayerView handles the surface lifecycle + resize modes;
        // pinch zoom/pan are applied as a GPU layer transform on top.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    subtitleView?.visibility = android.view.View.GONE
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { view ->
                view.player = viewModel.enginePlayer()
                view.resizeMode = when (state.zoomMode) {
                    ZoomMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    ZoomMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    ZoomMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    ZoomMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoomScale
                    scaleY = zoomScale
                    translationX = zoomOffset.x
                    translationY = zoomOffset.y
                },
        )

        if (state.isBuffering) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
        }

        settings?.let { s ->
            SubtitleOverlay(
                text = state.subtitleText,
                settings = s,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Brightness (left) / volume (right) vertical sliders.
        AnimatedVisibility(
            visible = brightnessHud != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
        ) {
            VerticalGestureBar(icon = Icons.Filled.BrightnessMedium, fraction = brightnessHud ?: 0f)
        }
        AnimatedVisibility(
            visible = volumeHud != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
        ) {
            VerticalGestureBar(icon = Icons.AutoMirrored.Filled.VolumeUp, fraction = volumeHud ?: 0f)
        }

        // Netflix-style ±10s bursts, left/right thirds.
        Box(modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.33f), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible = leftBurstToken > 0 && leftBurstSeconds > 0, enter = fadeIn(), exit = fadeOut()) {
                SeekBurst(seconds = leftBurstSeconds, forward = false, key = leftBurstToken)
            }
        }
        Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxWidth(0.33f), contentAlignment = Alignment.Center) {
            AnimatedVisibility(visible = rightBurstToken > 0 && rightBurstSeconds > 0, enter = fadeIn(), exit = fadeOut()) {
                SeekBurst(seconds = rightBurstSeconds, forward = true, key = rightBurstToken)
            }
        }
        AnimatedVisibility(
            visible = centerFlashToken > 0,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                if (centerFlashPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(84.dp)
                    .background(Color(0x4D000000), CircleShape)
                    .padding(16.dp),
            )
        }

        // Long-press speed: minimal, no background. Rises toward the top edge when
        // the rest of the UI is hidden so it never sits over the middle of the frame.
        val holdSpeedTopPadding by animateDpAsState(
            targetValue = if (state.controlsVisible) 64.dp else 12.dp,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
            label = "holdSpeedTopPadding",
        )
        AnimatedVisibility(
            visible = state.holdSpeedActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = holdSpeedTopPadding),
        ) {
            HoldSpeedHud(state.holdSpeedValue)
        }

        // Small transient toast (crop mode change, enhance toggle) — near the bottom row, never full-screen.
        AnimatedVisibility(
            visible = toast != null,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 116.dp),
        ) {
            toast?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .background(Color(0x99000000), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        if (state.locked) {
            IconButton(
                onClick = { viewModel.setLocked(false) },
                modifier = Modifier.align(Alignment.CenterStart).padding(24.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = "Unlock", tint = Color.White)
            }
        } else {
            PlayerControls(
                state = state,
                settings = settings,
                displayPositionMs = if (isScrubbing) scrubTargetMs.toLong() else state.positionMs,
                onExit = onExit,
                onEnterPip = onEnterPip,
                onScrub = { ms ->
                    isScrubbing = true
                    scrubTargetMs = ms.toFloat()
                },
                onScrubFinished = {
                    viewModel.seekTo(scrubTargetMs.toLong())
                    isScrubbing = false
                },
                onCropChanged = { label ->
                    zoomScale = 1f
                    zoomOffset = Offset.Zero
                    toast = label
                },
                onEnhanceToggled = { label -> toast = label },
                onInteractingChange = { menusInteracting = it },
                viewModel = viewModel,
            )
        }

        state.error?.let { error ->
            Text(
                text = "Playback error: $error",
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 140.dp)
                    .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                    .padding(12.dp),
            )
        }
    }
}

@UnstableApi
@Composable
private fun PlayerControls(
    state: PlayerUiState,
    settings: AppSettings?,
    displayPositionMs: Long,
    onExit: () -> Unit,
    onEnterPip: () -> Unit,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    onCropChanged: (String) -> Unit,
    onEnhanceToggled: (String) -> Unit,
    onInteractingChange: (Boolean) -> Unit,
    viewModel: PlayerViewModel,
) {
    val white = Color.White
    var overflowExpanded by remember { mutableStateOf(false) }
    var speedExpanded by remember { mutableStateOf(false) }
    var audioExpanded by remember { mutableStateOf(false) }
    var captionsExpanded by remember { mutableStateOf(false) }
    var sleepExpanded by remember { mutableStateOf(false) }
    val anyMenuOpen = overflowExpanded || speedExpanded || audioExpanded || captionsExpanded || sleepExpanded
    LaunchedEffect(anyMenuOpen) { onInteractingChange(anyMenuOpen) }

    AnimatedVisibility(
        visible = state.controlsVisible,
        enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) { -it / 2 },
        exit = fadeOut(tween(150)) + slideOutVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) { -it / 2 },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = white)
                }
                Text(
                    text = state.video?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = white,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { speedExpanded = !speedExpanded }) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (speedExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color(0x33FFFFFF),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${formatSpeed(state.speed)}×", color = white, style = MaterialTheme.typography.labelSmall)
                    }
                }
                CaptionsQuickMenu(viewModel, state, settings, white, onExpandedChange = { captionsExpanded = it })
                AudioQuickMenu(viewModel, state, white, onExpandedChange = { audioExpanded = it })
                IconButton(onClick = {
                    viewModel.toggleEnhance()
                    val s = viewModel.state.value
                    onEnhanceToggled(
                        when {
                            !s.enhanceSupported -> "Enhance not supported on this device"
                            s.enhance.enabled -> "AI Enhance on"
                            else -> "AI Enhance off"
                        },
                    )
                }) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        "AI Enhance",
                        tint = if (state.enhance.enabled) MaterialTheme.colorScheme.primary else white,
                    )
                }
                SleepQuickMenu(viewModel, white, onExpandedChange = { sleepExpanded = it })
                Box {
                    IconButton(onClick = { overflowExpanded = true }) {
                        Icon(Icons.Filled.MoreVert, "More", tint = white)
                    }
                    DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (state.repeatState == RepeatState.ONE) "Repeat: one" else if (state.repeatState == RepeatState.ALL) "Repeat: all" else "Repeat: off") },
                            leadingIcon = {
                                Icon(if (state.repeatState == RepeatState.ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, null)
                            },
                            onClick = { viewModel.cycleRepeat() },
                        )
                        DropdownMenuItem(
                            text = { Text(if (state.shuffle) "Shuffle: on" else "Shuffle: off") },
                            leadingIcon = { Icon(Icons.Filled.Shuffle, null) },
                            onClick = { viewModel.toggleShuffle() },
                        )
                        DropdownMenuItem(
                            text = { Text("Picture in picture") },
                            leadingIcon = { Icon(Icons.Filled.PictureInPictureAlt, null) },
                            onClick = { overflowExpanded = false; onEnterPip() },
                        )
                    }
                }
            }

            // Speed panel: slides down from the header when its pill is tapped,
            // and back up on confirm — never covers more than this one row.
            AnimatedVisibility(
                visible = speedExpanded,
                enter = expandVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeIn(),
                exit = shrinkVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) + fadeOut(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, end = 12.dp, bottom = 6.dp),
                ) {
                    Text(
                        "${formatSpeed(state.speed)}×",
                        color = white,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                    DottedSnapSlider(
                        value = state.speed,
                        range = SPEED_RANGE,
                        step = SPEED_STEP,
                        onValueChange = viewModel::setSpeed,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { speedExpanded = false }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Check, "Done", tint = white, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = state.controlsVisible,
            enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) { it / 2 },
            exit = fadeOut(tween(150)) + slideOutVertically(spring(dampingRatio = Spring.DampingRatioNoBouncy)) { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Formatters.duration(displayPositionMs), style = MaterialTheme.typography.labelMedium, color = white)
                    ThinSeekBar(
                        progress = displayPositionMs.toFloat() / state.durationMs.toFloat().coerceAtLeast(1f),
                        onSeek = { fraction -> onScrub((fraction * state.durationMs).toLong()) },
                        onSeekFinished = onScrubFinished,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                    Text(Formatters.duration(state.durationMs), style = MaterialTheme.typography.labelMedium, color = white)
                }
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { viewModel.setLocked(true) }) {
                        Icon(Icons.Filled.LockOpen, "Lock", tint = white)
                    }
                    IconButton(onClick = viewModel::previous) {
                        Icon(Icons.Filled.SkipPrevious, "Previous", tint = white, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.size(68.dp)) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            "Play/Pause",
                            tint = white,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                    IconButton(onClick = viewModel::next) {
                        Icon(Icons.Filled.SkipNext, "Next", tint = white, modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = {
                        val next = ZoomMode.entries[(state.zoomMode.ordinal + 1) % ZoomMode.entries.size]
                        viewModel.setZoomMode(next)
                        onCropChanged(zoomModeLabel(next))
                    }) {
                        Icon(Icons.Filled.AspectRatio, "Crop & fit", tint = white)
                    }
                }
            }
        }
    }
}

/** Vertical fill bar for brightness (left side) and volume (right side) gestures. */
@Composable
private fun VerticalGestureBar(icon: ImageVector, fraction: Float) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        label = "gestureBar",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .width(44.dp)
            .height(160.dp)
            .background(Color(0x9915171B), RoundedCornerShape(22.dp))
            .padding(vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .width(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x40FFFFFF)),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(animatedFraction)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White),
            )
        }
        Text("${(fraction * 100).roundToInt()}%", color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/** Circular Netflix-style ±N second burst shown on double tap, with a bouncy scale-in each tap. */
@Composable
private fun SeekBurst(seconds: Int, forward: Boolean, key: Int) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(key) {
        scale.snapTo(0.8f)
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .background(Color(0x59000000), CircleShape)
            .padding(22.dp),
    ) {
        Icon(
            if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )
        Text("${seconds}s", color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** Speed slider shown while a long-press hold is active — no background, so it never masks the video. */
@Composable
private fun HoldSpeedHud(value: Float) {
    val range = PlayerViewModel.HOLD_SPEED_RANGE
    val stepCount = ((range.endInclusive - range.start) / PlayerViewModel.HOLD_SPEED_STEP).roundToInt()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "${formatSpeed(value)}×",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        SnapTicks(
            count = stepCount + 1,
            fraction = fraction,
            modifier = Modifier.padding(top = 8.dp).width(180.dp),
        )
    }
}

private fun formatSpeed(value: Float): String {
    val text = "%.2f".format(value).trimEnd('0').trimEnd('.')
    return text.ifEmpty { "0" }
}

/**
 * Playback speed only supports forward, positive rates — ExoPlayer's
 * PlaybackParameters has no concept of reverse playback, so the slider's
 * lower bound stops at 0.5x rather than the originally requested -2x.
 */
private val SPEED_RANGE = 0.5f..4f
private const val SPEED_STEP = 0.5f

private fun zoomModeLabel(mode: ZoomMode): String = when (mode) {
    ZoomMode.FIT -> "Fit"
    ZoomMode.FILL -> "Fill"
    ZoomMode.STRETCH -> "Stretch"
    ZoomMode.ORIGINAL -> "Original size"
}
