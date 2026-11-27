package com.adastra.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.adastra.app.data.Repository
import com.adastra.app.notify.Reminder
import com.adastra.app.ui.Astra
import com.adastra.app.ui.AstraTheme
import androidx.activity.compose.BackHandler
import com.adastra.app.ui.DayPagesScreen
import com.adastra.app.ui.GridScreen
import com.adastra.app.ui.PageTurner
import com.adastra.app.ui.SettingsScreen
import com.adastra.app.ui.NewsScreen
import com.adastra.app.news.NewsRepository
import com.adastra.app.notify.NewsAlarm
import android.content.Intent
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.unit.dp
import com.adastra.app.ui.TodayScreen
import com.adastra.app.ui.WeekScreen
import com.adastra.app.ui.WordsScreen
import com.adastra.app.ui.HoursScreen
import androidx.compose.material.icons.filled.Star
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** A tab a notification asked to open (consumed once shown). */
    private val openTab = mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openTab.value = intent.getStringExtra(NewsAlarm.EXTRA_OPEN_TAB)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val app = application as AdAstraApp
        val repo = app.repo
        Reminder.schedule(this, repo.data.value.settings)
        NewsAlarm.schedule(this, repo.data.value.settings)
        openTab.value = intent?.getStringExtra(NewsAlarm.EXTRA_OPEN_TAB)
        setContent { AstraTheme { AppRoot(repo, app.news, openTab.value) { openTab.value = null } } }
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.CheckCircle),
    Hours("Hours", Icons.Filled.Star),
    News("News", Icons.Filled.Notifications),
    Grid("Grid", Icons.Filled.DateRange),
    Week("Week", Icons.AutoMirrored.Filled.List),
    Words("Words", Icons.Filled.Create),
    Setup("Setup", Icons.Filled.Settings),
}

@Composable
fun AppRoot(repo: Repository, news: NewsRepository, requestedTab: String?, onTabOpened: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    LaunchedEffect(requestedTab) {
        Tab.entries.firstOrNull { it.name == requestedTab }?.let { tab = it }
        if (requestedTab != null) onTabOpened()
    }
    // A past day opened from the Grid as a read-only page (null = none open).
    var openDay by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openDay != null) { openDay = null }
    val ctx = LocalContext.current

    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && repo.data.value.settings.reminderOn &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Box(Modifier.fillMaxSize().background(Astra.Bg)) {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                Column {
                HorizontalDivider(color = Astra.Line)
                NavigationBar(containerColor = Astra.Bg, tonalElevation = 0.dp) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                openDay = null
                                tab = t
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Astra.Text,
                                selectedTextColor = Astra.Text,
                                indicatorColor = Astra.Surface,
                                unselectedIconColor = Astra.Muted,
                                unselectedTextColor = Astra.Muted,
                            ),
                        )
                    }
                }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                val day = openDay
                if (day != null) {
                    DayPagesScreen(repo, LocalDate.parse(day)) { openDay = null }
                } else {
                    PageTurner(tab, order = { it.ordinal }) { t ->
                        when (t) {
                            Tab.Today -> TodayScreen(repo)
                            Tab.Hours -> HoursScreen(repo)
                            Tab.News -> NewsScreen(news)
                            Tab.Grid -> GridScreen(repo) { openDay = it.toString() }
                            Tab.Week -> WeekScreen(repo)
                            Tab.Words -> WordsScreen(repo)
                            Tab.Setup -> SettingsScreen(repo)
                        }
                    }
                }
            }
        }
    }
}
