package com.adastra.app.ui

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.Habit
import com.adastra.app.data.Repository
import com.adastra.app.notify.Reminder
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(repo: Repository) {
    val data by repo.data.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val s = data.settings
    var name by remember { mutableStateOf(s.name) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var adding by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf<Habit?>(null) }
    val active = data.habits.filter { !it.archived }
    val archived = data.habits.filter { it.archived }
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun reschedule() = Reminder.schedule(ctx, repo.data.value.settings)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item { ScreenTitle("Setup", null) }
        item {
            Section("Name") {
                OutlinedTextField(
                    value = name,
                    onValueChange = { v -> name = v; repo.updateSettings { it.copy(name = v.trim()) } },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, colors = fieldColors(),
                )
            }
        }
        item {
            Section("Evening reminder") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Hint("If nothing is logged by this time, you get one of your own lines as a reminder.", Modifier.weight(1f))
                    Switch(
                        checked = s.reminderOn,
                        onCheckedChange = { on ->
                            repo.updateSettings { it.copy(reminderOn = on) }
                            reschedule()
                            if (on && Build.VERSION.SDK_INT >= 33) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Astra.Ink, uncheckedTrackColor = Astra.Bg),
                    )
                }
                if (s.reminderOn) {
                    val time = LocalTime.of(s.reminderHour, s.reminderMinute).format(DateTimeFormatter.ofPattern("h:mm a"))
                    TextButton(onClick = {
                        TimePickerDialog(ctx, { _, h, m ->
                            repo.updateSettings { it.copy(reminderHour = h, reminderMinute = m) }
                            reschedule()
                        }, s.reminderHour, s.reminderMinute, false).show()
                    }) { Text("Remind me at $time", color = Astra.Ink, fontSize = 15.sp) }
                }
            }
        }
        item {
            Section("Habits", trailing = "${active.size}") {
                Hint("Hidden habits keep their history and can be restored.")
                TextButton(onClick = { adding = true }) { Text("Add a habit", color = Astra.Ink, fontSize = 15.sp) }
            }
        }
        itemsIndexed(active, key = { _, h -> h.id }) { i, h ->
            HorizontalDivider(color = Astra.Line.copy(alpha = 0.6f))
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(h.name, color = Astra.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { repo.moveHabit(h.id, -1) }, enabled = i > 0) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Move up", tint = if (i > 0) Astra.Muted else Astra.Line)
                }
                IconButton(onClick = { repo.moveHabit(h.id, 1) }, enabled = i < active.lastIndex) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Move down", tint = if (i < active.lastIndex) Astra.Muted else Astra.Line)
                }
                IconButton(onClick = { editing = h }) { Icon(Icons.Filled.Edit, "Rename", tint = Astra.Muted) }
                IconButton(onClick = { hiding = h }) { Icon(Icons.Filled.Close, "Hide", tint = Astra.Muted) }
            }
        }
        if (archived.isNotEmpty()) {
            item { Section("Hidden habits") {} }
            itemsIndexed(archived, key = { _, h -> "a" + h.id }) { _, h ->
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(h.name, color = Astra.Muted, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { repo.setArchived(h.id, false) }) { Text("Restore", color = Astra.Ink) }
                }
            }
        }
        item {
            Section("About") {
                Text("Ad astra per aspera: to the stars through difficulties.", color = Astra.Text, fontFamily = Serif, fontSize = 16.sp)
                Hint("Your data is stored only on this phone.", Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (adding || editing != null) {
        val h = editing
        var habitName by remember(h) { mutableStateOf(h?.name ?: "") }
        AlertDialog(
            onDismissRequest = { adding = false; editing = null },
            title = { Text(if (h == null) "Add a habit" else "Rename habit") },
            text = {
                OutlinedTextField(habitName, { habitName = it }, label = { Text("Name") }, singleLine = true, colors = fieldColors())
            },
            confirmButton = {
                TextButton(enabled = habitName.isNotBlank(), onClick = {
                    if (h == null) repo.addHabit(habitName.trim(), "") else repo.editHabit(h.id, habitName.trim(), h.emoji)
                    adding = false; editing = null
                }) { Text(if (h == null) "Add" else "Save") }
            },
            dismissButton = { TextButton(onClick = { adding = false; editing = null }) { Text("Cancel") } },
        )
    }

    hiding?.let { h ->
        AlertDialog(
            onDismissRequest = { hiding = null },
            title = { Text("Hide ${h.name}?") },
            text = { Text("It disappears from Today and the grid. Its history is kept, and you can restore it here.") },
            confirmButton = { TextButton(onClick = { repo.setArchived(h.id, true); hiding = null }) { Text("Hide") } },
            dismissButton = { TextButton(onClick = { hiding = null }) { Text("Keep") } },
        )
    }
}
