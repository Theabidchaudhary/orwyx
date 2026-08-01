package com.orwyx.player.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.orwyx.player.data.settings.BatteryMode
import com.orwyx.player.data.settings.DecoderMode
import com.orwyx.player.player.audio.AudioDelayProcessor
import com.orwyx.player.player.audio.StereoModeProcessor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Owns the single [ExoPlayer] instance shared by [PlaybackService] (background/
 * notification control) and the player UI (direct access for effects, track
 * selection, and frame stepping — capabilities MediaController can't express).
 *
 * Decoding strategy:
 *  - AUTO: hardware decoders first, `setEnableDecoderFallback` steps down to
 *    software automatically on codec failure.
 *  - HARDWARE / SOFTWARE: decoder lists are re-ordered so the preferred class
 *    is tried first; fallback still applies, so playback never hard-fails when
 *    an alternative decoder exists.
 *  - Asynchronous MediaCodec queueing keeps demux/decode off the playback thread
 *    (multi-core decode path).
 */
@UnstableApi
@Singleton
class PlayerEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    val audioDelayProcessor: AudioDelayProcessor,
    val stereoModeProcessor: StereoModeProcessor,
) {
    var player: ExoPlayer? = null
        private set

    var trackSelector: DefaultTrackSelector? = null
        private set

    private var currentDecoderMode = DecoderMode.AUTO

    fun acquire(decoderMode: DecoderMode, batteryMode: BatteryMode): ExoPlayer {
        val existing = player
        if (existing != null && decoderMode == currentDecoderMode) return existing
        existing?.release()
        currentDecoderMode = decoderMode

        val selector = DefaultTrackSelector(context).also { trackSelector = it }

        val renderersFactory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf(audioDelayProcessor, stereoModeProcessor))
                .build()
        }
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector(decoderMode))
            .forceEnableMediaCodecAsynchronousQueueing()

        // Battery saver trims buffer targets; performance mode buffers further ahead.
        val loadControl = when (batteryMode) {
            BatteryMode.SAVER -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 30_000, 1_000, 2_000)
                .build()
            BatteryMode.BALANCED -> DefaultLoadControl.Builder().build()
            BatteryMode.PERFORMANCE -> DefaultLoadControl.Builder()
                .setBufferDurationsMs(50_000, 120_000, 1_000, 2_000)
                .build()
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build()
            .also { player = it }
    }

    /** Playback speed with pitch correction (pitch stays 1.0 at any rate). */
    fun setSpeed(speed: Float) {
        player?.playbackParameters = PlaybackParameters(speed, 1f)
    }

    /** Frame stepping while paused; uses exact seeking for frame accuracy. */
    fun stepFrame(forward: Boolean, frameRate: Float) {
        val p = player ?: return
        val frameMs = if (frameRate > 1f) (1000f / frameRate).roundToLong() else 33L
        p.setSeekParameters(SeekParameters.EXACT)
        p.pause()
        p.seekTo((p.currentPosition + if (forward) frameMs else -frameMs).coerceAtLeast(0))
        p.setSeekParameters(SeekParameters.CLOSEST_SYNC)
    }

    /**
     * Applies (or clears) the enhancement pipeline. Returns false when the device
     * can't run GL effects, so the UI can disable the toggle gracefully.
     *
     * A no-op seek forces the frame processor to re-render the current frame
     * through the new effect chain immediately — without it the change only
     * becomes visible after the next natural seek or media transition.
     */
    fun setVideoEffects(effects: List<Effect>): Boolean =
        runCatching {
            player?.setVideoEffects(effects)
            player?.let { it.seekTo(it.currentPosition) }
        }.isSuccess

    /** Audio delay requires a pipeline flush to take effect immediately. */
    fun setAudioDelay(delayMs: Long) {
        audioDelayProcessor.setDelayMs(delayMs)
        player?.let { it.seekTo(it.currentPosition) }
    }

    fun setMonoAudio(enabled: Boolean) {
        stereoModeProcessor.monoEnabled = enabled
        player?.let { it.seekTo(it.currentPosition) }
    }

    fun release() {
        player?.release()
        player = null
        trackSelector = null
    }

    private fun codecSelector(mode: DecoderMode): MediaCodecSelector =
        when (mode) {
            DecoderMode.AUTO -> MediaCodecSelector.DEFAULT
            DecoderMode.HARDWARE -> MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                    .sortedByDescending { it.hardwareAccelerated }
            }
            DecoderMode.SOFTWARE -> MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                MediaCodecUtil.getDecoderInfos(mimeType, requiresSecure, requiresTunneling)
                    .sortedBy { it.hardwareAccelerated }
            }
        }
}
