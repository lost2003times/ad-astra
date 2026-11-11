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
import com.adastra.app.ui.GridScreen
import com.adastra.app.ui.SettingsScreen
import androidx.compose.ui.unit.dp
import com.adastra.app.ui.TodayScreen
import com.adastra.app.ui.WeekScreen
import com.adastra.app.ui.WordsScreen
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val repo = (application as AdAstraApp).repo
        Reminder.schedule(this, repo.data.value.settings)
        setContent { AstraTheme { AppRoot(repo) } }
    }
}

enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.CheckCircle),
    Grid("Grid", Icons.Filled.DateRange),
    Week("Week", Icons.AutoMirrored.Filled.List),
    Words("Words", Icons.Filled.Create),
    Setup("Setup", Icons.Filled.Settings),
}

@Composable
fun AppRoot(repo: Repository) {
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    var dateStr by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
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
                                if (t == Tab.Today && tab == Tab.Today) dateStr = LocalDate.now().toString()
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
                when (tab) {
                    Tab.Today -> TodayScreen(repo, LocalDate.parse(dateStr)) { dateStr = it.toString() }
                    Tab.Grid -> GridScreen(repo) { dateStr = it.toString(); tab = Tab.Today }
                    Tab.Week -> WeekScreen(repo)
                    Tab.Words -> WordsScreen(repo)
                    Tab.Setup -> SettingsScreen(repo)
                }
            }
        }
    }
}
