package com.adastra.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

const val KIND_STUDY = "study"
const val KIND_SHIFT = "shift"

/** Study and Shift habits log hours instead of a plain tick. */
fun hourKind(h: Habit): String? = when {
    h.id == "study" || h.name.trim().equals("study", true) -> KIND_STUDY
    h.id == "shift" || h.name.trim().equals("shift", true) || h.name.trim().equals("work", true) -> KIND_SHIFT
    else -> null
}

/** Minutes of one kind logged on one app day (timed and typed). */
fun dayMinutes(d: AppData, kind: String, date: LocalDate): Int =
    d.sessions.filter { it.kind == kind && it.date == date.toString() }.sumOf { it.minutes }

/** The app day (4 AM to 4 AM) an instant falls on. */
fun appDayOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime().minusHours(DAY_STARTS_AT_HOUR).toLocalDate()

data class WeekHours(val start: LocalDate, val study: Float, val shift: Float)

data class HoursSummary(
    val yearStart: LocalDate,
    val yearEnd: LocalDate,          // last day of the year
    val dayOfYear: Int,              // 1-based, today included
    val daysLeft: Int,               // after today
    val yearStudy: Float,
    val yearGoal: Int,
    val expectedByToday: Float,      // where an even pace would be at the start of today
    val projected: Float,            // at the current pace, by the end of the year
    val neededPerDay: Float,         // from tomorrow on, to still reach the goal
    val weekStudy: Float,
    val weekShift: Float,
    val weekStudyGoal: Int,
    val weekShiftGoal: Int,
    val weekDaysLeft: Int,           // days left this week after today
    val weekDays: List<Pair<Float, Float>>, // Mon..Sun (study, shift)
    val lastWeeks: List<WeekHours>,  // oldest first, this week last
    val todayStudy: Float,
    val todayShift: Float,
)

object Hours {
    /** Hours per kind per day, including the timer that is running right now. */
    fun byDay(d: AppData, now: Long = System.currentTimeMillis()): Map<LocalDate, Pair<Float, Float>> {
        val m = HashMap<LocalDate, FloatArray>()
        fun add(date: LocalDate, kind: String, minutes: Int) {
            val a = m.getOrPut(date) { FloatArray(2) }
            if (kind == KIND_STUDY) a[0] += minutes / 60f else a[1] += minutes / 60f
        }
        d.sessions.forEach { add(LocalDate.parse(it.date), it.kind, it.minutes) }
        d.timer?.let { add(appDayOf(it.start), it.kind, ((now - it.start) / 60_000).toInt()) }
        return m.mapValues { it.value[0] to it.value[1] }
    }

    fun summary(d: AppData, today: LocalDate = appToday(), now: Long = System.currentTimeMillis()): HoursSummary {
        val g = d.goals
        val days = byDay(d, now)
        val start = g.yearStart?.let { LocalDate.parse(it) } ?: today
        val end = start.plusYears(1).minusDays(1)
        val total = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
        val dayOfYear = (ChronoUnit.DAYS.between(start, today) + 1).toInt().coerceIn(1, total)
        val daysLeft = (total - dayOfYear).coerceAtLeast(0)
        val yearStudy = days.filterKeys { !it.isBefore(start) && !it.isAfter(end) }.values.sumOf { it.first.toDouble() }.toFloat()
        val remaining = (g.studyYear - yearStudy).coerceAtLeast(0f)

        val ws = Stats.weekStart(today)
        val weekDays = (0..6).map { days[ws.plusDays(it.toLong())] ?: (0f to 0f) }
        val lastWeeks = (7 downTo 0).map { back ->
            val s = ws.minusWeeks(back.toLong())
            val list = (0..6).map { days[s.plusDays(it.toLong())] ?: (0f to 0f) }
            WeekHours(s, list.sumOf { it.first.toDouble() }.toFloat(), list.sumOf { it.second.toDouble() }.toFloat())
        }
        val t = days[today] ?: (0f to 0f)
        return HoursSummary(
            yearStart = start, yearEnd = end, dayOfYear = dayOfYear, daysLeft = daysLeft,
            yearStudy = yearStudy, yearGoal = g.studyYear,
            expectedByToday = g.studyYear * (dayOfYear - 1) / total.toFloat(),
            projected = yearStudy / dayOfYear * total,
            neededPerDay = if (daysLeft > 0) remaining / daysLeft else remaining,
            weekStudy = weekDays.sumOf { it.first.toDouble() }.toFloat(),
            weekShift = weekDays.sumOf { it.second.toDouble() }.toFloat(),
            weekStudyGoal = g.studyWeek, weekShiftGoal = g.shiftWeek,
            weekDaysLeft = (6 - ChronoUnit.DAYS.between(ws, today).toInt()).coerceAtLeast(0),
            weekDays = weekDays, lastWeeks = lastWeeks,
            todayStudy = t.first, todayShift = t.second,
        )
    }
}

/** "2 h 15 m", "45 m". */
fun fmtDuration(hours: Float): String {
    val totalMin = Math.round(hours * 60)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0 -> "$m m"
        m == 0 -> "$h h"
        else -> "$h h $m m"
    }
}
