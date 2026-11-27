package com.adastra.app.usage

import com.adastra.app.data.HoursSummary
import com.adastra.app.data.fmtDuration
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class AdviceSet(val wins: List<String>, val leaks: List<String>, val plan: List<String>)

/** Plain rules over your own numbers. Runs on the phone; no AI, nothing sent anywhere. */
object Advisor {
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy")

    fun build(h: HoursSummary, week: UsageReport?, prevWeek: UsageReport?, sleepAvg: Float?): AdviceSet {
        val wins = mutableListOf<String>()
        val leaks = mutableListOf<String>()
        val plan = mutableListOf<String>()
        val studyLeft = (h.weekStudyGoal - h.weekStudy).coerceAtLeast(0f)
        val shiftLeft = (h.weekShiftGoal - h.weekShift).coerceAtLeast(0f)

        // ---- what's working ----
        if (h.weekStudy >= h.weekStudyGoal) wins += "${fmtDuration(h.weekStudy)} of study this week. The ${h.weekStudyGoal} h goal is done."
        else if (h.weekStudy > 0) wins += "${fmtDuration(h.weekStudy)} of study logged this week."
        if (h.weekShift >= h.weekShiftGoal) wins += "${fmtDuration(h.weekShift)} of shifts this week. That goal is done too."
        val ahead = h.yearStudy - h.expectedByToday
        if (h.yearStudy > 0 && ahead >= 0) wins += "You're ${fmtDuration(ahead)} ahead of the pace for ${h.yearGoal} hours."
        if (week != null && prevWeek != null && prevWeek.totalMinutes > 60) {
            val diff = (prevWeek.totalMinutes - week.totalMinutes) / 60f
            if (diff >= 1f) wins += "Screen time is down ${fmtDuration(diff)} from the week before."
        }
        if (week != null && week.duringStudy.isEmpty() && h.weekStudy >= 1f)
            wins += "No phone use while the study timer was running. That's real focus."

        // ---- where time goes ----
        if (week != null && week.totalMinutes > 0) {
            val perDay = week.dailyAvg / 60f
            leaks += "Screen time: ${fmtDuration(perDay)} a day on average over the last ${week.days} days."

            val top = week.apps.firstOrNull { it.cat.distracting } ?: week.apps.firstOrNull()
            if (top != null && top.minutes >= 60) {
                val pct = ((top.minutes / 60f) / h.weekStudyGoal * 100).roundToInt()
                leaks += "${top.label} took ${fmtDuration(top.minutes / 60f)} in the last ${week.days} days. That's $pct% of a ${h.weekStudyGoal} h study week."
            }
            val distract = week.distractingMinutes / 60f
            if (distract >= 1f) {
                val cats = week.apps.filter { it.cat.distracting }.groupBy { it.cat.label.lowercase() }.keys.joinToString(" and ")
                leaks += if (distract > h.weekStudy)
                    "${cats.replaceFirstChar { it.uppercase() }} got ${fmtDuration(distract)}, more than study got (${fmtDuration(h.weekStudy)})."
                else "${cats.replaceFirstChar { it.uppercase() }} got ${fmtDuration(distract)}, less than study. Keep it that way."
            }
            week.duringStudy.firstOrNull()?.let { worst ->
                val total = week.duringStudy.sumOf { it.minutes.toDouble() }.toFloat() / 60f
                if (total >= 0.25f) leaks += "While the study timer was running you were on your phone for ${fmtDuration(total)}, mostly ${worst.label}."
            }
            val lateAvg = week.lateNightMinutes / week.days / 60f
            if (lateAvg >= 0.5f) {
                val s = if (sleepAvg != null && sleepAvg < 7f) ", and you've averaged ${"%.1f".format(sleepAvg)} h of sleep" else ""
                leaks += "${fmtDuration(lateAvg)} a night on the phone after 11 PM$s."
            }
            if (week.unlocks > 0) {
                val perDayUnlocks = week.unlocks / week.days
                if (perDayUnlocks >= 40) {
                    val every = (16 * 60 / perDayUnlocks.toFloat()).roundToInt()
                    leaks += "You unlock your phone about $perDayUnlocks times a day, once every $every waking minutes."
                }
            }
            val peak = week.perHour.indices.maxByOrNull { week.perHour[it] }
            if (peak != null && week.perHour[peak] / week.days >= 20) {
                val avg = (week.perHour[peak] / week.days).roundToInt()
                plan += "Your heaviest phone hour is ${hourName(peak)}–${hourName((peak + 1) % 24)} ($avg min a day). Protect that hour for study instead."
            }
        }

        // ---- the plan ----
        if (studyLeft > 0 && h.weekDaysLeft >= 0) {
            val days = h.weekDaysLeft + 1
            plan += "${fmtDuration(studyLeft)} of study left this week: about ${fmtDuration(studyLeft / days)} a day for the next $days ${if (days == 1) "day" else "days"}."
        }
        if (shiftLeft > 0 && h.weekShift > 0) plan += "${fmtDuration(shiftLeft)} of shifts left to reach ${h.weekShiftGoal} h."
        if (week != null && studyLeft > 0) {
            val swap = week.distractingMinutes / 60f / 2f
            if (swap >= 1f) plan += if (swap >= studyLeft)
                "Moving half your social and video time (${fmtDuration(swap)}) to study would finish this week's goal."
            else "Moving half your social and video time to study would add ${fmtDuration(swap)} this week."
        }
        val remaining = (h.yearGoal - h.yearStudy).coerceAtLeast(0f)
        if (remaining > 0) {
            plan += "To finish ${h.yearGoal} h by ${h.yearEnd.format(dateFmt)}: ${fmtDuration(h.neededPerDay)} a day, or ${fmtDuration(h.neededPerDay * 7)} a week."
            if (h.dayOfYear >= 7) plan += "At your current pace you'll reach ${h.projected.roundToInt()} h by then."
        }
        if (week == null) plan += "Turn on usage access below to see where your phone time goes."
        return AdviceSet(wins, leaks, plan)
    }

    fun hourName(h: Int): String = when {
        h == 0 -> "12 AM"
        h < 12 -> "$h AM"
        h == 12 -> "12 PM"
        else -> "${h - 12} PM"
    }
}
