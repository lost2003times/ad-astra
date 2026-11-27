package com.adastra.app.notify

import android.annotation.SuppressLint
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
import com.adastra.app.data.KIND_STUDY
import com.adastra.app.data.RunningTimer
import com.adastra.app.data.fmtDuration

/** A quiet, ongoing notification with a live clock while study or shift time is running. */
object TimerNotice {
    private const val CHANNEL = "timer"
    private const val ID = 21
    const val ACTION_STOP = "com.adastra.app.STOP_TIMER"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, "Running timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the study or shift clock while it runs"
            setShowBadge(false)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    @SuppressLint("MissingPermission")
    fun sync(ctx: Context, timer: RunningTimer?) {
        val nm = NotificationManagerCompat.from(ctx)
        if (timer == null) { nm.cancel(ID); return }
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 5,
            Intent(ctx, MainActivity::class.java)
                .putExtra(NewsAlarm.EXTRA_OPEN_TAB, "Hours")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            ctx, 6, Intent(ctx, TimerReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_star)
            .setContentTitle(if (timer.kind == KIND_STUDY) "Studying" else "On shift")
            .setContentText("Put the phone down. This counts toward the goal.")
            .setWhen(timer.start)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "Stop and log", stop)
            .build()
        nm.notify(ID, n)
    }
}

class TimerReceiver : BroadcastReceiver() {
    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TimerNotice.ACTION_STOP) return
        val app = context.applicationContext as AdAstraApp
        val s = app.repo.stopTimer()
        TimerNotice.sync(context, null)
        if (s != null) {
            val nm = NotificationManagerCompat.from(context)
            if (nm.areNotificationsEnabled()) {
                nm.notify(
                    22,
                    NotificationCompat.Builder(context, "timer")
                        .setSmallIcon(R.drawable.ic_star)
                        .setContentTitle("Logged ${fmtDuration(s.minutes / 60f)} of ${if (s.kind == KIND_STUDY) "study" else "shift"}")
                        .setTimeoutAfter(8_000)
                        .setAutoCancel(true)
                        .build(),
                )
            }
        }
    }
}
