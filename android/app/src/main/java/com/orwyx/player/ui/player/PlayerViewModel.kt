package com.orwyx.player.ui.player

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import com.orwyx.player.data.repository.VideoRepository
import com.orwyx.player.data.settings.AppSettings
import com.orwyx.player.data.settings.ResumeMode
import com.orwyx.player.data.settings.SettingsRepository
import com.orwyx.player.data.settings.ZoomMode
import com.orwyx.player.domain.model.LibraryQuery
import com.orwyx.player.domain.model.SortBy
import com.orwyx.player.domain.model.SortDirection
import com.orwyx.player.domain.model.Video
import com.orwyx.player.player.PlayerEngine
import com.orwyx.player.player.SleepTimer
import com.orwyx.player.player.SleepTimerState
import com.orwyx.player.player.audio.AudioFxController
import com.orwyx.player.player.enhance.EnhancePipeline
import com.orwyx.player.player.enhance.EnhanceSettings
import com.orwyx.player.player.subtitle.SubtitleManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RepeatState { OFF, ALL, ONE }

@Immutable
data class PlayerUiState(
    val video: Video? = null,
    val queueIndex: Int = 0,
    val queueSize: Int = 1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val bufferedMs: Long = 0,
    val speed: Float = 1f,
    val holdSpeedActive: Boolean = false,
    val holdSpeedValue: Float = 1f,
    val repeatState: RepeatState = RepeatState.OFF,
    val shuffle: Boolean = false,
    val zoomMode: ZoomMode = ZoomMode.FIT,
    /** Decoder-reported size, rotation already applied; drives the activity's orientation lock. */
    val videoDisplayWidth: Int = 0,
    val videoDisplayHeight: Int = 0,
    val locked: Boolean = false,
    val controlsVisible: Boolean = true,
    val subtitleText: String? = null,
    val subtitleDelayMs: Long = 0,
    val audioDelayMs: Long = 0,
    val enhance: EnhanceSettings = EnhanceSettings.OFF,
    val enhanceSupported: Boolean = true,
    val audioTracks: List<TrackChoice> = emptyList(),
    val textTracks: List<TrackChoice> = emptyList(),
    val error: String? = null,
)

@Immutable
data class TrackChoice(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
)

/**
 * Playback session state holder. Owns the queue, position persistence, subtitle
 * cue resolution, speed/repeat/shuffle/zoom, sleep timer, and track selection.
 * The Activity only forwards lifecycle and PiP events.
 */
@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val engine: PlayerEngine,
    private val repository: VideoRepository,
    private val settingsRepository: SettingsRepository,
    val subtitleManager: SubtitleManager,
    val audioFx: AudioFxController,
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state

    val sleepTimer = SleepTimer(viewModelScope) { engine.player?.pause() }
    val sleepTimerState: StateFlow<SleepTimerState> = sleepTimer.state

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var queue: List<Video> = emptyList()
    private var embeddedCueText: String? = null
    private var initialized = false

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.value = _state.value.copy(isBuffering = playbackState == Player.STATE_BUFFERING)
            if (playbackState == Player.STATE_ENDED && sleepTimer.onVideoEnded()) {
                engine.player?.pause()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            onQueueItemChanged()
        }

        override fun onTracksChanged(tracks: Tracks) {
            publishTracks(tracks)
        }

        override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
            embeddedCueText = cueGroup.cues
                .mapNotNull { it.text?.toString() }
                .joinToString("\n")
                .ifBlank { null }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            audioFx.attach(audioSessionId)
        }

        // Rotation-corrected size: unappliedRotationDegrees is 0 once the decoder
        // has already applied the rotation, which is the common case.
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            val rotated = videoSize.unappliedRotationDegrees % 180 != 0
            val width = if (rotated) videoSize.height else videoSize.width
            val height = if (rotated) videoSize.width else videoSize.height
            if (width > 0 && height > 0) {
                _state.value = _state.value.copy(videoDisplayWidth = width, videoDisplayHeight = height)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.value = _state.value.copy(error = error.errorCodeName)
        }
    }

    /** Entry from the library (videoId) or an external VIEW intent (uri only). */
    fun initialize(videoId: Long?, externalUri: Uri?) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val prefs = settingsRepository.settings.first()
            val player = engine.acquire(prefs.decoderMode, prefs.batteryMode)
            player.addListener(playerListener)
            engine.setSpeed(prefs.defaultSpeed)
            _state.value = _state.value.copy(
                speed = prefs.defaultSpeed,
                zoomMode = if (prefs.rememberZoom) prefs.zoomMode else ZoomMode.FIT,
            )

            when {
                videoId != null -> openFromLibrary(videoId, prefs)
                externalUri != null -> openExternal(externalUri)
            }
            player.prepare()
            player.play()
            startTicker()
        }
    }

    private suspend fun openFromLibrary(videoId: Long, prefs: AppSettings) {
        val video = repository.video(videoId) ?: return
        // Queue = the video's folder, name-sorted, so next/previous feel predictable.
        queue = repository.videos(
            LibraryQuery(
                folderPath = video.folderPath,
                sortBy = SortBy.TITLE,
                direction = SortDirection.ASCENDING,
                includePrivate = video.isPrivate,
            ),
        ).first().ifEmpty { listOf(video) }

        val startIndex = queue.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
        engine.player?.setMediaItems(queue.map { it.toMediaItem() }, startIndex, C.TIME_UNSET)

        val resumeAt = when (prefs.resumeMode) {
            ResumeMode.ALWAYS, ResumeMode.ASK -> // ASK surfaces a snackbar; resume by default
                video.positionMs.takeIf { video.isInProgress } ?: 0L
            ResumeMode.NEVER -> 0L
        }
        if (resumeAt > 0) engine.player?.seekTo(startIndex, resumeAt)
        onQueueItemChanged()
    }

    private fun openExternal(uri: Uri) {
        queue = emptyList()
        engine.player?.setMediaItem(MediaItem.fromUri(uri))
        _state.value = _state.value.copy(queueSize = 1)
    }

    private fun onQueueItemChanged() {
        val player = engine.player ?: return
        val index = player.currentMediaItemIndex
        val video = queue.getOrNull(index)
        _state.value = _state.value.copy(
            video = video,
            queueIndex = index,
            queueSize = queue.size.coerceAtLeast(1),
            durationMs = player.duration.coerceAtLeast(0),
        )
        subtitleManager.clearTrack()
        video ?: return
        viewModelScope.launch {
            val prefs = settingsRepository.settings.first()
            subtitleManager.prepareFor(
                videoPath = video.path,
                autoLoad = prefs.subtitleAutoLoad,
                encoding = prefs.subtitleEncoding,
                frameRate = video.frameRate,
            )
        }
    }

    /** UI clock: position + active subtitle cue, throttled and lifecycle-safe. */
    private fun startTicker() {
        viewModelScope.launch {
            while (isActive) {
                val player = engine.player
                if (player != null) {
                    val position = player.currentPosition
                    val external = subtitleManager.cueFor(position)?.text
                    _state.value = _state.value.copy(
                        positionMs = position,
                        durationMs = player.duration.coerceAtLeast(0),
                        bufferedMs = player.bufferedPosition,
                        subtitleText = external ?: embeddedCueText,
                        subtitleDelayMs = subtitleManager.session.value.delayMs,
                    )
                }
                delay(if (_state.value.isPlaying) 200 else 500)
            }
        }
        // Persist the position periodically so "continue watching" survives crashes.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                persistPosition()
            }
        }
    }

    /** Direct player handle for the PlayerView surface binding. */
    fun enginePlayer(): androidx.media3.exoplayer.ExoPlayer? = engine.player

    // --- Transport ---------------------------------------------------------

    fun togglePlayPause() {
        val player = engine.player ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        engine.player?.seekTo(positionMs.coerceIn(0, _state.value.durationMs))
    }

    fun seekBy(deltaMs: Long) = seekTo(_state.value.positionMs + deltaMs)

    fun next() = engine.player?.seekToNextMediaItem() ?: Unit
    fun previous() = engine.player?.seekToPreviousMediaItem() ?: Unit

    fun stepFrame(forward: Boolean) =
        engine.stepFrame(forward, _state.value.video?.frameRate ?: 0f)

    fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _state.value = _state.value.copy(speed = speed)
    }

    // --- Long-press hold speed -----------------------------------------------
    // Starts at a quick 2x boost (a plain long press with no drag is the common
    // case); dragging left/right while still holding fine-tunes the value via
    // the on-screen slider. Releasing always restores the speed from before
    // the hold started.

    private var speedBeforeHold = 1f
    private var holdDragAccum = 0f

    fun startHoldSpeed() {
        speedBeforeHold = _state.value.speed
        holdDragAccum = 0f
        engine.setSpeed(HOLD_INITIAL_SPEED)
        _state.value = _state.value.copy(holdSpeedActive = true, holdSpeedValue = HOLD_INITIAL_SPEED)
    }

    /** [normalizedDeltaX] is the horizontal drag delta as a fraction of screen width. */
    fun dragHoldSpeed(normalizedDeltaX: Float) {
        holdDragAccum += normalizedDeltaX
        val raw = (HOLD_INITIAL_SPEED + holdDragAccum * HOLD_DRAG_SENSITIVITY)
            .coerceIn(HOLD_SPEED_RANGE.start, HOLD_SPEED_RANGE.endInclusive)
        val snapped = (Math.round(raw / HOLD_SPEED_STEP) * HOLD_SPEED_STEP)
            .coerceIn(HOLD_SPEED_RANGE.start, HOLD_SPEED_RANGE.endInclusive)
        engine.setSpeed(snapped)
        _state.value = _state.value.copy(holdSpeedValue = snapped)
    }

    fun endHoldSpeed() {
        engine.setSpeed(speedBeforeHold)
        _state.value = _state.value.copy(holdSpeedActive = false)
    }

    fun cycleRepeat() {
        val next = when (_state.value.repeatState) {
            RepeatState.OFF -> RepeatState.ALL
            RepeatState.ALL -> RepeatState.ONE
            RepeatState.ONE -> RepeatState.OFF
        }
        engine.player?.repeatMode = when (next) {
            RepeatState.OFF -> Player.REPEAT_MODE_OFF
            RepeatState.ALL -> Player.REPEAT_MODE_ALL
            RepeatState.ONE -> Player.REPEAT_MODE_ONE
        }
        _state.value = _state.value.copy(repeatState = next)
    }

    fun toggleShuffle() {
        val next = !_state.value.shuffle
        engine.player?.shuffleModeEnabled = next
        _state.value = _state.value.copy(shuffle = next)
    }

    // --- View --------------------------------------------------------------

    fun setZoomMode(mode: ZoomMode) {
        _state.value = _state.value.copy(zoomMode = mode)
        viewModelScope.launch {
            if (settingsRepository.settings.first().rememberZoom) {
                settingsRepository.setZoomMode(mode)
            }
        }
    }

    fun setLocked(locked: Boolean) {
        _state.value = _state.value.copy(locked = locked, controlsVisible = !locked)
    }

    fun setControlsVisible(visible: Boolean) {
        if (_state.value.locked && visible) return
        _state.value = _state.value.copy(controlsVisible = visible)
    }

    // --- Subtitles & audio ---------------------------------------------------

    fun loadSubtitleFromPath(path: String, encoding: String) {
        viewModelScope.launch {
            subtitleManager.loadFromPath(path, encoding, _state.value.video?.frameRate ?: 24f)
        }
    }

    fun loadSubtitleFromUri(uri: Uri, encoding: String) {
        viewModelScope.launch {
            subtitleManager.loadFromUri(
                uri = uri,
                displayName = uri.lastPathSegment ?: "subtitle.srt",
                encoding = encoding,
                frameRate = _state.value.video?.frameRate ?: 24f,
            )
        }
    }

    fun setSubtitleDelay(deltaMs: Long) {
        subtitleManager.setDelay(subtitleManager.session.value.delayMs + deltaMs)
    }

    fun setAudioDelay(deltaMs: Long) {
        val next = _state.value.audioDelayMs + deltaMs
        engine.setAudioDelay(next)
        _state.value = _state.value.copy(audioDelayMs = engine.audioDelayProcessor.delayMs())
    }

    fun setMonoAudio(enabled: Boolean) = engine.setMonoAudio(enabled)

    fun selectTrack(choice: TrackChoice) {
        val selector = engine.trackSelector ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .addOverride(TrackSelectionOverride(choice.group.mediaTrackGroup, choice.trackIndex))
                .setTrackTypeDisabled(choice.group.type, false),
        )
    }

    fun disableTextTracks() {
        val selector = engine.trackSelector ?: return
        selector.setParameters(
            selector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true),
        )
        subtitleManager.clearTrack()
    }

    private fun publishTracks(tracks: Tracks) {
        fun choices(type: Int): List<TrackChoice> =
            tracks.groups.filter { it.type == type }.flatMap { group ->
                (0 until group.length).mapNotNull { i ->
                    if (!group.isTrackSupported(i)) return@mapNotNull null
                    val format = group.getTrackFormat(i)
                    val label = buildString {
                        append(format.label ?: format.language?.uppercase() ?: "Track ${i + 1}")
                        format.language?.let { if (format.label != null) append(" · $it") }
                    }
                    TrackChoice(group, i, label, group.isTrackSelected(i))
                }
            }
        _state.value = _state.value.copy(
            audioTracks = choices(C.TRACK_TYPE_AUDIO),
            textTracks = choices(C.TRACK_TYPE_TEXT),
        )
    }

    // --- Enhancement -----------------------------------------------------------
    // One-tap automatic enhancement: no manual sliders. Tuned constants live in
    // EnhanceSettings' defaults; toggling just flips the pipeline on/off.

    fun toggleEnhance() {
        val next = _state.value.enhance.copy(enabled = !_state.value.enhance.enabled)
        val supported = engine.setVideoEffects(EnhancePipeline.build(next))
        _state.value = _state.value.copy(
            enhance = if (supported) next else EnhanceSettings.OFF,
            enhanceSupported = supported,
        )
    }

    // --- Lifecycle ---------------------------------------------------------

    fun onUiStopped(inPictureInPicture: Boolean) {
        viewModelScope.launch {
            persistPosition()
            val background = settingsRepository.settings.first().backgroundPlayback
            if (!inPictureInPicture && !background) engine.player?.pause()
        }
    }

    private suspend fun persistPosition() {
        val video = _state.value.video ?: return
        val player = engine.player ?: return
        repository.savePlaybackPosition(video.id, player.currentPosition)
    }

    override fun onCleared() {
        audioFx.release()
        engine.release()
        super.onCleared()
    }

    private fun Video.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setUri(uri)
            .setMediaId(id.toString())
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .build()

    companion object {
        const val HOLD_INITIAL_SPEED = 2f
        /** Full screen-width drag shifts the speed by roughly this many multiples. */
        const val HOLD_DRAG_SENSITIVITY = 4f
        const val HOLD_SPEED_STEP = 0.5f
        val HOLD_SPEED_RANGE = 0.5f..3f
    }
}
