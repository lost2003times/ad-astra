package com.adastra.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.R
import com.adastra.app.data.AppData
import com.adastra.app.data.DayEntry
import com.adastra.app.data.DayState
import com.adastra.app.data.Habit
import com.adastra.app.data.Hours
import com.adastra.app.data.KIND_SHIFT
import com.adastra.app.data.Repository
import com.adastra.app.data.Stats
import com.adastra.app.data.Word
import com.adastra.app.data.appToday
import com.adastra.app.data.dayMinutes
import com.adastra.app.data.fmtDuration
import com.adastra.app.data.fmtHours
import com.adastra.app.data.hourKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TodayScreen(repo: Repository) {
    val data by repo.data.collectAsStateWithLifecycle()
    val today = appToday()
    val entry = data.days[today.toString()] ?: DayEntry(today.toString())
    val habits = data.habits.filter { !it.archived }
    val steps = entry.done.size
    val ySteps = Stats.steps(data, today.minusDays(1))
    val focus = data.focus[Stats.weekStart(today).toString()]
    val h = remember(data) { Hours.summary(data) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(today.format(DateTimeFormatter.ofPattern("EEEE · d MMM")))
                    if (data.settings.name.isNotBlank()) Text(data.settings.name, color = Astra.Muted, fontSize = 14.sp, fontStyle = FontStyle.Italic)
                }
                Image(painterResource(R.drawable.ic_launcher_fg), null, Modifier.size(48.dp))
            }
        }
        item {
            Hero(
                streak = Stats.currentStreak(data, today),
                best = Stats.bestStreak(data),
                message = stepMessage(entry, steps, ySteps, true),
                warn = !entry.isStep,
            )
        }
        item { WeekSeals(data, today) }
        item {
            Card {
                ScoreLine("Study", h.weekStudy, h.weekStudyGoal, Astra.Ink)
                Spacer(Modifier.height(12.dp))
                ScoreLine("Shift", h.weekShift, h.weekShiftGoal, Astra.Signal)
            }
        }
        if (!focus.isNullOrBlank()) item {
            Card {
                Label("This week's focus")
                Text(focus, color = Astra.Text, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item { Spacer(Modifier.height(18.dp)); DayEditor(repo, today, entry, habits) }
        item { Quote(data.words, today) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Hero(streak: Int, best: Int, message: String, warn: Boolean) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$streak", color = Astra.Signal, fontSize = 72.sp, lineHeight = 72.sp, style = Score)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(bottom = 12.dp)) {
                Text(if (streak == 1) "day" else "days", color = Astra.Text, fontSize = 17.sp)
                Text("without a zero · best $best", color = Astra.Muted, fontSize = 13.sp)
            }
        }
        Text(message, color = if (warn) Astra.Signal else Astra.Muted, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** This week as seals: a red stamp for every day that wasn't a zero. */
@Composable
private fun WeekSeals(d: AppData, today: LocalDate) {
    val week = Stats.week(d, Stats.weekStart(today), today)
    val letters = listOf("M", "T", "W", "T", "F", "S", "S")
    val tilt = listOf(-5f, 4f, -2f, 3f, -4f, 2f, -3f)
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        week.states.forEachIndexed { i, s ->
            val shape = RoundedCornerShape(5.dp)
            val base = Modifier.size(30.dp)
            when (s) {
                DayState.STEP, DayState.REST -> Box(
                    base.rotate(tilt[i]).clip(shape).background(if (s == DayState.STEP) Astra.Signal else Astra.Rest),
                    contentAlignment = Alignment.Center,
                ) { Text(letters[i], color = Astra.Bg, fontSize = 13.sp, style = Score) }
                else -> Box(
                    base.border(1.5.dp, if (s == DayState.FUTURE) Astra.Line else Astra.Muted.copy(alpha = 0.5f), shape),
                    contentAlignment = Alignment.Center,
                ) { Text(letters[i], color = Astra.Line, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun ScoreLine(label: String, value: Float, goal: Int, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(label, color = Astra.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("${fmtDuration(value)} / $goal h", color = if (value >= goal) color else Astra.Muted, fontSize = 14.sp, style = Numbers)
    }
    BrushBar(value / goal, color, Modifier.padding(top = 6.dp))
}

private const val ROW_DAYS = 3          // earlier days shown faded above the day being filled in
private val CELL = 40.dp
private val DAY_COL = 58.dp
private val HEAD_H = 96.dp

/**
 * The editable part of a day, laid out like the notebook: habit names written sideways along the top,
 * the last few days faded above, and this day's row of squares to fill in. Used by Today and by an
 * unlocked past day.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DayEditor(repo: Repository, date: LocalDate, entry: DayEntry, habits: List<Habit>) {
    val data by repo.data.collectAsStateWithLifecycle()
    var history by remember { mutableStateOf<Habit?>(null) }
    var asking by remember { mutableStateOf<Habit?>(null) }
    history?.let { h -> HabitHistory(repo, h) { history = null } }
    asking?.let { h ->
        val kind = hourKind(h)!!
        HoursSheet(h.name, kind == KIND_SHIFT, date, dayMinutes(data, kind, date), onDismiss = { asking = null }) { m ->
            repo.setDayHours(kind, date, m); asking = null
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label(if (date == appToday()) "Today's page" else "This day's page", Modifier.weight(1f))
            Text("${entry.done.size} of ${habits.size}", color = Astra.Muted, fontSize = 13.sp, style = Numbers)
        }
        if (habits.isEmpty()) Hint("Add habits in Setup.", Modifier.padding(top = 8.dp))
        else {
            BookStrip(data, date, habits, onHeading = { history = it }, onHold = { history = it }) { h ->
                if (hourKind(h) != null) asking = h else repo.toggleHabit(date, h.id)
            }
            Hint("Tap a square to fill it. Study and Shift ask for hours. Hold a square or tap a heading to see its history.", Modifier.padding(top = 6.dp))
        }

        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip(if (entry.rest) "Rest day ✓" else "Rest day", entry.rest, Astra.Rest) { repo.setRest(date, !entry.rest) }
        }
        Hint("A planned recovery day counts as a step.", Modifier.padding(top = 4.dp))

        Card {
            Row {
                Label("Sleep last night", Modifier.weight(1f))
                Text(entry.sleep?.let { "${fmtHours(it)} h" } ?: "not logged", color = Astra.Text, fontSize = 14.sp, style = Numbers)
            }
            Slider(
                value = entry.sleep ?: 7f,
                onValueChange = { repo.setSleep(date, (it * 2).roundToInt() / 2f) },
                valueRange = 3f..12f,
                steps = 17,
                colors = SliderDefaults.colors(thumbColor = Astra.Ink, activeTrackColor = Astra.Ink, inactiveTrackColor = Astra.Line),
            )
            if (entry.sleep != null) Text(
                "Clear", color = Astra.Muted, fontSize = 13.sp,
                modifier = Modifier.clickable { repo.setSleep(date, null) },
            )
        }
        NoteSection(repo, date, entry.note)
    }
}

/**
 * The notebook grid for one day: habit names sideways along the top, the last few days faded above,
 * and this day's row of squares. [onTap] null makes it read-only.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BookStrip(
    data: AppData, date: LocalDate, habits: List<Habit>,
    onHeading: (Habit) -> Unit, onHold: (Habit) -> Unit, onTap: ((Habit) -> Unit)?,
) {
    val scroll = rememberScrollState()
    Column(Modifier.padding(top = 6.dp).horizontalScroll(scroll)) {
        Row {
            Spacer(Modifier.width(DAY_COL))
            habits.forEach { h ->
                Box(Modifier.width(CELL).height(HEAD_H).clickable { onHeading(h) }, contentAlignment = Alignment.BottomCenter) {
                    Text(
                        h.name, color = Astra.Muted, fontSize = 13.sp, maxLines = 1, softWrap = false,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 6.dp).sideways(),
                    )
                }
            }
            Box(Modifier.width(CELL + 8.dp).height(HEAD_H), contentAlignment = Alignment.BottomCenter) {
                Text("Sleep", color = Astra.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp).sideways())
            }
        }
        (ROW_DAYS downTo 0).forEach { back ->
            val day = date.minusDays(back.toLong())
            val e = data.days[day.toString()] ?: DayEntry(day.toString())
            val live = back == 0
            Row(
                Modifier.height(if (live) CELL + 8.dp else CELL).alpha(if (live) 1f else 0.45f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(DAY_COL)) {
                    Text(day.format(DateTimeFormatter.ofPattern("EEE")), color = if (live) Astra.Signal else Astra.Muted, fontSize = 12.sp)
                    Text("${day.dayOfMonth}", color = Astra.Text, fontSize = if (live) 18.sp else 14.sp, style = Score)
                }
                habits.forEach { h ->
                    val kind = hourKind(h)
                    val hours = kind?.let { dayMinutes(data, it, day) / 60f } ?: 0f
                    BookCell(
                        on = h.id in e.done,
                        hours = if (kind != null && hours > 0f) hours else null,
                        fill = if (kind == KIND_SHIFT) Astra.Signal else Astra.Ink,
                        rest = e.rest && e.done.isEmpty(),
                        big = live,
                        modifier = when {
                            !live -> Modifier
                            onTap == null -> Modifier.combinedClickable(onLongClick = { onHold(h) }, onClick = { onHold(h) })
                            else -> Modifier.combinedClickable(onLongClick = { onHold(h) }, onClick = { onTap(h) })
                        },
                    )
                }
                Box(Modifier.width(CELL + 8.dp), contentAlignment = Alignment.Center) {
                    Text(e.sleep?.let { fmtHours(it) } ?: "·", color = if (live) Astra.Text else Astra.Muted, fontSize = 14.sp, style = Numbers)
                }
            }
        }
    }
}

/** One square of the notebook grid. */
@Composable
private fun BookCell(on: Boolean, hours: Float?, fill: Color, rest: Boolean, big: Boolean, modifier: Modifier) {
    val size: Dp = if (big) 34.dp else 24.dp
    val shape = RoundedCornerShape(5.dp)
    Box(Modifier.width(CELL).fillMaxHeight().then(modifier), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size).clip(shape).then(
                when {
                    on -> Modifier.background(fill)
                    rest -> Modifier.background(Astra.Rest.copy(alpha = 0.35f))
                    else -> Modifier.border(1.5.dp, Astra.Line, shape)
                }
            ),
            contentAlignment = Alignment.Center,
        ) {
            val ink = if (fill == Astra.Signal) Astra.Text else Astra.Bg
            when {
                hours != null -> Text(fmtNum(hours), color = ink, fontSize = if (big) 13.sp else 10.sp, style = Score)
                on -> Text("✓", color = ink, fontSize = if (big) 16.sp else 11.sp, style = Score)
            }
        }
    }
}

@Composable
private fun Chip(text: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.clip(CircleShape).background(if (on) color else Astra.Surface).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(text, color = Astra.Text, fontSize = 14.sp) }
}

/** The sheet that slides up for Study or Shift: one big number, presets, and Log. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HoursSheet(name: String, shift: Boolean, date: LocalDate, current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var minutes by remember { mutableIntStateOf(if (current > 0) current else 60) }
    val accent = if (shift) Astra.Signal else Astra.Ink
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Astra.Surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Label("$name · " + if (date == appToday()) "today" else date.format(DateTimeFormatter.ofPattern("EEE d MMM")))
            Text("How many hours?", color = Astra.Text, fontSize = 22.sp, modifier = Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(fmtNum(minutes / 60f), color = if (shift) Astra.Signal else Astra.Text, fontSize = 76.sp, lineHeight = 76.sp, style = Score)
                Text(" h", color = Astra.Muted, fontSize = 24.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
            listOf(listOf(0.5f, 1f, 2f, 3f), listOf(4f, 5f, 6f, 8f)).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { v ->
                        val on = minutes == (v * 60).roundToInt()
                        Box(
                            Modifier.weight(1f).height(44.dp).clip(CircleShape)
                                .background(if (on) accent else Astra.Empty)
                                .clickable { minutes = (v * 60).roundToInt() },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (v == 0.5f) "½" else fmtNum(v), color = if (on) (if (shift) Astra.Text else Astra.Bg) else Astra.Text, fontSize = 16.sp, style = Numbers) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(-30 to "− ½", 30 to "+ ½").forEach { (step, label) ->
                    Box(
                        Modifier.weight(1f).height(44.dp).clip(CircleShape).background(Astra.Empty)
                            .clickable { minutes = (minutes + step).coerceIn(0, 20 * 60) },
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = Astra.Muted, fontSize = 16.sp) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(Astra.Empty)
                        .clickable { if (current > 0) onSave(0) else onDismiss() },
                    contentAlignment = Alignment.Center,
                ) { Text(if (current > 0) "Clear" else "Cancel", color = Astra.Muted, fontSize = 16.sp) }
                Box(
                    Modifier.weight(2f).height(52.dp).clip(RoundedCornerShape(12.dp)).background(Astra.Text)
                        .clickable(enabled = minutes > 0) { onSave(minutes) },
                    contentAlignment = Alignment.Center,
                ) { Text("Log ${fmtNum(minutes / 60f)} h", color = Astra.Bg, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

private fun fmtNum(v: Float): String = when {
    v % 1f == 0f -> v.toInt().toString()
    (v * 2) % 1f == 0f -> "${v.toInt().takeIf { it > 0 } ?: ""}½"
    else -> String.format("%.1f", v)
}

private fun plural(n: Int) = if (n == 1) "habit" else "habits"

private fun stepMessage(e: DayEntry, steps: Int, ySteps: Int, isToday: Boolean): String = when {
    !e.isStep && isToday -> "Nothing logged yet today. One square, or a rest day, keeps the streak alive."
    !e.isStep -> "Nothing was logged on this day."
    steps == 0 -> "Rest day. It counts."
    steps > ySteps -> "$steps ${plural(steps)}, ${steps - ySteps} more than yesterday."
    steps == ySteps -> "$steps ${plural(steps)}, same as yesterday."
    else -> "$steps ${plural(steps)}. Not a zero."
}

/** One of your own lines, with a red rule beside it. */
@Composable
fun Quote(words: List<Word>, date: LocalDate) {
    if (words.isEmpty()) return
    var offset by remember(date) { mutableIntStateOf(0) }
    val w = words[Math.floorMod(date.toEpochDay() + offset, words.size.toLong()).toInt()]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 6.dp)
            .height(IntrinsicSize.Min)
            .clickable { offset++ }
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Astra.Signal))
        Spacer(Modifier.width(14.dp))
        Text(w.text, color = Astra.Text, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 18.sp, lineHeight = 26.sp)
    }
}

@Composable
private fun NoteSection(repo: Repository, date: LocalDate, note: String) {
    var text by remember(date) { mutableStateOf(note) }
    var saved by remember(date) { mutableStateOf(false) }
    Column(Modifier.padding(top = 16.dp)) {
        Label("One line about the day")
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false; repo.setNote(date, it) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            placeholder = { Text("Today I…", color = Astra.Muted, fontStyle = FontStyle.Italic) },
            maxLines = 4,
            colors = fieldColors(),
        )
        Row {
            Spacer(Modifier.weight(1f))
            TextButton(enabled = text.isNotBlank() && !saved, onClick = { repo.addWord(text.trim(), date.toString()); saved = true }) {
                Text(if (saved) "Saved to your words" else "Save to my words", color = if (saved) Astra.Muted else Astra.Signal)
            }
        }
    }
}
