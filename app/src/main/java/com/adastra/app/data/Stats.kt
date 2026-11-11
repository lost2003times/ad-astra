package com.adastra.app.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

enum class DayState { STEP, REST, ZERO, FUTURE }

data class WeekStats(
    val start: LocalDate,
    val elapsed: Int,
    val nonZero: Int,
    val restDays: Int,
    val totalSteps: Int,
    val sleepAvg: Float?,
    val perHabit: Map<String, Int>,
    val notes: List<Pair<LocalDate, String>>,
    val states: List<DayState>,
)

object Stats {
    fun entry(d: AppData, date: LocalDate): DayEntry? = d.days[date.toString()]
    fun isStep(d: AppData, date: LocalDate) = entry(d, date)?.isStep == true
    fun steps(d: AppData, date: LocalDate) = entry(d, date)?.done?.size ?: 0

    /** Consecutive never-zero days ending today (or yesterday, if today isn't logged yet). */
    fun currentStreak(d: AppData, today: LocalDate): Int {
        var day = if (isStep(d, today)) today else today.minusDays(1)
        var n = 0
        while (isStep(d, day)) { n++; day = day.minusDays(1) }
        return n
    }

    fun bestStreak(d: AppData): Int {
        val dates = d.days.values.filter { it.isStep }.map { LocalDate.parse(it.date) }.sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (x in dates) {
            run = if (prev != null && prev.plusDays(1) == x) run + 1 else 1
            best = maxOf(best, run); prev = x
        }
        return best
    }

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Days before you started (and never filled in) don't count as zeros. */
    fun startDate(d: AppData, today: LocalDate): LocalDate {
        val since = d.since?.let { LocalDate.parse(it) } ?: today
        val earliest = d.days.keys.minOrNull()?.let { LocalDate.parse(it) } ?: since
        return minOf(since, earliest)
    }

    /** A day that counts: started, and not in the future. */
    fun counts(d: AppData, day: LocalDate, today: LocalDate) = !day.isAfter(today) && !day.isBefore(startDate(d, today))

    fun week(d: AppData, start: LocalDate, today: LocalDate): WeekStats {
        val days = (0L..6L).map { start.plusDays(it) }
        // Today is still in progress: it only counts once you've taken a step.
        val past = days.filter { counts(d, it, today) && (it != today || isStep(d, today)) }
        val entries = past.mapNotNull { entry(d, it) }
        val sleeps = entries.mapNotNull { it.sleep }
        val perHabit = mutableMapOf<String, Int>()
        entries.forEach { e -> e.done.forEach { perHabit[it] = (perHabit[it] ?: 0) + 1 } }
        return WeekStats(
            start = start,
            elapsed = past.size,
            nonZero = entries.count { it.isStep },
            restDays = entries.count { it.rest && it.done.isEmpty() },
            totalSteps = entries.sumOf { it.done.size },
            sleepAvg = if (sleeps.isEmpty()) null else sleeps.average().toFloat(),
            perHabit = perHabit,
            notes = past.mapNotNull { day -> entry(d, day)?.note?.takeIf { it.isNotBlank() }?.let { day to it } },
            states = days.map { day ->
                val e = entry(d, day)
                when {
                    !counts(d, day, today) -> DayState.FUTURE
                    day == today && !isStep(d, today) -> DayState.FUTURE
                    e == null -> DayState.ZERO
                    e.done.isNotEmpty() -> DayState.STEP
                    e.rest -> DayState.REST
                    else -> DayState.ZERO
                }
            },
        )
    }
}

fun fmtHours(h: Float): String {
    val r = (h * 2).roundToInt() / 2f
    return if (r % 1f == 0f) r.toInt().toString() else String.format("%.1f", r)
}

/** Builds the encouraging weekly review: wins first, then gentle room to grow. */
object Review {
    data class Result(val wins: List<String>, val grow: List<String>)

    fun build(d: AppData, cur: WeekStats, prev: WeekStats, streak: Int, isCurrent: Boolean): Result {
        val wins = mutableListOf<String>()
        val grow = mutableListOf<String>()
        val habits = d.habits.filter { !it.archived }
        val soFar = if (isCurrent) " so far" else ""

        if (cur.nonZero > 0) wins += "${cur.nonZero} of ${cur.elapsed} day${s(cur.elapsed)} without a zero$soFar."
        if (cur.totalSteps > prev.totalSteps)
            wins += "${cur.totalSteps} habit check-ins$soFar, ${cur.totalSteps - prev.totalSteps} more than last week."
        habits.forEach { h ->
            val c = cur.perHabit[h.id] ?: 0
            val p = prev.perHabit[h.id] ?: 0
            if (c > p) wins += "${h.name}: $c day${s(c)}, up from $p."
        }
        if (streak >= 3) wins += "Current streak: $streak days without a zero."
        if (cur.restDays > 0) wins += "${cur.restDays} planned rest day${s(cur.restDays)}."
        cur.sleepAvg?.let { if (it >= 7f) wins += "Average sleep of ${fmtHours(it)} h." }
        if (wins.isEmpty()) wins += "Nothing logged yet. Tonight's check-in is the first entry."

        val dips = habits.mapNotNull { h ->
            val c = cur.perHabit[h.id] ?: 0
            val p = prev.perHabit[h.id] ?: 0
            if (c < p) Triple(h, c, p) else null
        }.sortedByDescending { it.third - it.second }.take(2)
        dips.forEach { (h, c, p) ->
            grow += "${h.name} dropped from $p to $c day${s(c)}. One more day next week is enough."
        }
        if (dips.isEmpty() && cur.elapsed > 0) {
            habits.minByOrNull { cur.perHabit[it.id] ?: 0 }?.let { h ->
                val c = cur.perHabit[h.id] ?: 0
                if (c * 2 < cur.elapsed) grow += "${h.name}: $c of ${cur.elapsed} days. Add one more."
            }
        }
        val zeros = cur.elapsed - cur.nonZero
        if (zeros > 0) grow += "$zeros day${s(zeros)} with nothing logged. Aim for one fewer next week."
        cur.sleepAvg?.let { if (it < 6.5f) grow += "Average sleep of ${fmtHours(it)} h. More sleep makes the pace easier to sustain." }
        if (grow.isEmpty() && cur.elapsed > 0) grow += "Nothing slipped. Consider raising one target."
        if (grow.isEmpty()) grow += "Log a few days and this fills in."
        return Result(wins, grow)
    }

    private fun s(n: Int) = if (n == 1) "" else "s"
}
