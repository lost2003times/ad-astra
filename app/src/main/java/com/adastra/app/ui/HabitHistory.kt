package com.adastra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.Habit
import com.adastra.app.data.Repository
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** Every day a habit was ticked, as a grid of weeks (columns) by weekdays (rows). */
@Composable
fun HabitHistory(repo: Repository, habit: Habit, onClose: () -> Unit) {
    val data by repo.data.collectAsStateWithLifecycle()
    val today = appToday()
    val ticked = data.days.values.filter { habit.id in it.done }.map { LocalDate.parse(it.date) }.toSet()
    val first = listOfNotNull(ticked.minOrNull(), Stats.startDate(data, today)).min()
    val lastWeek = Stats.weekStart(today)
    val firstWeek = minOf(Stats.weekStart(first), lastWeek.minusWeeks(19))
    val weeks = (ChronoUnit.WEEKS.between(firstWeek, lastWeek) + 1).toInt()

    var streak = 0
    var d = if (today in ticked) today else today.minusDays(1)
    while (d in ticked) { streak++; d = d.minusDays(1) }
    var best = 0; var run = 0; var prev: LocalDate? = null
    ticked.sorted().forEach { t -> run = if (prev != null && t == prev!!.plusDays(1)) run + 1 else 1; best = maxOf(best, run); prev = t }
    val last30 = ticked.count { !it.isAfter(today) && it.isAfter(today.minusDays(30)) }

    val cell = 18.dp
    val gap = 3.dp
    val scroll = rememberScrollState()
    LaunchedEffect(weeks) { scroll.scrollTo(scroll.maxValue) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Astra.Bg).statusBarsPadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ScreenTitle(habit.name, if (habit.archived) "Hidden habit" else "Every day you ticked it") {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = Astra.Muted) }
            }
            Row(Modifier.padding(vertical = 12.dp)) {
                HistStat("Total", "${ticked.size}", Modifier.weight(1f))
                HistStat("Last 30 days", "$last30", Modifier.weight(1f))
                HistStat("Streak", "$streak", Modifier.weight(1f))
                HistStat("Best run", "$best", Modifier.weight(1f))
            }
            Section("Grid", trailing = "$weeks weeks") {
                Row {
                    // Weekday labels
                    Column(Modifier.padding(top = 18.dp, end = 6.dp)) {
                        listOf("M", "", "W", "", "F", "", "S").forEach {
                            Box(Modifier.height(cell + gap), contentAlignment = Alignment.CenterStart) {
                                Text(it, color = Astra.Muted, fontSize = 11.sp)
                            }
                        }
                    }
                    Row(Modifier.horizontalScroll(scroll)) {
                        (0 until weeks).forEach { w ->
                            val ws = firstWeek.plusWeeks(w.toLong())
                            Column(Modifier.padding(end = gap)) {
                                // Month name above the first week of each month
                                val label = if (ws.dayOfMonth <= 7) ws.format(DateTimeFormatter.ofPattern("MMM")) else ""
                                Box(Modifier.height(18.dp).width(cell)) {
                                    Text(label, color = Astra.Muted, fontSize = 11.sp, softWrap = false, maxLines = 1)
                                }
                                (0..6).forEach { i ->
                                    val day = ws.plusDays(i.toLong())
                                    val color = when {
                                        day.isAfter(today) -> Astra.Bg
                                        day in ticked -> Astra.Ink
                                        else -> Astra.Empty
                                    }
                                    Box(
                                        Modifier.padding(bottom = gap).size(cell).clip(RoundedCornerShape(3.dp)).background(color)
                                            .then(if (day == today) Modifier.background(Astra.Signal.copy(alpha = 0.35f)) else Modifier),
                                    )
                                }
                            }
                        }
                    }
                }
                Hint("Filled squares are days you ticked ${habit.name}. Today is tinted amber.", Modifier.padding(top = 6.dp))
            }
            Section("By month") {
                val months = (5 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
                val counts = months.map { m -> ticked.count { YearMonth.from(it) == m } }
                months.forEachIndexed { i, m ->
                    val days = if (m == YearMonth.from(today)) today.dayOfMonth else m.lengthOfMonth()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.format(DateTimeFormatter.ofPattern("MMM yy")), color = Astra.Muted, fontSize = 13.sp, modifier = Modifier.width(64.dp))
                        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(3.dp)).background(Astra.Empty)) {
                            Box(Modifier.fillMaxWidth(counts[i] / days.toFloat()).height(10.dp).background(Astra.Ink))
                        }
                        Text("${counts[i]} / $days", color = Astra.Text, fontSize = 13.sp, style = Numbers,
                            modifier = Modifier.width(64.dp).padding(start = 8.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HistStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = Astra.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, style = Numbers)
        Text(label, color = Astra.Muted, fontSize = 12.sp)
    }
}
