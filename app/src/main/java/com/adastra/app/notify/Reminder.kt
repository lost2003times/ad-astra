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
import com.adastra.app.data.AppData
import com.adastra.app.data.Settings
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Reminder {
    private const val CHANNEL = "nightly"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Nightly check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "A reminder with your own words if the day is still a zero"
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun schedule(ctx: Context, s: Settings) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 1, Intent(ctx, ReminderReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        am.cancel(pi)
        if (!s.reminderOn) return
        var at = LocalDate.now().atTime(s.reminderHour, s.reminderMinute)
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
    }

    @SuppressLint("MissingPermission")
    fun showIfZero(ctx: Context, data: AppData) {
        if (Stats.isStep(data, appToday())) return
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val word = data.words.randomOrNull()?.text ?: "Never a zero."
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_star)
            .setContentTitle("Nothing logged today yet")
            .setContentText(word)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$word\n\nOne habit, or a rest day, keeps the streak alive."))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(10, n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as AdAstraApp).repo
        val data = repo.data.value
        Reminder.showIfZero(context, data)
        Reminder.schedule(context, data.settings)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val settings = (context.applicationContext as AdAstraApp).repo.data.value.settings
            Reminder.schedule(context, settings)
            NewsAlarm.schedule(context, settings)
            WeeklyReport.schedule(context)
        }
    }
}
