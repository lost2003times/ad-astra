package com.adastra.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.DayEntry
import com.adastra.app.data.Habit
import com.adastra.app.data.Repository
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import com.adastra.app.data.Word
import com.adastra.app.data.fmtHours
import com.adastra.app.data.fmtDuration
import com.adastra.app.data.hourKind
import com.adastra.app.data.dayMinutes
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TodayScreen(repo: Repository) {
    val data by repo.data.collectAsStateWithLifecycle()
    val today = appToday()
    val date = today   // past days are read-only: they open as pages from the Grid
    val entry = data.days[date.toString()] ?: DayEntry(date.toString())
    val habits = data.habits.filter { !it.archived }
    val steps = entry.done.size
    val ySteps = Stats.steps(data, date.minusDays(1))
    val focus = data.focus[Stats.weekStart(date).toString()]

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item { Header(date, data.settings.name) }
        item { Quote(data.words, date) }
        item {
            Streak(
                streak = Stats.currentStreak(data, today),
                best = Stats.bestStreak(data),
                message = stepMessage(entry, steps, ySteps, date == today),
                warn = !entry.isStep && date == today,
            )
        }
        if (!focus.isNullOrBlank()) item {
            Section("This week's focus") { Text(focus, color = Astra.Text, fontSize = 15.sp, lineHeight = 21.sp) }
        }
        item { DayEditor(repo, date, entry, habits) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

/** The editable part of a day: habits, rest day, sleep and the note. Used by Today and by an unlocked past day. */
@Composable
fun DayEditor(repo: Repository, date: LocalDate, entry: DayEntry, habits: List<Habit>) {
    var history by remember { mutableStateOf<Habit?>(null) }
    var asking by remember { mutableStateOf<Habit?>(null) }
    val data by repo.data.collectAsStateWithLifecycle()
    history?.let { h -> HabitHistory(repo, h) { history = null } }
    asking?.let { h ->
        val kind = hourKind(h)!!
        HoursAsk(h.name, date, dayMinutes(data, kind, date), onDismiss = { asking = null }) { m ->
            repo.setDayHours(kind, date, m); asking = null
        }
    }
    Column {
        Section("Habits", trailing = "${entry.done.size} of ${habits.size}") {
            if (habits.isEmpty()) Hint("Add habits in Setup.") else Hint("Hold a habit to see every day you ticked it.", Modifier.padding(bottom = 4.dp))
            habits.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { h ->
                        val kind = hourKind(h)
                        val hours = kind?.let { dayMinutes(data, it, date) / 60f }
                        HabitRow(h, h.id in entry.done, Modifier.weight(1f), hours, onHold = { history = h }) {
                            if (kind != null) asking = h else repo.toggleHabit(date, h.id)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Section("Rest day") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Hint("A planned recovery day counts as a step.", Modifier.weight(1f))
                Switch(
                    checked = entry.rest, onCheckedChange = { repo.setRest(date, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = Astra.Rest, uncheckedTrackColor = Astra.Bg),
                )
            }
        }
        Section("Sleep", trailing = entry.sleep?.let { "${fmtHours(it)} h" } ?: "Not logged") {
            Slider(
                value = entry.sleep ?: 7f,
                onValueChange = { repo.setSleep(date, (it * 2).roundToInt() / 2f) },
                valueRange = 3f..12f,
                steps = 17,
                colors = SliderDefaults.colors(thumbColor = Astra.Ink, activeTrackColor = Astra.Ink, inactiveTrackColor = Astra.Line),
            )
            Row {
                Hint("3 h")
                Spacer(Modifier.weight(1f))
                if (entry.sleep != null) {
                    Text(
                        "Clear", color = Astra.Muted, fontSize = 13.sp,
                        modifier = Modifier.clickable { repo.setSleep(date, null) },
                    )
                    Spacer(Modifier.weight(1f))
                }
                Hint("12 h")
            }
        }
        NoteSection(repo, date, entry.note)
    }
}

private fun plural(n: Int) = if (n == 1) "habit" else "habits"

private fun stepMessage(e: DayEntry, steps: Int, ySteps: Int, isToday: Boolean): String = when {
    !e.isStep && isToday -> "Nothing logged yet today. One habit, or a rest day, keeps the streak alive."
    !e.isStep -> "Nothing was logged on this day."
    steps == 0 -> "Rest day. It counts."
    steps > ySteps -> "$steps ${plural(steps)}, ${steps - ySteps} more than the day before."
    steps == ySteps -> "$steps ${plural(steps)}, same as the day before."
    else -> "$steps ${plural(steps)}. Not a zero."
}

@Composable
private fun Header(date: LocalDate, name: String) {
    val hour = LocalTime.now().hour
    val greet = when {
        hour < 4 -> "Good evening"
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    ScreenTitle(
        title = date.format(DateTimeFormatter.ofPattern("EEEE, d MMM")),
        subtitle = if (name.isBlank()) greet else "$greet, $name",
    )
}

/** One of your own lines, set in serif like handwriting on the page. */
@Composable
fun Quote(words: List<Word>, date: LocalDate) {
    if (words.isEmpty()) return
    var offset by remember(date) { mutableIntStateOf(0) }
    val w = words[Math.floorMod(date.toEpochDay() + offset, words.size.toLong()).toInt()]
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp)
            .height(IntrinsicSize.Min)
            .clickable { offset++ }
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(Astra.Ink))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.padding(vertical = 2.dp)) {
            Text(w.text, color = Astra.Text, fontFamily = Serif, fontSize = 20.sp, lineHeight = 28.sp)
            Hint("Tap for another", Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Streak(streak: Int, best: Int, message: String, warn: Boolean) {
    Column(Modifier.padding(top = 18.dp, bottom = 12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("$streak", color = Astra.Signal, fontSize = 60.sp, fontWeight = FontWeight.Bold, lineHeight = 60.sp, style = Numbers)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.padding(bottom = 8.dp)) {
                Text(if (streak == 1) "day without a zero" else "days without a zero", color = Astra.Text, fontSize = 16.sp)
                Text("Best: $best", color = Astra.Muted, fontSize = 13.sp, style = Numbers)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(message, color = if (warn) Astra.Signal else Astra.Muted, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HabitRow(h: Habit, on: Boolean, modifier: Modifier, hours: Float?, onHold: () -> Unit, onToggle: () -> Unit) {
    Row(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(6.dp))
            .combinedClickable(onLongClick = onHold, onClick = onToggle)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckSquare(on)
        Spacer(Modifier.width(12.dp))
        Text(h.name, color = Astra.Text, fontSize = 15.sp, maxLines = 1)
        if (hours != null && hours > 0f) {
            Text("  ${fmtDuration(hours)}", color = Astra.Signal, fontSize = 14.sp, maxLines = 1, style = Numbers)
        }
    }
}

/** Asks how many hours of study or shift the day had. */
@Composable
private fun HoursAsk(name: String, date: LocalDate, current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) fmtNum(current / 60f) else "") }
    val minutes = text.replace(',', '.').toFloatOrNull()?.let { (it * 60).roundToInt() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How many hours of ${name.lowercase()}?") },
        text = {
            Column {
                Hint(if (date == appToday()) "Total for today." else "Total for ${date.format(DateTimeFormatter.ofPattern("EEE d MMM"))}.")
                Spacer(Modifier.height(10.dp))
                listOf(listOf(0.5f, 1f, 1.5f, 2f), listOf(3f, 4f, 6f, 8f)).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        row.forEach { v ->
                            val on = minutes == (v * 60).roundToInt()
                            Box(
                                Modifier.weight(1f).padding(horizontal = 3.dp).height(40.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (on) Astra.Ink else Astra.Empty)
                                    .clickable { text = fmtNum(v) },
                                contentAlignment = Alignment.Center,
                            ) { Text("${fmtNum(v)} h", color = if (on) Color.White else Astra.Text, fontSize = 15.sp) }
                        }
                    }
                }
                OutlinedTextField(
                    text, { text = it }, label = { Text("Hours") }, singleLine = true, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = minutes != null && minutes in 0..(20 * 60), onClick = { onSave(minutes ?: 0) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (current > 0) TextButton(onClick = { onSave(0) }) { Text("Clear", color = Astra.Muted) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

private fun fmtNum(v: Float): String = if (v % 1f == 0f) v.toInt().toString() else String.format("%.1f", v)

@Composable
private fun NoteSection(repo: Repository, date: LocalDate, note: String) {
    var text by remember(date) { mutableStateOf(note) }
    var saved by remember(date) { mutableStateOf(false) }
    Section("Note") {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; saved = false; repo.setNote(date, it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("One line about the day", color = Astra.Muted) },
            maxLines = 4,
            colors = fieldColors(),
        )
        Row {
            Spacer(Modifier.weight(1f))
            TextButton(enabled = text.isNotBlank() && !saved, onClick = { repo.addWord(text.trim(), date.toString()); saved = true }) {
                Text(if (saved) "Saved to your words" else "Save to my words", color = if (saved) Astra.Muted else Astra.Ink)
            }
        }
    }
}
