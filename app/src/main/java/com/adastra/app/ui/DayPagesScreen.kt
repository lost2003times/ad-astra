package com.adastra.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.AppData
import com.adastra.app.data.DayEntry
import com.adastra.app.data.Repository
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import com.adastra.app.data.fmtHours
import com.adastra.app.data.fmtDuration
import com.adastra.app.data.dayMinutes
import com.adastra.app.data.KIND_STUDY
import com.adastra.app.data.KIND_SHIFT
import com.adastra.app.data.Habit
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontStyle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Past days as read-only notebook pages. Swipe left for the next day, right for the day before;
 * the page follows your finger and turns around its left edge.
 */
@Composable
fun DayPagesScreen(repo: Repository, start: LocalDate, onClose: () -> Unit) {
    val data by repo.data.collectAsStateWithLifecycle()
    val today = appToday()
    val first = Stats.startDate(data, today)
    val count = (ChronoUnit.DAYS.between(first, today) + 1).toInt().coerceAtLeast(1)
    var index by rememberSaveable {
        mutableIntStateOf(ChronoUnit.DAYS.between(first, start).toInt().coerceIn(0, count - 1))
    }
    // How far the page is turned, in pages: 0..1 turning to the next day, 0..-1 back to the previous.
    val drag = remember { Animatable(0f) }
    val direction by remember { derivedStateOf { if (drag.value > 0f) 1 else if (drag.value < 0f) -1 else 0 } }
    val scope = rememberCoroutineScope()
    // Special access: past days stay locked until the lock is held. Turning the page locks it again.
    var editing by remember(index) { mutableStateOf(false) }
    var showLockHint by remember { mutableStateOf(false) }
    val isToday = first.plusDays(index.toLong()) == today

    Column(Modifier.fillMaxSize()) {
        // Drawn above the pages so a turning page never covers it.
        Row(Modifier.zIndex(1f).fillMaxWidth().background(Astra.Bg).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Astra.Text)
                Text("Grid", color = Astra.Ink, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            if (editing) {
                Text("Editing", color = Astra.Signal, fontSize = 14.sp)
                TextButton(onClick = { editing = false }) { Text("Lock", color = Astra.Ink, fontSize = 16.sp) }
            } else {
                Text("Page ${index + 1} of $count", color = Astra.Muted, fontSize = 14.sp)
                if (!isToday) {
                    Box(
                        Modifier
                            .padding(start = 4.dp)
                            .size(48.dp)
                            .pointerInput(index) {
                                detectTapGestures(
                                    onTap = { showLockHint = true },
                                    onLongPress = { showLockHint = false; editing = true },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Lock, "Hold to unlock editing", tint = Astra.Muted, modifier = Modifier.size(20.dp)) }
                } else Spacer(Modifier.width(12.dp))
            }
        }
        if (showLockHint && !editing) {
            Hint("Past days are locked. Hold the lock to edit this one.", Modifier.padding(horizontal = 24.dp, vertical = 2.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(count, index, editing) {
                    if (editing) return@pointerInput   // no page turns while editing: the sleep slider needs the drag
                    fun settle() {
                        scope.launch {
                            val target = when {
                                drag.value > 0.2f && index < count - 1 -> 1f
                                drag.value < -0.2f && index > 0 -> -1f
                                else -> 0f
                            }
                            drag.animateTo(target, tween(280))
                            if (target != 0f) {
                                index += target.toInt()
                                drag.snapTo(0f)
                            }
                        }
                    }
                    detectHorizontalDragGestures(
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { _, dx ->
                            val min = if (index > 0) -1f else 0f
                            val max = if (index < count - 1) 1f else 0f
                            scope.launch { drag.snapTo((drag.value - dx / size.width).coerceIn(min, max)) }
                        },
                    )
                }
        ) {
            // Bottom layer first. Turning forward lifts today's page off the next one;
            // turning back lays the previous page down over this one.
            val layers = when (direction) {
                1 -> listOf(index + 1, index)
                -1 -> listOf(index, index - 1)
                else -> listOf(index)
            }
            layers.forEachIndexed { i, page ->
                key(page) {
                    val role = when {
                        layers.size == 1 -> Modifier
                        i == 0 -> Modifier.pageBeneath { if (direction == 1) drag.value else 1f + drag.value }
                        else -> Modifier.turningPage { if (direction == 1) drag.value else 1f + drag.value }
                    }
                    Box(Modifier.fillMaxSize().then(role).background(Astra.Bg)) {
                        DayPage(repo, data, first.plusDays(page.toLong()), editing && page == index)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPage(repo: Repository, data: AppData, date: LocalDate, editing: Boolean) {
    val e = data.days[date.toString()]
    val habits = data.habits.filter { !it.archived || e?.done?.contains(it.id) == true }
    var history by remember { mutableStateOf<Habit?>(null) }
    history?.let { h -> HabitHistory(repo, h) { history = null } }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        Label(date.format(DateTimeFormatter.ofPattern("EEEE")))
        Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), color = Astra.Text, fontSize = 34.sp, lineHeight = 38.sp, style = Score)
        Spacer(Modifier.height(10.dp))

        if (editing) {
            DayEditor(repo, date, e ?: DayEntry(date.toString()), data.habits.filter { !it.archived })
            Spacer(Modifier.height(16.dp))
            return@Column
        }

        val state = when {
            e == null -> null
            e.done.isNotEmpty() -> "STEP"
            e.rest -> "REST"
            else -> null
        }
        // A seal for a day that counted, like the stamps on Today.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).rotate(-5f).clip(RoundedCornerShape(5.dp)).background(
                    when (state) { "STEP" -> Astra.Signal; "REST" -> Astra.Rest; else -> Astra.Empty }
                ),
                contentAlignment = Alignment.Center,
            ) { Text(date.format(DateTimeFormatter.ofPattern("EEEEE")), color = Astra.Bg, fontSize = 14.sp, style = Score) }
            Spacer(Modifier.width(12.dp))
            Text(
                when (state) {
                    "STEP" -> "${e!!.done.size} of ${habits.size} filled in"
                    "REST" -> "Rest day. It counted."
                    else -> "Nothing was logged on this day."
                },
                color = if (state == null) Astra.Muted else Astra.Text, fontSize = 15.sp,
            )
        }

        if (e != null && e.note.isNotBlank()) {
            Label("Memorable", Modifier.padding(top = 18.dp))
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(Astra.Signal))
                Spacer(Modifier.width(14.dp))
                Text(e.note, color = Astra.Text, fontSize = 20.sp, lineHeight = 28.sp, fontStyle = FontStyle.Italic)
            }
        }

        val study = dayMinutes(data, KIND_STUDY, date) / 60f
        val shift = dayMinutes(data, KIND_SHIFT, date) / 60f
        Card {
            Row {
                DayStat("Study", if (study > 0) fmtDuration(study) else "–", Astra.Text, Modifier.weight(1f))
                DayStat("Shift", if (shift > 0) fmtDuration(shift) else "–", Astra.Signal, Modifier.weight(1f))
                DayStat("Sleep", e?.sleep?.let { "${fmtHours(it)} h" } ?: "–", Astra.Text, Modifier.weight(1f))
            }
        }

        Spacer(Modifier.height(18.dp))
        Label("The page")
        BookStrip(data, date, habits, onHeading = { history = it }, onHold = { history = it }, onTap = null)
        Hint("Hold the lock at the top to edit this day.", Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DayStat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = color, fontSize = 22.sp, style = Score)
        Label(label)
    }
}
