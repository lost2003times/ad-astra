package com.adastra.app.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adastra.app.AdAstraApp
import com.adastra.app.MainActivity
import com.adastra.app.R
import com.adastra.app.data.RunningTimer
import com.adastra.app.data.KIND_BREAK
import com.adastra.app.data.KIND_STUDY
import com.adastra.app.data.appDayOf
import com.adastra.app.data.fmtDuration

/**
 * The study focus timer: for a set length the phone goes into Do Not Disturb, then sound comes back.
 * Hours aren't logged automatically; the "done" notification offers to add them.
 */
object Focus {
    const val ACTION_END = "com.adastra.app.FOCUS_END"     // the timer ran out
    const val ACTION_STOP = "com.adastra.app.FOCUS_STOP"   // ended early from the notification
    const val ACTION_LOG = "com.adastra.app.FOCUS_LOG"     // "Log to Study" on the done notification
    const val EXTRA_MINUTES = "minutes"
    const val EXTRA_DAY = "day"

    fun hasDndAccess(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun dndSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun start(ctx: Context, minutes: Int, kind: String = KIND_STUDY) {
        val app = ctx.applicationContext as AdAstraApp
        if (app.repo.data.value.timer != null) end(ctx, completed = false)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        var prev = 0
        // Only study goes quiet; a break keeps sound on.
        if (kind == KIND_STUDY && hasDndAccess(ctx)) {
            prev = nm.currentInterruptionFilter
            // Priority-only follows your own Do Not Disturb exceptions (alarms, starred contacts).
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        }
        val t = app.repo.startFocus(minutes, prev, kind)
        scheduleEnd(ctx, t.end ?: return)
    }

    /** Ends the focus block: sound back on, the alarm cancelled, the block recorded. */
    fun end(ctx: Context, completed: Boolean) {
        val app = ctx.applicationContext as AdAstraApp
        val t = app.repo.data.value.timer ?: return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (hasDndAccess(ctx) && t.prevFilter > 0 &&
            nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY
        ) nm.setInterruptionFilter(t.prevFilter)
        ctx.getSystemService(AlarmManager::class.java).cancel(endIntent(ctx))
        val minutes = app.repo.endFocus()
        if (completed && minutes > 0) notifyDone(ctx, t, minutes)
    }

    /** After a restart: finish a block that ran out while the phone was off, or re-arm its alarm. */
    fun resume(ctx: Context) {
        val t = (ctx.applicationContext as AdAstraApp).repo.data.value.timer ?: return
        val end = t.end ?: return
        if (end <= System.currentTimeMillis()) end(ctx, completed = true) else scheduleEnd(ctx, end)
    }

    private fun endIntent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 7, Intent(ctx, FocusReceiver::class.java).setAction(ACTION_END),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    private fun scheduleEnd(ctx: Context, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = endIntent(ctx)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    @SuppressLint("MissingPermission")
    private fun notifyDone(ctx: Context, t: RunningTimer, minutes: Int) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        if (t.kind == KIND_BREAK) {
            nm.notify(
                22,
                NotificationCompat.Builder(ctx, TimerNotice.CHANNEL_DONE)
                    .setSmallIcon(R.drawable.ic_star)
                    .setContentTitle("Break's over")
                    .setContentText("${minutes} min done. Back to it.")
                    .setContentIntent(TimerNotice.openHours(ctx))
                    .setAutoCancel(true)
                    .build(),
            )
            return
        }
        val log = PendingIntent.getBroadcast(
            ctx, 8,
            Intent(ctx, FocusReceiver::class.java).setAction(ACTION_LOG)
                .putExtra(EXTRA_MINUTES, minutes).putExtra(EXTRA_DAY, appDayOf(t.start).toString()),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            22,
            NotificationCompat.Builder(ctx, TimerNotice.CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_star)
                .setContentTitle("Study block done: ${fmtDuration(minutes / 60f)}")
                .setContentText("Sound is back on.")
                .setContentIntent(TimerNotice.openHours(ctx))
                .addAction(0, "Log to Study", log)
                .setAutoCancel(true)
                .build(),
        )
    }
}

/** The ongoing countdown notification while a focus block runs. */
object TimerNotice {
    private const val CHANNEL = "timer"
    const val CHANNEL_DONE = "focus_done"
    private const val ID = 21

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Study timer", NotificationManager.IMPORTANCE_LOW).apply {
            description = "The countdown while a study block runs"
            setShowBadge(false)
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Study block done", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "When a study block ends and sound comes back"
            // Must be allowed through Do Not Disturb so the end is never missed.
            setBypassDnd(true)
        })
    }

    fun openHours(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 5,
        Intent(ctx, MainActivity::class.java)
            .putExtra(NewsAlarm.EXTRA_OPEN_TAB, "Hours")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @SuppressLint("MissingPermission")
    fun sync(ctx: Context, timer: RunningTimer?) {
        val nm = NotificationManagerCompat.from(ctx)
        if (timer?.end == null) { nm.cancel(ID); return }
        if (!nm.areNotificationsEnabled()) return
        val stop = PendingIntent.getBroadcast(
            ctx, 6, Intent(ctx, FocusReceiver::class.java).setAction(Focus.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_star)
            .setContentTitle(if (timer.kind == KIND_BREAK) "On a break" else "Studying · phone on Do Not Disturb")
            .setContentText(if (timer.kind == KIND_BREAK) "You'll get a nudge when it's over." else "Sound comes back when the timer ends.")
            .setWhen(timer.end)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setShowWhen(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openHours(ctx))
            .addAction(0, "End now", stop)
            .build()
        nm.notify(ID, n)
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Focus.ACTION_END -> Focus.end(context, completed = true)
            Focus.ACTION_STOP -> Focus.end(context, completed = false)
            Focus.ACTION_LOG -> {
                val minutes = intent.getIntExtra(Focus.EXTRA_MINUTES, 0)
                val day = intent.getStringExtra(Focus.EXTRA_DAY)?.let { java.time.LocalDate.parse(it) } ?: return
                (context.applicationContext as AdAstraApp).repo.addStudyMinutes(day, minutes)
                NotificationManagerCompat.from(context).cancel(22)
            }
        }
    }
}
