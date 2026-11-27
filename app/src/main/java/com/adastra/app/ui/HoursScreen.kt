package com.adastra.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.AppData
import com.adastra.app.data.HoursSummary
import com.adastra.app.data.Hours
import com.adastra.app.data.KIND_SHIFT
import com.adastra.app.data.KIND_STUDY
import com.adastra.app.data.Repository
import com.adastra.app.data.Session
import com.adastra.app.data.appToday
import com.adastra.app.data.fmtDuration
import com.adastra.app.usage.Advisor
import com.adastra.app.usage.PhoneUsage
import com.adastra.app.usage.UsageReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val StudyColor = Astra.Ink
private val ShiftColor = Astra.Signal

@Composable
fun HoursScreen(repo: Repository) {
    val data by repo.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    // A live clock while a timer runs.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(data.timer) {
        while (data.timer != null) { now = System.currentTimeMillis(); delay(1000) }
        now = System.currentTimeMillis()
    }
    val h = remember(data, now / 60_000) { Hours.summary(data, now = now) }

    // Phone usage: re-read whenever the screen comes back (e.g. after granting access).
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    val access = remember(resumed) { PhoneUsage.hasAccess(ctx) }
    val usage by produceState<Pair<UsageReport, UsageReport>?>(null, resumed, access, data.sessions.size) {
        value = if (!access) null else withContext(Dispatchers.Default) {
            runCatching { readUsage(ctx, data) }.getOrNull()
        }
    }
    val sleepAvg = remember(data) {
        val t = appToday()
        (0L..6L).mapNotNull { data.days[t.minusDays(it).toString()]?.sleep }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
    }
    val advice = remember(h, usage, sleepAvg) { Advisor.build(h, usage?.first, usage?.second, sleepAvg) }

    var adding by remember { mutableStateOf(false) }
    var goals by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Session?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            ScreenTitle("Hours", "${h.yearGoal} hours of study in a year") {
                IconButton(onClick = { goals = true }) { Icon(Icons.Filled.Edit, "Goals", tint = Astra.Muted) }
            }
        }
        item { TimerBlock(data, now, repo) { adding = true } }
        item { YearBlock(h) }
        item { WeekBlock(h) }
        if (advice.wins.isNotEmpty()) item { AdviceCard("What's working", advice.wins, Astra.Ink) }
        if (advice.leaks.isNotEmpty()) item { AdviceCard("Where the time goes", advice.leaks, Astra.Signal) }
        if (advice.plan.isNotEmpty()) item { AdviceCard("The plan", advice.plan, Astra.Muted) }
        item { PhoneBlock(access, usage?.first) { ctx.startActivity(PhoneUsage.settingsIntent(ctx)) } }
        item { WeeksChart(h) }
        val recent = data.sessions.sortedByDescending { it.start }.take(12)
        if (recent.isNotEmpty()) {
            item { Section("Recent", trailing = "${data.sessions.size} logged") {} }
            items(recent, key = { it.id }) { s -> SessionRow(s) { deleting = s } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (adding) AddTimeDialog(onDismiss = { adding = false }) { kind, date, minutes ->
        repo.addSession(kind, date, minutes); adding = false
    }
    if (goals) GoalsDialog(data, onDismiss = { goals = false }) { year, study, shift, restart ->
        repo.setGoals { g ->
            g.copy(studyYear = year, studyWeek = study, shiftWeek = shift,
                yearStart = if (restart) appToday().toString() else g.yearStart)
        }
        goals = false
    }
    deleting?.let { s ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete this entry?") },
            text = { Text("${kindName(s.kind)}, ${fmtDuration(s.minutes / 60f)} on ${LocalDate.parse(s.date).format(dayFmt)}.") },
            confirmButton = { TextButton(onClick = { repo.deleteSession(s.id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep") } },
        )
    }
}

private fun readUsage(ctx: android.content.Context, d: AppData): Pair<UsageReport, UsageReport> {
    val zone = ZoneId.systemDefault()
    val nowMs = System.currentTimeMillis()
    val study = d.sessions.filter { it.kind == KIND_STUDY && !it.manual }.map { it.start to it.start + it.minutes * 60_000L } +
        listOfNotNull(d.timer?.takeIf { it.kind == KIND_STUDY }?.let { it.start to nowMs })
    val weekFrom = appToday().minusDays(6).atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
    val prevFrom = appToday().minusDays(13).atTime(4, 0).atZone(zone).toInstant().toEpochMilli()
    val week = PhoneUsage.report(ctx, weekFrom, nowMs, 7, study)
    val prev = PhoneUsage.report(ctx, prevFrom, weekFrom, 7, study)
    return week to prev
}

/** Advice as a card with a colored edge, like a margin note. */
@Composable
private fun AdviceCard(title: String, lines: List<String>, edge: Color) {
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .clip(RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp, topStart = 4.dp, bottomStart = 4.dp)).background(Astra.Surface),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(edge))
        Column(Modifier.padding(14.dp)) {
            Label(title)
            Spacer(Modifier.height(4.dp))
            lines.forEach { Text(it, color = Astra.Text, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM")
private fun kindName(k: String) = if (k == KIND_STUDY) "Study" else "Shift"

@Composable
private fun Bar(frac: Float, color: Color, height: Dp = 10.dp) {
    BrushBar(frac, color, height = height)
}

@Composable
private fun TimerBlock(d: AppData, now: Long, repo: Repository, onAdd: () -> Unit) {
    val t = d.timer
    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(8.dp)).background(Astra.Surface).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (t != null) {
            val secs = ((now - t.start) / 1000).coerceAtLeast(0)
            Text(if (t.kind == KIND_STUDY) "Studying" else "On shift", color = if (t.kind == KIND_STUDY) StudyColor else ShiftColor, fontSize = 16.sp)
            Text(
                "%d:%02d:%02d".format(secs / 3600, (secs / 60) % 60, secs % 60),
                color = Astra.Text, fontSize = 56.sp, style = Score,
            )
            val started = Instant.ofEpochMilli(t.start).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
            Hint("Started at $started. The clock keeps running if you close the app.")
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { repo.stopTimer() }, colors = ButtonDefaults.buttonColors(containerColor = Astra.Ink)) { Text("Stop and log") }
                OutlinedButton(onClick = { repo.discardTimer() }) { Text("Discard", color = Astra.Muted) }
            }
        } else {
            Text("Start the clock", color = Astra.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Hint("Timed hours are the real ones. Phone use while studying shows up below.", Modifier.padding(top = 2.dp, bottom = 12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { repo.startTimer(KIND_STUDY) }, colors = ButtonDefaults.buttonColors(containerColor = StudyColor)) { Text("Study") }
                Button(onClick = { repo.startTimer(KIND_SHIFT) }, colors = ButtonDefaults.buttonColors(containerColor = ShiftColor, contentColor = Astra.Text)) { Text("Shift") }
            }
            TextButton(onClick = onAdd) { Text("Add time by hand", color = Astra.Muted) }
        }
    }
}

@Composable
private fun YearBlock(h: HoursSummary) {
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy")
    Section("The year", trailing = "day ${h.dayOfYear} of ${h.dayOfYear + h.daysLeft}") {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(String.format("%.1f", h.yearStudy), color = Astra.Text, fontSize = 56.sp, lineHeight = 56.sp, style = Score)
            Text(" / ${h.yearGoal} h", color = Astra.Muted, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Box(Modifier.fillMaxWidth().height(14.dp)) {
            Bar(h.yearStudy / h.yearGoal, StudyColor, 14.dp)
            // Where an even pace would be today.
            val pace = (h.expectedByToday / h.yearGoal).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width * pace
                drawLine(Astra.Text, Offset(x, -4f), Offset(x, size.height + 4f), strokeWidth = 3f)
            }
        }
        Spacer(Modifier.height(8.dp))
        val diff = h.yearStudy - h.expectedByToday
        Text(
            if (diff >= 0) "${fmtDuration(diff)} ahead of pace" else "${fmtDuration(-diff)} behind pace",
            color = if (diff >= 0) Astra.Text else Astra.Signal, fontSize = 15.sp,
        )
        Hint("The white mark is where an even pace would be as today began (${String.format("%.1f", h.expectedByToday)} h). The year ends ${h.yearEnd.format(fmt)}.")
    }
}

@Composable
private fun WeekBlock(h: HoursSummary) {
    Section("This week", trailing = "${h.weekDaysLeft} ${if (h.weekDaysLeft == 1) "day" else "days"} left") {
        GoalLine("Study", h.weekStudy, h.weekStudyGoal, StudyColor)
        Spacer(Modifier.height(10.dp))
        GoalLine("Shift", h.weekShift, h.weekShiftGoal, ShiftColor)
        Spacer(Modifier.height(14.dp))
        val max = maxOf(4f, h.weekDays.maxOf { it.first + it.second })
        val todayIdx = (appToday().dayOfWeek.value - 1)
        Canvas(Modifier.fillMaxWidth().height(90.dp)) {
            val slot = size.width / 7f
            val w = slot * 0.5f
            h.weekDays.forEachIndexed { i, (st, sh) ->
                val x = slot * i + (slot - w) / 2
                val hs = size.height * st / max
                val hh = size.height * sh / max
                drawRect(Astra.Empty, Offset(x, 0f), Size(w, size.height))
                drawRect(StudyColor, Offset(x, size.height - hs), Size(w, hs))
                drawRect(ShiftColor, Offset(x, size.height - hs - hh), Size(w, hh))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, l ->
                Text(l, Modifier.weight(1f), color = if (i == todayIdx) Astra.Signal else Astra.Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        Hint("Today: ${fmtDuration(h.todayStudy)} study, ${fmtDuration(h.todayShift)} shift.", Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun GoalLine(label: String, value: Float, goal: Int, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = Astra.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text("${fmtDuration(value)} / $goal h", color = if (value >= goal) color else Astra.Muted, fontSize = 14.sp, style = Numbers)
    }
    Spacer(Modifier.height(4.dp))
    Bar(value / goal, color)
}

@Composable
private fun PhoneBlock(access: Boolean, week: UsageReport?, onGrant: () -> Unit) {
    Section("Phone", trailing = if (week != null) "last 7 days" else null) {
        if (!access) {
            Text("See where your phone time goes", color = Astra.Text, fontSize = 15.sp)
            Hint(
                "Ad Astra can read the same screen-time numbers as Digital Wellbeing: time per app, unlocks, and late-night use. " +
                    "It stays on this phone. Android asks you to switch this on yourself: find Ad Astra on the next screen and allow usage access.",
                Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Astra.Ink)) { Text("Allow usage access") }
            return@Section
        }
        if (week == null) { Hint("Reading screen time…"); return@Section }
        val today = week.perDay[appToday()] ?: 0f
        Row {
            PhoneStat("Today", fmtDuration(today / 60f), Modifier.weight(1f))
            PhoneStat("Daily average", fmtDuration(week.dailyAvg / 60f), Modifier.weight(1f))
            PhoneStat("Unlocks a day", "${week.unlocks / week.days}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        val top = week.apps.take(7)
        val max = top.firstOrNull()?.minutes ?: 1f
        top.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row {
                        Text(a.label, color = Astra.Text, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(fmtDuration(a.minutes / 60f), color = Astra.Muted, fontSize = 13.sp, style = Numbers)
                    }
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { Bar(a.minutes / max, if (a.cat.distracting) Astra.Signal else Astra.Rest, 6.dp) }
                        Text(a.cat.label, color = Astra.Muted, fontSize = 11.sp, modifier = Modifier.width(80.dp).padding(start = 8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Through the day", color = Astra.Muted, fontSize = 13.sp)
        val hmax = maxOf(1f, week.perHour.max())
        Canvas(Modifier.fillMaxWidth().height(48.dp).padding(top = 4.dp)) {
            val slot = size.width / 24f
            week.perHour.forEachIndexed { i, m ->
                val bh = size.height * m / hmax
                val late = i >= 23 || i < 4
                drawRect(if (late) Astra.Signal else Astra.Rest, Offset(slot * i + 1f, size.height - bh), Size(slot - 2f, bh))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("12a", "6a", "12p", "6p").forEach { Text(it, Modifier.weight(1f), color = Astra.Muted, fontSize = 11.sp) }
        }
        Hint("Amber bars are social, video and games, and phone use between 11 PM and 4 AM.", Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PhoneStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = Astra.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = Numbers)
        Text(label, color = Astra.Muted, fontSize = 12.sp)
    }
}

@Composable
private fun WeeksChart(h: HoursSummary) {
    Section("Last 8 weeks") {
        val goal = maxOf(h.weekStudyGoal, h.weekShiftGoal).toFloat()
        val max = maxOf(goal * 1.2f, h.lastWeeks.maxOf { maxOf(it.study, it.shift) })
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val slot = size.width / h.lastWeeks.size
            val w = slot * 0.3f
            h.lastWeeks.forEachIndexed { i, wk ->
                val x = slot * i + slot * 0.15f
                val hs = size.height * wk.study / max
                val hh = size.height * wk.shift / max
                drawRect(StudyColor, Offset(x, size.height - hs), Size(w, hs))
                drawRect(ShiftColor, Offset(x + w + 2f, size.height - hh), Size(w, hh))
            }
            val y = size.height - size.height * h.weekStudyGoal / max
            drawLine(Astra.Muted, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
        }
        val fmt = DateTimeFormatter.ofPattern("d/M")
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            h.lastWeeks.forEach { Text(it.start.format(fmt), Modifier.weight(1f), color = Astra.Muted, fontSize = 11.sp, textAlign = TextAlign.Center) }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(StudyColor)); Text("  Study   ", color = Astra.Muted, fontSize = 12.sp)
            Box(Modifier.size(10.dp).background(ShiftColor)); Text("  Shift   ", color = Astra.Muted, fontSize = 12.sp)
            Text("- - -  ${h.weekStudyGoal} h goal", color = Astra.Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SessionRow(s: Session, onDelete: () -> Unit) {
    HorizontalDivider(color = Astra.Line.copy(alpha = 0.6f))
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if (s.kind == KIND_STUDY) StudyColor else ShiftColor))
        Spacer(Modifier.width(12.dp))
        Text(LocalDate.parse(s.date).format(dayFmt), color = Astra.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            "${kindName(s.kind)}  ${fmtDuration(s.minutes / 60f)}" + if (s.manual) "  (added)" else "",
            color = Astra.Muted, fontSize = 14.sp,
        )
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Close, "Delete", tint = Astra.Line) }
    }
}

@Composable
private fun Choice(label: String, on: Boolean, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(6.dp))
            .background(if (on) color else Astra.Empty)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (on) Astra.Bg else Astra.Muted, fontSize = 14.sp) }
}

@Composable
private fun AddTimeDialog(onDismiss: () -> Unit, onAdd: (String, LocalDate, Int) -> Unit) {
    var kind by remember { mutableStateOf(KIND_STUDY) }
    var hours by remember { mutableStateOf("") }
    var yesterday by remember { mutableStateOf(false) }
    val minutes = hours.replace(',', '.').toFloatOrNull()?.let { (it * 60).roundToInt() } ?: 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add time") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Study", kind == KIND_STUDY, StudyColor) { kind = KIND_STUDY }
                    Choice("Shift", kind == KIND_SHIFT, ShiftColor) { kind = KIND_SHIFT }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    hours, { hours = it }, label = { Text("Hours, e.g. 1.5") }, singleLine = true, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Today", !yesterday, Astra.Text) { yesterday = false }
                    Choice("Yesterday", yesterday, Astra.Text) { yesterday = true }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = minutes in 1..(16 * 60), onClick = {
                onAdd(kind, if (yesterday) appToday().minusDays(1) else appToday(), minutes)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalsDialog(d: AppData, onDismiss: () -> Unit, onSave: (Int, Int, Int, Boolean) -> Unit) {
    val g = d.goals
    var year by remember { mutableStateOf(g.studyYear.toString()) }
    var study by remember { mutableStateOf(g.studyWeek.toString()) }
    var shift by remember { mutableStateOf(g.shiftWeek.toString()) }
    var restart by remember { mutableStateOf(false) }
    val ok = listOf(year, study, shift).all { (it.toIntOrNull() ?: 0) > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Goals") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val num = KeyboardOptions(keyboardType = KeyboardType.Number)
                OutlinedTextField(year, { year = it }, label = { Text("Study hours in the year") }, singleLine = true, colors = fieldColors(), keyboardOptions = num)
                OutlinedTextField(study, { study = it }, label = { Text("Study hours a week") }, singleLine = true, colors = fieldColors(), keyboardOptions = num)
                OutlinedTextField(shift, { shift = it }, label = { Text("Shift hours a week") }, singleLine = true, colors = fieldColors(), keyboardOptions = num)
                Row(Modifier.clickable { restart = !restart }.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    CheckSquare(restart)
                    Spacer(Modifier.width(10.dp))
                    Text("Start the year again from today", color = Astra.Text, fontSize = 14.sp)
                }
                Hint("The year started ${g.yearStart ?: "today"}. Logged hours are never deleted.")
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = { onSave(year.toInt(), study.toInt(), shift.toInt(), restart) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
