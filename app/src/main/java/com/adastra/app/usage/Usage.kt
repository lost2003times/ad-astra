package com.adastra.app.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Rough buckets for where phone time goes. */
enum class Cat(val label: String, val distracting: Boolean) {
    Social("Social", true),
    Video("Video", true),
    Games("Games", true),
    Messaging("Messaging", false),
    Browser("Browser", false),
    Learning("Study & work", false),
    Music("Music", false),
    Other("Other", false),
}

data class AppUse(val pkg: String, val label: String, val cat: Cat, val minutes: Float)

data class UsageReport(
    val days: Int,
    val totalMinutes: Float,
    val apps: List<AppUse>,                 // most used first
    val perDay: Map<LocalDate, Float>,      // minutes per app day
    val perHour: FloatArray,                // minutes per clock hour (0..23), summed over the range
    val lateNightMinutes: Float,            // 11 PM to 4 AM
    val unlocks: Int,
    val duringStudy: List<AppUse>,          // phone use while a study session was running
) {
    val dailyAvg get() = totalMinutes / days
    fun catMinutes(c: Cat) = apps.filter { it.cat == c }.sumOf { it.minutes.toDouble() }.toFloat()
    val distractingMinutes get() = apps.filter { it.cat.distracting }.sumOf { it.minutes.toDouble() }.toFloat()
}

/**
 * Reads the same screen-time data Digital Wellbeing uses, through UsageStatsManager.
 * Needs "Usage access", which the user grants in system settings. Nothing leaves the phone.
 */
object PhoneUsage {
    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        else @Suppress("DEPRECATION") ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun settingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = android.net.Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Foreground time per app over [from, to), split by hour and app day.
     * [study] holds the study intervals (start, end) in epoch millis.
     */
    fun report(ctx: Context, from: Long, to: Long, days: Int, study: List<Pair<Long, Long>>): UsageReport {
        val usm = ctx.getSystemService(UsageStatsManager::class.java)
        val pm = ctx.packageManager
        val ignore = ignored(ctx)
        val zone = ZoneId.systemDefault()

        val perApp = HashMap<String, Float>()
        val perDay = HashMap<LocalDate, Float>()
        val perHour = FloatArray(24)
        val inStudy = HashMap<String, Float>()
        var late = 0f
        var unlocks = 0

        fun close(pkg: String, a: Long, b: Long) {
            if (b <= a || pkg in ignore) return
            perApp[pkg] = (perApp[pkg] ?: 0f) + (b - a) / 60_000f
            for ((s, e) in study) {
                val o = minOf(b, e) - maxOf(a, s)
                if (o > 0) inStudy[pkg] = (inStudy[pkg] ?: 0f) + o / 60_000f
            }
            // Walk hour by hour so each slice lands in the right hour and day.
            var t = a
            while (t < b) {
                val dt = Instant.ofEpochMilli(t).atZone(zone)
                val nextHour = dt.withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant().toEpochMilli()
                val sliceEnd = minOf(b, nextHour)
                val min = (sliceEnd - t) / 60_000f
                val hour = dt.hour
                perHour[hour] += min
                if (hour >= 23 || hour < 4) late += min
                val day = dt.toLocalDateTime().minusHours(4).toLocalDate()
                perDay[day] = (perDay[day] ?: 0f) + min
                t = sliceEnd
            }
        }

        val events = usm.queryEvents(from, to)
        val e = UsageEvents.Event()
        var current: String? = null
        var since = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val t = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    if (current != e.packageName) {
                        current?.let { close(it, since, t) }
                        current = e.packageName; since = t
                    }
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> if (current == e.packageName) { close(current!!, since, t); current = null }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> { current?.let { close(it, since, t) }; current = null }
                UsageEvents.Event.KEYGUARD_HIDDEN -> unlocks++
            }
        }
        current?.let { close(it, since, minOf(to, System.currentTimeMillis())) }

        fun use(pkg: String, min: Float) = AppUse(pkg, label(pm, pkg), category(pm, pkg), min)
        val apps = perApp.filterValues { it >= 1f }.map { use(it.key, it.value) }.sortedByDescending { it.minutes }
        return UsageReport(
            days = days,
            totalMinutes = apps.sumOf { it.minutes.toDouble() }.toFloat(),
            apps = apps,
            perDay = perDay,
            perHour = perHour,
            lateNightMinutes = late,
            unlocks = unlocks,
            duringStudy = inStudy.filterValues { it >= 1f }.map { use(it.key, it.value) }.sortedByDescending { it.minutes },
        )
    }

    /** The launcher, system UI and this app aren't "screen time" worth advice. */
    private fun ignored(ctx: Context): Set<String> {
        val home = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
        return setOfNotNull(
            home, ctx.packageName, "android", "com.android.systemui", "com.sec.android.app.launcher",
            "com.samsung.android.app.aodservice", "com.android.settings", "com.google.android.permissioncontroller",
            "com.samsung.android.incallui", "com.android.incallui",
        )
    }

    private fun label(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (_: Exception) {
        pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }

    private val known = mapOf(
        Cat.Social to listOf(
            "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.facebook.lite",
            "com.snapchat.android", "com.twitter.android", "com.reddit.frontpage", "com.pinterest",
            "com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.linkedin.android", "com.quora.android",
            "com.sharechat.app", "in.mohalla.sharechat", "com.moj.android",
        ),
        Cat.Video to listOf(
            "com.google.android.youtube", "app.revanced.android.youtube", "com.netflix.mediaclient",
            "com.amazon.avod.thirdpartyclient", "in.startv.hotstar", "com.jio.media.ondemand", "tv.twitch.android.app",
            "com.mxtech.videoplayer.ad", "com.disney.disneyplus", "com.crunchyroll.crunchyroid",
        ),
        Cat.Messaging to listOf(
            "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.discord", "org.thoughtcrime.securesms",
            "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.facebook.orca",
        ),
        Cat.Browser to listOf(
            "com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser",
            "com.microsoft.emmx", "com.opera.browser", "com.duckduckgo.mobile.android",
        ),
        Cat.Learning to listOf(
            "md.obsidian", "notion.id", "com.duolingo", "org.coursera.android", "com.udemy.android",
            "com.google.android.apps.docs", "com.google.android.apps.docs.editors.docs", "com.microsoft.office.onenote",
            "com.microsoft.office.word", "com.adobe.reader", "com.amazon.kindle", "com.google.android.apps.classroom",
            "org.khanacademy.android", "com.anthropic.claude", "com.openai.chatgpt", "com.termux", "com.github.android",
            "com.google.android.keep", "com.samsung.android.app.notes", "com.microsoft.teams",
        ),
    ).flatMap { (c, list) -> list.map { it to c } }.toMap()

    fun category(pm: PackageManager, pkg: String): Cat {
        known[pkg]?.let { return it }
        val info = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return Cat.Other }
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> Cat.Games
            ApplicationInfo.CATEGORY_SOCIAL -> Cat.Social
            ApplicationInfo.CATEGORY_VIDEO -> Cat.Video
            ApplicationInfo.CATEGORY_AUDIO -> Cat.Music
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> Cat.Learning
            else -> if (info.flags and ApplicationInfo.FLAG_IS_GAME != 0) Cat.Games else Cat.Other
        }
    }
}
