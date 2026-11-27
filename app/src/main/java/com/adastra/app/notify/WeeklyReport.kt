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
import com.adastra.app.data.Report
import com.adastra.app.data.Stats
import com.adastra.app.data.appToday
import com.adastra.app.data.fmtDuration
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Every Monday morning: last week's report card. */
object WeeklyReport {
    private const val CHANNEL = "report"

    fun createChannel(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Weekly report card", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Monday morning: last week's grade and what to improve"
            },
        )
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            ctx, 9, Intent(ctx, WeeklyReportReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val now = LocalDateTime.now()
        var at = now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).atTime(9, 0)
        if (!at.isAfter(now)) at = at.plusWeeks(1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), pi)
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        val data = (ctx.applicationContext as AdAstraApp).repo.data.value
        val today = appToday()
        val r = Report.build(data, Stats.weekStart(today).minusWeeks(1), today)
        val open = PendingIntent.getActivity(
            ctx, 11,
            Intent(ctx, MainActivity::class.java)
                .putExtra(NewsAlarm.EXTRA_OPEN_TAB, "Week")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = buildString {
            append("Study ${fmtDuration(r.study)} of ${r.studyGoal} h · Shift ${fmtDuration(r.shift)} of ${r.shiftGoal} h\n")
            append(r.verdict)
            r.improve.firstOrNull()?.let { append("\n\nNext: $it") }
        }
        nm.notify(
            30,
            NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_star)
                .setContentTitle("Last week: ${r.grade}")
                .setContentText("Study ${fmtDuration(r.study)} of ${r.studyGoal} h. Tap for the full report card.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

}

class WeeklyReportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WeeklyReport.show(context)
        WeeklyReport.schedule(context)
    }
}
