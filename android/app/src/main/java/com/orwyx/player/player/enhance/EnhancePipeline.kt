package com.orwyx.player.player.enhance

import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment

/**
 * User-facing enhancement toggle (all off by default).
 *
 * Tuned for a clearly visible "auto enhance" look on a single tap — punchier
 * contrast and saturation plus a small lift, similar to a photo app's
 * one-tap auto-enhance. No manual controls are exposed; these constants are
 * the whole feature.
 */
data class EnhanceSettings(
    val enabled: Boolean = false,
    val contrastBoost: Float = 0.35f, // -1..1
    val colorBoost: Float = 32f, // saturation delta, -100..100
    val brightnessLift: Float = 0.05f, // -1..1
    val skinToneWarmth: Float = 4f, // hue-band saturation lift
) {
    companion object {
        val OFF = EnhanceSettings(enabled = false)
    }
}

/**
 * Real-time enhancement pipeline built on Media3's GPU effect framework.
 *
 * Every effect runs as a GL shader on the video path (zero CPU-side frame
 * copies). [com.orwyx.player.player.PlayerEngine.setVideoEffects] reports
 * whether the device accepted the pipeline; on failure the UI disables the
 * toggle rather than degrading playback.
 *
 * Extension points (same GlEffect mechanism, add as custom shaders):
 * unsharp-mask sharpening, bilateral denoise, debanding dither, and NPU/GPU
 * super-resolution where the vendor exposes it.
 */
@UnstableApi
object EnhancePipeline {

    fun build(settings: EnhanceSettings): List<Effect> {
        if (!settings.enabled) return emptyList()
        return listOf(
            Contrast(settings.contrastBoost.coerceIn(-1f, 1f)),
            Brightness(settings.brightnessLift.coerceIn(-1f, 1f)),
            HslAdjustment.Builder()
                .adjustSaturation(settings.colorBoost.coerceIn(-100f, 100f))
                .build(),
        )
    }
}
