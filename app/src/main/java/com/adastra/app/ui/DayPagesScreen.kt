package com.adastra.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import com.adastra.app.data.Repository
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import com.adastra.app.data.fmtHours
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

    Column(Modifier.fillMaxSize()) {
        // Drawn above the pages so a turning page never covers it.
        Row(Modifier.zIndex(1f).fillMaxWidth().background(Astra.Bg).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = Astra.Ink)
                Text("Grid", color = Astra.Ink, fontSize = 16.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("Page ${index + 1} of $count", color = Astra.Muted, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(count, index) {
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
                        DayPage(data, first.plusDays(page.toLong()))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayPage(data: AppData, date: LocalDate) {
    val e = data.days[date.toString()]
    val habits = data.habits.filter { !it.archived || e?.done?.contains(it.id) == true }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        Text(date.format(DateTimeFormatter.ofPattern("EEEE")), color = Astra.Muted, fontSize = 15.sp)
        Text(date.format(DateTimeFormatter.ofPattern("d MMMM yyyy")), color = Astra.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))

        val empty = e == null || (!e.isStep && e.note.isBlank() && e.sleep == null)
        if (empty || e == null) {
            Text("Nothing was logged on this day.", color = Astra.Muted, fontSize = 16.sp)
            return@Column
        }
        if (e.note.isNotBlank()) {
            Text(e.note, color = Astra.Text, fontSize = 22.sp, lineHeight = 31.sp)
            Spacer(Modifier.height(10.dp))
        }
        Section("Habits", trailing = "${e.done.size} of ${habits.size}") {
            habits.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth()) {
                    pair.forEach { h ->
                        val on = h.id in e.done
                        Row(Modifier.weight(1f).height(42.dp), verticalAlignment = Alignment.CenterVertically) {
                            CheckSquare(on, size = 20.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(h.name, color = if (on) Astra.Text else Astra.Muted, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Section("Sleep", trailing = e.sleep?.let { "${fmtHours(it)} h" } ?: "Not logged") {}
        if (e.rest) Section("Rest day") { Hint("A planned recovery day.") }
        Spacer(Modifier.height(16.dp))
    }
}
