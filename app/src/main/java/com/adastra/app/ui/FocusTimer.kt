package com.adastra.app.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adastra.app.data.AppData
import com.adastra.app.data.KIND_BREAK
import com.adastra.app.data.KIND_STUDY
import com.adastra.app.notify.Focus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

/** Study timer (phone goes quiet) and break timer, with a stamp-and-ring animation while running. */
@Composable
fun FocusBlock(d: AppData, now: Long, ctx: Context, dnd: Boolean) {
    val t = d.timer?.takeIf { it.end != null }
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp)).background(Astra.Surface).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (t != null) Running(t.kind, t.start, t.end!!, now, ctx) else Setup(ctx, dnd)
    }
}

@Composable
private fun Running(kind: String, start: Long, end: Long, now: Long, ctx: Context) {
    val isBreak = kind == KIND_BREAK
    val color = if (isBreak) Astra.Rest else Astra.Signal
    val total = (end - start).coerceAtLeast(1)
    val left = (end - now).coerceAtLeast(0)
    val frac = left / total.toFloat()

    // Entry: a seal stamps down, then the ring draws itself in.
    val stamp = remember(start) { Animatable(0f) }
    val draw = remember(start) { Animatable(0f) }
    LaunchedEffect(start) {
        val fresh = System.currentTimeMillis() - start < 4_000
        if (!fresh) { stamp.snapTo(2f); draw.snapTo(1f); return@LaunchedEffect }
        stamp.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f))
        kotlinx.coroutines.delay(350)
        stamp.animateTo(2f, tween(350))
        draw.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    // Slow turning tick ring and a breathing glow on the arc's head.
    val inf = rememberInfiniteTransition(label = "focus")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (isBreak) 30_000 else 60_000, easing = LinearEasing)), label = "spin")
    val pulse by inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")

    Label(if (isBreak) "Break · sound on" else "Studying · phone on Do Not Disturb", color = if (isBreak) Astra.Muted else Astra.Signal)
    Box(Modifier.size(270.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val r = size.minDimension / 2 - stroke
            val c = Offset(size.width / 2, size.height / 2)
            val tl = Offset(c.x - r, c.y - r)
            val box = Size(r * 2, r * 2)
            // turning tick marks, like a dial
            rotate(spin, c) {
                val outer = r + stroke * 1.15f
                for (i in 0 until 60) {
                    val a = Math.toRadians(i * 6.0)
                    val len = if (i % 5 == 0) 10f else 4f
                    drawLine(
                        Astra.Line, Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                        Offset(c.x + cos(a).toFloat() * (outer + len), c.y + sin(a).toFloat() * (outer + len)), strokeWidth = 2f,
                    )
                }
            }
            drawArc(Astra.Empty, 0f, 360f, false, tl, box, style = Stroke(stroke))
            val sweep = 360f * frac * draw.value
            drawArc(color, -90f, sweep, false, tl, box, style = Stroke(stroke, cap = StrokeCap.Round))
            if (sweep > 1f) {
                val a = Math.toRadians((-90f + sweep).toDouble())
                val head = Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
                drawCircle(color.copy(alpha = 0.25f * pulse), stroke * 1.6f, head)
                drawCircle(Astra.Text, stroke * 0.32f, head)
            }
            drawCircle(Astra.Line, r - stroke * 1.4f, c, style = Stroke(1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f))))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.alpha(draw.value)) {
            val s = left / 1000
            Text(
                if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60),
                color = Astra.Text, fontSize = 50.sp, lineHeight = 54.sp, style = Score,
            )
            val ends = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
            Text("until $ends", color = Astra.Muted, fontSize = 13.sp)
        }
        // The seal: drops in big and tilted, lands, then fades as the ring appears.
        val st = stamp.value
        if (st < 2f) {
            val land = st.coerceAtMost(1f)
            val fade = if (st > 1f) 2f - st else 1f
            Box(
                Modifier.size(120.dp)
                    .scale(1.7f - 0.7f * land)
                    .rotate(-24f + 16f * land)
                    .alpha(land * fade)
                    .clip(RoundedCornerShape(12.dp)).background(color),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isBreak) "REST" else "FOCUS", color = Astra.Bg, fontSize = 24.sp, letterSpacing = 3.sp, style = Score)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Pill("End now", false, Astra.Text) { Focus.end(ctx, completed = false) }
        if (!isBreak) Pill("Take a break", false, Astra.Text) { Focus.start(ctx, 10, KIND_BREAK) }
    }
}

@Composable
private fun Setup(ctx: Context, dnd: Boolean) {
    var minutes by remember { mutableIntStateOf(60) }
    var typing by remember { mutableStateOf(false) }
    Label("Study timer")
    TimeDial(minutes, { minutes = it.coerceAtLeast(5) }, Astra.Signal, Modifier.padding(vertical = 6.dp), caption = "1 turn = 1 hour")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(60, 120, 240, 360, 480).forEach { m -> Pill(fmtLength(m), minutes == m, Astra.Ink, Modifier.weight(1f)) { minutes = m } }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Pill("− 15", false, Astra.Ink, Modifier.weight(1f)) { minutes = (minutes - 15).coerceAtLeast(5) }
        Pill("Type", false, Astra.Ink, Modifier.weight(1f)) { typing = true }
        Pill("+ 15", false, Astra.Ink, Modifier.weight(1f)) { minutes = (minutes + 15).coerceAtMost(24 * 60) }
    }
    Spacer(Modifier.height(14.dp))
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(12.dp)).background(Astra.Text)
            .clickable { Focus.start(ctx, minutes, KIND_STUDY) },
        contentAlignment = Alignment.Center,
    ) { Text("Start · phone goes silent", color = Astra.Bg, fontSize = 16.sp, fontWeight = FontWeight.Bold) }

    Spacer(Modifier.height(18.dp))
    Label("Break")
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5, 10, 15).forEach { m ->
            Pill("$m min", false, Astra.Rest, Modifier.weight(1f)) { Focus.start(ctx, m, KIND_BREAK) }
        }
    }
    Hint("A break keeps sound on and tells you when it's time to get back to it.", Modifier.padding(top = 6.dp))

    if (!dnd) {
        Hint("To silence the phone, Android needs you to allow Do Not Disturb access for Ad Astra once.", Modifier.padding(top = 12.dp))
        TextButton(onClick = { ctx.startActivity(Focus.dndSettingsIntent()) }) { Text("Allow Do Not Disturb access", color = Astra.Signal) }
    }

    if (typing) {
        var h by remember { mutableStateOf((minutes / 60).toString()) }
        var m by remember { mutableStateOf((minutes % 60).toString()) }
        val total = (h.toIntOrNull() ?: 0) * 60 + (m.toIntOrNull() ?: 0)
        AlertDialog(
            onDismissRequest = { typing = false },
            title = { Text("How long?") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
                    OutlinedTextField(h, { h = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Hours") }, singleLine = true, colors = fieldColors(), keyboardOptions = num)
                    OutlinedTextField(m, { m = it.filter(Char::isDigit).take(2) }, Modifier.weight(1f), label = { Text("Minutes") }, singleLine = true, colors = fieldColors(), keyboardOptions = num)
                }
            },
            confirmButton = {
                TextButton(enabled = total in 1..(24 * 60), onClick = { minutes = total; typing = false }) { Text("Set") }
            },
            dismissButton = { TextButton(onClick = { typing = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Pill(text: String, on: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(CircleShape).background(if (on) color else Astra.Empty).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (on) (if (color == Astra.Ink) Astra.Bg else Astra.Text) else Astra.Text, fontSize = 14.sp, maxLines = 1)
    }
}

fun fmtLength(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60}"
}
