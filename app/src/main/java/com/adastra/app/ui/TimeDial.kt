package com.adastra.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Drag a finger around the ring to set a length: one full turn is an hour, and it keeps going
 * for as many turns as you like (up to [max]). Liquid fills the dial as the hour fills, and a dot
 * is added outside the ring for every full hour.
 */
@Composable
fun TimeDial(
    minutes: Int,
    onChange: (Int) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 250.dp,
    max: Int = 24 * 60,
    step: Int = 5,
    caption: String = "drag around the ring",
) {
    val haptic = LocalHapticFeedback.current
    val current by rememberUpdatedState(minutes)
    val emit by rememberUpdatedState(onChange)
    // The finger's running total, kept as a float so slow drags still add up.
    val exact = remember { mutableFloatStateOf(minutes.toFloat()) }

    // Smooth, slightly springy follow of the value: this is what makes the liquid slosh.
    val shown by animateFloatAsState(minutes.toFloat(), spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow), label = "dial")
    val inf = rememberInfiniteTransition(label = "wave")
    val phase by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "phase")

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(max, step) {
                var last = 0f
                fun angle(p: Offset): Float {
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    return Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                }
                detectDragGestures(
                    onDragStart = { p -> last = angle(p); exact.floatValue = current.toFloat() },
                ) { change, _ ->
                    change.consume()
                    val a = angle(change.position)
                    var d = a - last
                    if (d > 180f) d -= 360f
                    if (d < -180f) d += 360f
                    last = a
                    exact.floatValue = (exact.floatValue + d / 360f * 60f).coerceIn(0f, max.toFloat())
                    val snapped = ((exact.floatValue / step).roundToInt() * step).coerceIn(0, max)
                    if (snapped != current) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        emit(snapped)
                    }
                }
            }
        ) {
            val stroke = 16.dp.toPx()
            val r = this.size.minDimension / 2f - stroke * 1.6f
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val laps = floor(shown / 60f).toInt()
            val frac = (shown / 60f) - laps

            // Liquid inside the dial, two waves for depth.
            val inner = r - stroke * 0.9f
            val disc = Path().apply { addOval(Rect(c, inner)) }
            clipPath(disc) {
                drawCircle(Astra.Empty, inner, c)
                val level = if (shown <= 0f) 0f else (frac.takeIf { it > 0.001f } ?: 1f)
                val top = c.y + inner - 2 * inner * level
                val amp = 9.dp.toPx() * (0.35f + 0.65f * (1 - kotlin.math.abs(level - 0.5f) * 2))
                for ((k, alpha, shift) in listOf(Triple(1f, 0.16f, 0f), Triple(1.4f, 0.34f, 1.7f))) {
                    val wave = Path().apply {
                        moveTo(c.x - inner, this@Canvas.size.height)
                        var x = c.x - inner
                        while (x <= c.x + inner) {
                            val y = top + amp * sin((x / inner) * 2.2f * k + phase * k + shift)
                            lineTo(x, y); x += 4f
                        }
                        lineTo(c.x + inner, this@Canvas.size.height); close()
                    }
                    drawPath(wave, color.copy(alpha = alpha))
                }
            }

            // The ring and this hour's arc.
            drawCircle(Astra.Line, r, c, style = Stroke(stroke))
            val sweep = if (shown > 0f && frac < 0.001f) 360f else frac * 360f
            drawArc(color, -90f, sweep, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            // The handle you drag.
            val a = Math.toRadians((-90f + sweep).toDouble())
            val knob = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
            drawCircle(color.copy(alpha = 0.25f), stroke * 1.25f, knob)
            drawCircle(Astra.Text, stroke * 0.62f, knob)
            // One dot outside the ring per full hour (up to 24).
            val dotR = r + stroke * 1.25f
            for (i in 0 until laps.coerceAtMost(24)) {
                val t = Math.toRadians((-90.0 + i * 15.0))
                drawCircle(color, 3.4.dp.toPx(), Offset(c.x + cos(t).toFloat() * dotR, c.y + sin(t).toFloat() * dotR))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmtLength(shown.roundToInt()), color = Astra.Text, fontSize = 44.sp, lineHeight = 48.sp, style = Score.copy(shadow = androidx.compose.ui.graphics.Shadow(Astra.Bg, Offset(0f, 2f), 10f)))
            Text(caption, color = Astra.Text.copy(alpha = 0.7f), fontSize = 12.sp, style = Score.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal, shadow = androidx.compose.ui.graphics.Shadow(Astra.Bg, Offset(0f, 1f), 6f)))
        }
    }
}
