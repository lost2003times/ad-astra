package com.adastra.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.AppData
import com.adastra.app.data.Habit
import com.adastra.app.data.Repository
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import com.adastra.app.data.fmtHours
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private val CELL = 30.dp
private val DAY_COL = 32.dp
private val SLEEP_COL = 40.dp
private val HEADER_H = 104.dp

@Composable
fun GridScreen(repo: Repository, onOpenDay: (LocalDate) -> Unit) {
    val data by repo.data.collectAsStateWithLifecycle()
    val today = appToday()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val habits = data.habits.filter { !it.archived }
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val elapsed = days.count { Stats.counts(data, it, today) }
    val nonZero = days.count { Stats.counts(data, it, today) && Stats.isStep(data, it) }
    val hScroll = rememberScrollState()
    var history by remember { mutableStateOf<Habit?>(null) }
    history?.let { h -> HabitHistory(repo, h) { history = null } }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)) {
        item {
            ScreenTitle(
                title = month.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                subtitle = if (elapsed == 0) "No days logged this month" else "$nonZero of $elapsed ${if (elapsed == 1) "day" else "days"} without a zero",
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month", tint = Astra.Text)
                }
                val canNext = month.isBefore(YearMonth.from(today))
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = canNext) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month", tint = if (canNext) Astra.Text else Astra.Line)
                }
            }
        }
        item { SleepChart(data, days, today) }
        item { Spacer(Modifier.height(16.dp)); GridHeader(habits, hScroll) { history = it } }
        items(days, key = { it.toString() }) { d -> GridRow(data, habits, d, today, hScroll, onOpenDay) }
        item { Hint("Tap a day to read its page. Blue marks a rest day.", Modifier.padding(top = 14.dp)) }
    }
}

@Composable
private fun SideLabel(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(Modifier.width(width).height(HEADER_H), contentAlignment = Alignment.BottomCenter) {
        Text(
            text, color = Astra.Muted, fontSize = 13.sp, maxLines = 1, softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 6.dp).sideways(),
        )
    }
}

@Composable
private fun GridHeader(habits: List<Habit>, hScroll: ScrollState, onHabit: (Habit) -> Unit) {
    Row {
        Spacer(Modifier.width(DAY_COL))
        Row(Modifier.horizontalScroll(hScroll)) {
            habits.forEach { h -> Box(Modifier.clickable { onHabit(h) }) { SideLabel(h.name, CELL) } }
            SideLabel("Sleep", SLEEP_COL)
        }
    }
}

@Composable
private fun GridRow(
    data: AppData, habits: List<Habit>, d: LocalDate, today: LocalDate,
    hScroll: ScrollState, onOpenDay: (LocalDate) -> Unit,
) {
    val e = data.days[d.toString()]
    val future = d.isAfter(today)
    val counted = Stats.counts(data, d, today)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !future) { onOpenDay(d) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${d.dayOfMonth}",
            modifier = Modifier.width(DAY_COL),
            color = when {
                d == today -> Astra.Signal
                counted -> Astra.Text
                else -> Astra.Muted
            },
            fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp, style = Numbers,
        )
        Row(Modifier.horizontalScroll(hScroll)) {
            habits.forEach { h ->
                val on = e?.done?.contains(h.id) == true
                Box(
                    Modifier
                        .size(CELL)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            when {
                                on -> Astra.Ink
                                future -> Color.Transparent
                                e?.rest == true -> Astra.Rest.copy(alpha = 0.55f)
                                counted -> Astra.Empty
                                else -> Astra.Empty.copy(alpha = 0.45f)
                            }
                        )
                )
            }
            Box(Modifier.width(SLEEP_COL).height(CELL), contentAlignment = Alignment.Center) {
                Text(e?.sleep?.let { fmtHours(it) } ?: "", color = Astra.Text, fontSize = 12.sp, style = Numbers)
            }
        }
    }
}

@Composable
private fun SleepChart(data: AppData, days: List<LocalDate>, today: LocalDate) {
    val values = days.map { d -> if (d.isAfter(today)) null else data.days[d.toString()]?.sleep }
    val logged = values.filterNotNull()
    Section("Sleep", trailing = if (logged.isEmpty()) null else "Average ${fmtHours(logged.average().toFloat())} h") {
        if (logged.isEmpty()) {
            Hint("Log sleep on the Today tab to see the month's curve.")
        } else {
            Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                val minH = 3f; val maxH = 12f
                val stepX = size.width / (days.size - 1).coerceAtLeast(1)
                fun y(h: Float) = size.height * (1f - (h - minH) / (maxH - minH))
                drawLine(
                    Astra.Line, Offset(0f, y(8f)), Offset(size.width, y(8f)),
                    strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
                val pts = values.mapIndexedNotNull { i, v -> v?.let { Offset(i * stepX, y(it)) } }
                val path = Path()
                pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                drawPath(path, Astra.Ink, style = Stroke(width = 2.dp.toPx()))
                pts.forEach { drawCircle(Astra.Ink, 3.dp.toPx(), it) }
            }
            Hint("Dashed line marks 8 hours.", Modifier.padding(top = 6.dp))
        }
    }
}
