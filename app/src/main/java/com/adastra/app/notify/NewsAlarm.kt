package com.adastra.app.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adastra.app.AdAstraApp
import com.adastra.app.MainActivity
import com.adastra.app.R
import com.adastra.app.data.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** The morning security briefing: fetch, rank, notify with the top story. */
object NewsAlarm {
    private const val CHANNEL = "morning_news"
    const val EXTRA_OPEN_TAB = "open_tab"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Morning security news", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "The day's top cyber security stories, once each morning"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun schedule(ctx: Context, s: Settings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 3, Intent(ctx, NewsReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (!s.newsOn) return
        var at = LocalDate.now().atTime(s.newsHour, s.newsMinute)
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    @SuppressLint("MissingPermission")
    fun notify(ctx: Context) {
        val app = ctx.applicationContext as AdAstraApp
        val b = app.news.briefing.value ?: return
        val top = b.stories.firstOrNull() ?: return
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 4,
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_TAB, "News")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val list = b.stories.mapIndexed { i, s -> "${i + 1}. ${s.title}" }.joinToString("\n")
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_star)
            .setContentTitle("This morning's security news")
            .setContentText(top.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(list))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(11, n)
    }
}

class NewsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as AdAstraApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ok = withTimeoutOrNull(25_000) { app.news.refresh() } ?: false
                if (ok) NewsAlarm.notify(context)
            } finally {
                NewsAlarm.schedule(context, app.repo.data.value.settings)
                pending.finish()
            }
        }
    }
}
