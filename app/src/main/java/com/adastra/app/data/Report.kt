package com.adastra.app.data

import com.adastra.app.usage.UsageReport
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** The weekly report card: a grade, the main numbers, what stood out and what to change. */
data class ReportCard(
    val start: LocalDate,
    val final: Boolean,           // false while the week is still running
    val days: Int,                // days of the week that count so far
    val score: Int,               // 0..100
    val grade: String,
    val verdict: String,
    val study: Float, val studyGoal: Int, val prevStudy: Float,
    val shift: Float, val shiftGoal: Int, val prevShift: Float,
    val studyDays: Int,
    val blocks: Int, val avgBlock: Int, val longestBlock: Int,
    val breaks: Int, val breakMinutes: Int,
    val bestDay: LocalDate?, val bestDayHours: Float,
    val sleepAvg: Float?,
    val phonePerDay: Float?,      // minutes, when usage access is on
    val insights: List<String>,
    val improve: List<String>,
)

object Report {
    fun build(d: AppData, start: LocalDate, today: LocalDate, phone: UsageReport? = null, prevPhone: UsageReport? = null): ReportCard {
        val end = start.plusDays(6)
        val final = today.isAfter(end)
        val days = if (final) 7 else (ChronoUnit.DAYS.between(start, today).toInt() + 1).coerceIn(1, 7)
        val keys = (0L..6L).map { start.plusDays(it).toString() }.toSet()
        val prevKeys = (0L..6L).map { start.minusWeeks(1).plusDays(it).toString() }.toSet()
        val week = d.sessions.filter { it.date in keys }
        val prev = d.sessions.filter { it.date in prevKeys }
        fun hours(list: List<Session>, kind: String) = list.filter { it.kind == kind }.sumOf { it.minutes } / 60f

        val study = hours(week, KIND_STUDY); val shift = hours(week, KIND_SHIFT)
        val prevStudy = hours(prev, KIND_STUDY); val prevShift = hours(prev, KIND_SHIFT)
        val g = d.goals
        val perDay = week.filter { it.kind == KIND_STUDY }.groupBy { it.date }.mapValues { e -> e.value.sumOf { it.minutes } / 60f }
        val best = perDay.maxByOrNull { it.value }
        val blocks = week.filter { it.kind == KIND_STUDY && !it.manual }
        val breaks = week.filter { it.kind == KIND_BREAK }
        val breakMin = breaks.sumOf { it.minutes }
        val avgBlock = if (blocks.isEmpty()) 0 else blocks.sumOf { it.minutes } / blocks.size
        val longest = blocks.maxOfOrNull { it.minutes } ?: 0
        val stats = Stats.week(d, start, today)
        val sleep = stats.sleepAvg

        // Score: goals are measured against the share of the week that has passed.
        val pace = days / 7f
        fun part(have: Float, goal: Int) = if (goal <= 0) 1f else (have / (goal * pace)).coerceIn(0f, 1f)
        val studyPart = part(study, g.studyWeek)
        val shiftPart = part(shift, g.shiftWeek)
        val steady = (perDay.count { it.value > 0f } / days.toFloat()).coerceIn(0f, 1f)
        val sleepPart = sleep?.let { ((it - 5f) / 2f).coerceIn(0f, 1f) } ?: 0.6f
        val score = (studyPart * 45 + shiftPart * 20 + steady * 20 + sleepPart * 15).roundToInt()
        val grade = when {
            score >= 95 -> "A+"; score >= 85 -> "A"; score >= 75 -> "B"
            score >= 62 -> "C"; score >= 45 -> "D"; else -> "F"
        }
        val verdict = when (grade) {
            "A+", "A" -> "A week to copy."
            "B" -> "Solid. A little more and it's an A."
            "C" -> "Half there. One habit to fix below."
            "D" -> "Behind. Pick the first fix below and start tomorrow."
            else -> if (study + shift == 0f) "Nothing logged yet." else "A rough week. Start small and build it back."
        }

        val insights = mutableListOf<String>()
        best?.let { (day, h) -> insights += "Best day: ${dayName(LocalDate.parse(day))}, ${fmtDuration(h)} of study." }
        val diff = study - prevStudy
        if (final || prevStudy > 0f) when {
            diff >= 0.5f -> insights += "Study up ${fmtDuration(diff)} on the week before."
            diff <= -0.5f -> insights += "Study down ${fmtDuration(-diff)} on the week before."
        }
        if (blocks.isNotEmpty()) insights += "${blocks.size} timer ${plural(blocks.size, "block")}, ${fmtLen(avgBlock)} on average, longest ${fmtLen(longest)}."
        if (breaks.isNotEmpty()) insights += "${breaks.size} ${plural(breaks.size, "break")}, $breakMin min in all" +
            (if (study > 0f) ", about one every ${fmtLen((study * 60 / breaks.size).roundToInt())} of study." else ".")
        if (shift > 0f) insights += "Shift: ${fmtDuration(shift)} of ${g.shiftWeek} h."
        sleep?.let { insights += "Slept ${fmtHours(it)} h a night on average." }
        if (phone != null) {
            val per = phone.dailyAvg
            var line = "Phone: ${fmtLen(per.roundToInt())} a day"
            if (prevPhone != null && prevPhone.totalMinutes > 0f) {
                val ch = (per - prevPhone.dailyAvg).roundToInt()
                if (ch >= 10) line += ", up ${fmtLen(ch)} on the week before"
                if (ch <= -10) line += ", down ${fmtLen(-ch)} on the week before"
            }
            phone.apps.firstOrNull()?.let { line += ". Most used: ${it.label} (${fmtLen(it.minutes.roundToInt())})" }
            insights += "$line."
            phone.duringStudy.firstOrNull()?.takeIf { it.minutes >= 5 }?.let {
                insights += "During study you opened ${it.label} for ${fmtLen(it.minutes.roundToInt())}."
            }
        }

        val improve = mutableListOf<String>()
        val studyShort = g.studyWeek * pace - study
        if (studyShort >= 1f) {
            improve += if (final) "Study came up ${fmtDuration(studyShort)} short. ${fmtDuration(g.studyWeek / 6f)} a day on 6 days reaches ${g.studyWeek} h."
            else "You're ${fmtDuration(studyShort)} behind pace. ${fmtDuration((g.studyWeek - study).coerceAtLeast(0f) / (7 - days + 1))} a day from today still gets you to ${g.studyWeek} h."
        }
        val shiftShort = g.shiftWeek * pace - shift
        if (shiftShort >= 1f) improve += "Shift is ${fmtDuration(shiftShort)} behind the ${g.shiftWeek} h goal. Plan the shifts before Monday."
        val zeroDays = days - perDay.count { it.value > 0f }
        if (zeroDays >= 2) improve += "$zeroDays days with no study. Even one 30-minute block keeps the work going."
        if (blocks.size >= 2 && avgBlock < 30) improve += "Blocks average ${avgBlock} min. Try 50 minutes on, 10 minutes off."
        if (longest >= 100 && breaks.isEmpty()) improve += "Long blocks with no break. A 5–10 minute break every hour keeps focus up."
        if (study > 0f && breakMin > study * 60 * 0.3f) improve += "Breaks came to ${(breakMin / (study * 60) * 100).roundToInt()}% of study time. Keep each one to 10 minutes."
        if (sleep != null && sleep < 7f) improve += "Sleep averaged ${fmtHours(sleep)} h. Aim for 7 or more; it shows up in focus."
        if (phone != null) {
            val dist = phone.distractingMinutes / phone.days
            if (dist >= 90f) improve += "${fmtLen(dist.roundToInt())} a day on distracting apps. Cutting half of that is ${fmtDuration(dist / 2f * 7 / 60f)} more for study a week."
            val late = phone.lateNightMinutes / phone.days
            if (late >= 20f) improve += "${fmtLen(late.roundToInt())} a night on the phone after 11 PM. Put it away by 11."
        }
        if (improve.isEmpty()) improve += if (study >= g.studyWeek) "Keep the same plan. If it felt easy, add 1 h to the weekly goal." else "Keep going the way you are."

        return ReportCard(
            start, final, days, score, grade, verdict,
            study, g.studyWeek, prevStudy, shift, g.shiftWeek, prevShift,
            perDay.count { it.value > 0f }, blocks.size, avgBlock, longest, breaks.size, breakMin,
            best?.key?.let { LocalDate.parse(it) }, best?.value ?: 0f, sleep, phone?.dailyAvg, insights, improve,
        )
    }

    private fun dayName(d: LocalDate) = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    private fun plural(n: Int, w: String) = if (n == 1) w else w + "s"
    fun fmtLen(m: Int) = when { m < 60 -> "$m min"; m % 60 == 0 -> "${m / 60} h"; else -> "${m / 60} h ${m % 60}" }
}
