package com.adastra.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.data.Repository
import com.adastra.app.data.Word
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WordsScreen(repo: Repository) {
    val data by repo.data.collectAsStateWithLifecycle()
    var newText by remember { mutableStateOf("") }
    var toDelete by remember { mutableStateOf<Word?>(null) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            ScreenTitle("Your words", "Shown on Today and in the evening reminder.")
            Row(Modifier.padding(top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newText, onValueChange = { newText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Add a line", color = Astra.Muted) },
                    colors = fieldColors(),
                )
                Spacer(Modifier.padding(start = 10.dp))
                Button(
                    onClick = { repo.addWord(newText.trim(), null); newText = "" },
                    enabled = newText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Astra.Ink),
                ) { Text("Add") }
            }
        }
        items(data.words.reversed(), key = { it.id }) { w ->
            HorizontalDivider(color = Astra.Line)
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(w.text, color = Astra.Text, fontFamily = Serif, fontSize = 18.sp, lineHeight = 26.sp)
                    w.fromDate?.let {
                        Hint(
                            "From your note on " + LocalDate.parse(it).format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                            Modifier.padding(top = 4.dp),
                        )
                    }
                }
                IconButton(onClick = { toDelete = w }) { Icon(Icons.Filled.Close, "Remove", tint = Astra.Muted) }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    toDelete?.let { w ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Remove this line?") },
            text = { Text(w.text, fontFamily = Serif) },
            confirmButton = { TextButton(onClick = { repo.deleteWord(w.id); toDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Keep") } },
        )
    }
}
