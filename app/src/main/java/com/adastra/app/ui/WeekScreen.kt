package com.adastra.app.ui

import com.adastra.app.data.Report
import com.adastra.app.usage.PhoneUsage
import com.adastra.app.usage.UsageReport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.DayState
import com.adastra.app.data.Repository
import com.adastra.app.data.Review
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import com.adastra.app.data.fmtHours
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun WeekScreen(repo: Repository) {
    val data by repo.data.collectAsStateWithLifecycle()
    val today = appToday()
    val thisWeek = Stats.weekStart(today)
    var start by remember { mutableStateOf(thisWeek) }
    val isCurrent = start == thisWeek
    val cur = Stats.week(data, start, today)
    val prev = Stats.week(data, start.minusWeeks(1), today)
    val review = Review.build(data, cur, prev, Stats.currentStreak(data, today), isCurrent)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Phone use for this week and the one before (Android keeps about two weeks of it).
    val phone by androidx.compose.runtime.produceState<Pair<UsageReport, UsageReport>?>(null, start, data.sessions.size) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { weekUsage(ctx, data, start, today) }.getOrNull()
        }
    }
    val report = remember(data, start, phone) { Report.build(data, start, today, phone?.first, phone?.second) }
    val lastReport = remember(data, start) { if (isCurrent) Report.build(data, start.minusWeeks(1), today) else null }
    val habits = data.habits.filter { !it.archived }
    val fmt = DateTimeFormatter.ofPattern("d MMM")
    val days = (0L..6L).map { start.plusDays(it) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            ScreenTitle(
                title = if (isCurrent) "This week" else "Week of ${start.format(fmt)}",
                subtitle = "${start.format(fmt)} to ${start.plusDays(6).format(fmt)}",
            ) {
                IconButton(onClick = { start = start.minusWeeks(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week", tint = Astra.Text)
                }
                IconButton(onClick = { start = start.plusWeeks(1) }, enabled = !isCurrent) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week", tint = if (!isCurrent) Astra.Text else Astra.Line)
                }
            }
        }
        lastReport?.takeIf { it.study + it.shift > 0f }?.let { r ->
            item { LastWeekLink(r) { start = start.minusWeeks(1) } }
        }
        item { ReportCardView(report) }
        item {
            Column(Modifier.padding(top = 16.dp, bottom = 16.dp)) {
                if (cur.elapsed == 0) {
                    Text("Nothing logged yet this week.", color = Astra.Text, fontSize = 18.sp)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${cur.nonZero}", color = Astra.Signal, fontSize = 52.sp, fontWeight = FontWeight.Bold, lineHeight = 52.sp, style = Numbers)
                        Text(" of ${cur.elapsed}", color = Astra.Muted, fontSize = 22.sp, modifier = Modifier.padding(bottom = 6.dp), style = Numbers)
                    }
                    Text("days without a zero", color = Astra.Text, fontSize = 15.sp)
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    cur.states.forEachIndexed { i, s ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            DayCell(s, 38.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(dayLetter(days[i]), color = if (days[i] == today) Astra.Signal else Astra.Muted, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row {
                    Stat("Habit check-ins", "${cur.totalSteps}", Modifier.weight(1f))
                    Stat("Average sleep", cur.sleepAvg?.let { "${fmtHours(it)} h" } ?: "–", Modifier.weight(1f))
                    Stat("Rest days", "${cur.restDays}", Modifier.weight(1f))
                }
            }
        }
        item { Section("What went well") { review.wins.forEach { Point(it) } } }
        item { Section("What to work on") { review.grow.forEach { Point(it, Astra.Muted) } } }
        if (habits.isNotEmpty()) item {
            Section("Habits", trailing = "vs last week") {
                habits.forEach { h ->
                    val c = cur.perHabit[h.id] ?: 0
                    val diff = c - (prev.perHabit[h.id] ?: 0)
                    Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(h.name, color = Astra.Text, fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        days.forEach { d ->
                            val on = data.days[d.toString()]?.done?.contains(h.id) == true
                            Box(
                                Modifier.padding(2.dp).size(14.dp).clip(RoundedCornerShape(2.dp))
                                    .background(if (on) Astra.Ink else Astra.Empty)
                            )
                        }
                        Text(
                            "$c" + when { diff > 0 -> " +$diff"; diff < 0 -> " −${-diff}"; else -> "" },
                            color = when { diff > 0 -> Astra.Text; else -> Astra.Muted },
                            fontSize = 13.sp, style = Numbers,
                            modifier = Modifier.width(52.dp).padding(start = 10.dp),
                        )
                    }
                }
            }
        }
        if (cur.notes.isNotEmpty()) item {
            Section("Notes") {
                cur.notes.forEach { (d, n) ->
                    Row(Modifier.padding(vertical = 4.dp)) {
                        Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), color = Astra.Muted, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                        Text(n, color = Astra.Text, fontSize = 15.sp, fontFamily = Serif, lineHeight = 21.sp)
                    }
                }
            }
        }
        item {
            val thisFocus = data.focus[start.toString()]
            val nextKey = start.plusWeeks(1)
            var text by remember(start) { mutableStateOf(data.focus[nextKey.toString()] ?: "") }
            if (!thisFocus.isNullOrBlank()) {
                Section("Focus for this week") { Text(thisFocus, color = Astra.Text, fontSize = 15.sp) }
            }
            Section("Focus for next week") {
                Hint("One concrete goal with a number in it.", Modifier.padding(bottom = 8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it; repo.setFocus(nextKey, it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Gym 4 days, 30 minutes minimum", color = Astra.Muted) },
                    colors = fieldColors(),
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun weekUsage(ctx: android.content.Context, d: com.adastra.app.data.AppData, start: LocalDate, today: LocalDate): Pair<UsageReport, UsageReport>? {
    if (!PhoneUsage.hasAccess(ctx) || start.isBefore(today.minusDays(13))) return null
    val zone = java.time.ZoneId.systemDefault()
    fun at(day: LocalDate) = day.atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
    val now = System.currentTimeMillis()
    val study = d.focusRuns.map { it.start to it.end }
    val days = (java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, 7)
    val week = PhoneUsage.report(ctx, at(start), minOf(now, at(start.plusDays(7))), days, study)
    val prev = PhoneUsage.report(ctx, at(start.minusDays(7)), at(start), 7, study)
    return week to prev
}

private fun dayLetter(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = Astra.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = Numbers)
        Text(label, color = Astra.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DayCell(s: DayState, size: Dp) {
    val shape = RoundedCornerShape(5.dp)
    val m = Modifier.size(size).clip(shape)
    when (s) {
        DayState.STEP -> Box(m.background(Astra.Ink))
        DayState.REST -> Box(m.background(Astra.Rest))
        DayState.ZERO -> Box(m.border(1.5.dp, Astra.Line, shape))
        DayState.FUTURE -> Box(m.background(Color.Transparent).border(1.dp, Astra.Empty, shape))
    }
}
