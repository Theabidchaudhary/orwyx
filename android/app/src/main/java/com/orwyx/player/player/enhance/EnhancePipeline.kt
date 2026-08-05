package com.orwyx.player.player.enhance

import android.graphics.ColorMatrix

/**
 * User-facing "AI Enhance" toggle: a single tap boosts contrast, saturation,
 * and brightness. No manual controls are exposed; these tuned constants are
 * the whole feature.
 *
 * This is applied as a hardware-layer [ColorMatrix] filter directly on the
 * player's rendered [android.view.View] (see [toColorMatrix] and its use in
 * PlayerScreen), not through Media3's GPU video-effects pipeline. That GPU
 * pipeline requires routing frames through a VideoFrameProcessor, which some
 * devices/codecs silently don't support — which was the actual reason an
 * earlier version of this feature never visibly changed anything. A
 * View-level color filter has no codec dependency: it just recolors whatever
 * pixels already landed on screen, so it works unconditionally.
 */
data class EnhanceSettings(
    val enabled: Boolean = false,
    val contrast: Float = 1.18f,
    val saturation: Float = 1.4f,
    val brightnessOffset: Float = 10f,
) {
    companion object {
        val OFF = EnhanceSettings(enabled = false)
    }
}

/** Builds the combined contrast+brightness+saturation matrix for this setting. */
fun EnhanceSettings.toColorMatrix(): ColorMatrix {
    val c = contrast
    val translate = (1 - c) * 127.5f + brightnessOffset
    val contrastAndBrightness = ColorMatrix(
        floatArrayOf(
            c, 0f, 0f, 0f, translate,
            0f, c, 0f, 0f, translate,
            0f, 0f, c, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
    val saturationMatrix = ColorMatrix().apply { setSaturation(saturation) }
    contrastAndBrightness.postConcat(saturationMatrix)
    return contrastAndBrightness
}
