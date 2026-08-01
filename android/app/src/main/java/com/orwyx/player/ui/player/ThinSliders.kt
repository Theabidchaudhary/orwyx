package com.orwyx.player.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Hairline seek bar: a thin track with a small round dot thumb instead of
 * Material3's default fat pill thumb.
 *
 * [progress] is 0..1. While the user drags, the displayed position tracks the
 * finger directly (not the external [progress]) so it never fights the touch.
 */
@Composable
fun ThinSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 3.dp,
    thumbSize: Dp = 12.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = Color(0x40FFFFFF),
    thumbColor: Color = Color.White,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val shown = if (dragging) dragFraction else progress.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        onSeek(dragFraction)
                        onSeekFinished()
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                        onSeek(dragFraction)
                        change.consume()
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                    onSeekFinished()
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(inactiveColor),
        )
        Box(
            Modifier
                .fillMaxWidth(shown)
                .height(trackHeight)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(activeColor),
        )
        Box(
            Modifier
                .offset(x = maxWidth * shown - thumbSize / 2)
                .align(Alignment.CenterStart)
                .size(thumbSize)
                .clip(CircleShape)
                .background(thumbColor),
        )
    }
}

/**
 * A slider that snaps to fixed [step] increments across [range], with a small
 * tick dot rendered at every step — used for playback speed controls.
 */
@Composable
fun DottedSnapSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stepCount = ((range.endInclusive - range.start) / step).roundToInt()

    fun snap(fraction: Float): Float {
        val raw = range.start + fraction.coerceIn(0f, 1f) * (range.endInclusive - range.start)
        val snapped = (raw / step).roundToInt() * step
        return snapped.coerceIn(range.start, range.endInclusive)
    }

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .pointerInput(range, step) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        onValueChange(snap(change.position.x / size.width))
                        change.consume()
                    },
                )
            }
            .pointerInput(range, step) {
                detectTapGestures { offset -> onValueChange(snap(offset.x / size.width)) }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(Color(0x40FFFFFF)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(3.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(stepCount + 1) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0x99FFFFFF)))
            }
        }
        Box(
            Modifier
                .offset(x = maxWidth * fraction - 6.dp)
                .align(Alignment.CenterStart)
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Evenly spaced tick dots along a thin line — used by the long-press speed HUD (no touch input). */
@Composable
fun SnapTicks(count: Int, fraction: Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.height(12.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(Color(0x40FFFFFF)),
        )
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(3.dp)
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary),
        )
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            repeat(count) {
                Box(Modifier.size(4.dp).clip(CircleShape).background(Color(0x99FFFFFF)))
            }
        }
        Box(
            Modifier
                .offset(x = maxWidth * fraction.coerceIn(0f, 1f) - 5.dp)
                .align(Alignment.CenterStart)
                .size(10.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
