package com.adastra.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adastra.app.news.NewsRepository
import com.adastra.app.news.Story
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewsScreen(news: NewsRepository) {
    val briefing by news.briefing.collectAsStateWithLifecycle()
    val loading by news.loading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    var failed by remember { mutableStateOf(false) }

    // Fetch automatically when there's nothing yet or the briefing is over 3 hours old.
    LaunchedEffect(Unit) {
        val b = news.briefing.value
        if (b == null || System.currentTimeMillis() - b.fetchedAt > 3 * 3_600_000L) failed = !news.refresh()
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
        item {
            val b = briefing
            ScreenTitle(
                title = "Security news",
                subtitle = when {
                    loading -> "Checking ${com.adastra.app.news.NewsSources.all.size} sources…"
                    b == null -> "Not loaded yet"
                    else -> "Top ${b.stories.size} as of " + Instant.ofEpochMilli(b.fetchedAt).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("EEE d MMM, h:mm a"))
                },
            ) {
                IconButton(onClick = { scope.launch { failed = !news.refresh() } }, enabled = !loading) {
                    Icon(Icons.Filled.Refresh, "Refresh", tint = if (loading) Astra.Line else Astra.Text)
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Astra.Ink, trackColor = Astra.Line)
            if (failed && !loading) Hint("Couldn't reach the news sites. Check your connection and tap refresh.", Modifier.padding(vertical = 8.dp))
            if (b != null && b.stories.isEmpty() && !loading) Hint("No stories from the last 36 hours yet.", Modifier.padding(vertical = 8.dp))
        }
        val stories = briefing?.stories.orEmpty()
        itemsIndexed(stories, key = { _, s -> s.link }) { i, s -> StoryRow(i + 1, s) { uri.openUri(s.link) } }
        item {
            val b = briefing ?: return@item
            Spacer(Modifier.height(8.dp))
            Section("How these are picked") {
                Hint("Stories rank higher when several outlets cover them, when they're recent, and when they mention actively exploited bugs, zero-days or breaches. Tap a story to read it on the site.")
                Spacer(Modifier.height(6.dp))
                Hint("Sources: " + b.sourcesOk.joinToString(", "))
                if (b.sourcesFailed.isNotEmpty()) Hint("Didn't respond this time: " + b.sourcesFailed.joinToString(", "))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoryRow(n: Int, s: Story, onOpen: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        HorizontalDivider(color = Astra.Line)
        Row(Modifier.padding(vertical = 14.dp)) {
            Text("$n", color = Astra.Signal, fontSize = 28.sp, fontWeight = FontWeight.Bold, style = Numbers, modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, color = Astra.Text, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
                if (s.tags.isNotEmpty()) {
                    FlowRow(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        s.tags.forEach { Text(it, color = Astra.Signal, fontSize = 13.sp) }
                    }
                }
                if (s.summary.isNotBlank()) {
                    Text(s.summary, color = Astra.Muted, fontSize = 15.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Text(
                    s.sources.joinToString(", ") + ago(s.published),
                    color = Astra.Ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

private fun ago(t: Long): String {
    if (t == 0L) return ""
    val h = (System.currentTimeMillis() - t) / 3_600_000
    return when { h < 1 -> ", just now"; h < 24 -> ", ${h}h ago"; else -> ", ${h / 24}d ago" }
}
