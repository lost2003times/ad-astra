package com.adastra.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.Collections
import java.util.UUID

/** All data lives in one small JSON file on the phone. */
class Repository(private val file: File) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeLock = Mutex()

    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data

    init {
        if (_data.value.since == null) update { it.copy(since = appToday().toString()) }
        if (_data.value.goals.yearStart == null) update { it.copy(goals = it.goals.copy(yearStart = appToday().toString())) }
    }

    private fun load(): AppData = try {
        if (file.exists()) json.decodeFromString<AppData>(file.readText()) else Defaults.data()
    } catch (e: Exception) {
        // Keep the unreadable file aside instead of silently losing it.
        file.copyTo(File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}"), overwrite = true)
        Defaults.data()
    }

    fun update(transform: (AppData) -> AppData) {
        _data.updateAndGet(transform)
        scope.launch {
            writeLock.withLock {
                val tmp = File(file.parentFile, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(AppData.serializer(), _data.value))
                if (!tmp.renameTo(file)) {
                    file.writeText(tmp.readText()); tmp.delete()
                }
            }
        }
    }

    // ---- days ----
    private fun updateDay(date: LocalDate, f: (DayEntry) -> DayEntry) = update { d ->
        val key = date.toString()
        d.copy(days = d.days + (key to f(d.days[key] ?: DayEntry(key))))
    }

    fun toggleHabit(date: LocalDate, id: String) = updateDay(date) { e ->
        e.copy(done = if (id in e.done) e.done - id else e.done + id)
    }

    fun setRest(date: LocalDate, rest: Boolean) = updateDay(date) { it.copy(rest = rest) }
    fun setSleep(date: LocalDate, hours: Float?) = updateDay(date) { it.copy(sleep = hours) }
    fun setNote(date: LocalDate, note: String) = updateDay(date) { it.copy(note = note) }

    // ---- habits ----
    fun addHabit(name: String, emoji: String) = update {
        it.copy(habits = it.habits + Habit(UUID.randomUUID().toString(), name, emoji.ifBlank { "✦" }))
    }

    fun editHabit(id: String, name: String, emoji: String) = update { d ->
        d.copy(habits = d.habits.map { if (it.id == id) it.copy(name = name, emoji = emoji.ifBlank { "✦" }) else it })
    }

    fun setArchived(id: String, archived: Boolean) = update { d ->
        d.copy(habits = d.habits.map { if (it.id == id) it.copy(archived = archived) else it })
    }

    /** Removes the habit and every tick it had. Can't be undone. */
    fun deleteHabit(id: String) = update { d ->
        d.copy(
            habits = d.habits.filterNot { it.id == id },
            days = d.days.mapValues { (_, e) -> if (id in e.done) e.copy(done = e.done - id) else e },
        )
    }

    fun moveHabit(id: String, direction: Int) = update { d ->
        val active = d.habits.filter { !it.archived }.toMutableList()
        val i = active.indexOfFirst { it.id == id }
        val j = i + direction
        if (i < 0 || j !in active.indices) d
        else {
            Collections.swap(active, i, j)
            d.copy(habits = active + d.habits.filter { it.archived })
        }
    }

    // ---- words ----
    fun addWord(text: String, fromDate: String?) = update {
        if (text.isBlank() || it.words.any { w -> w.text == text }) it
        else it.copy(words = it.words + Word(UUID.randomUUID().toString(), text, fromDate))
    }

    fun deleteWord(id: String) = update { d -> d.copy(words = d.words.filterNot { it.id == id }) }

    // ---- hours ----
    fun startTimer(kind: String) {
        stopTimer()
        update { it.copy(timer = RunningTimer(kind, System.currentTimeMillis())) }
    }

    /** Ends the running timer and logs it. Returns the logged session, if any. */
    fun stopTimer(): Session? {
        val t = _data.value.timer ?: return null
        val minutes = ((System.currentTimeMillis() - t.start) / 60_000).toInt().coerceAtMost(16 * 60)
        val s = if (minutes >= 1) Session(UUID.randomUUID().toString(), t.kind, appDayOf(t.start).toString(), t.start, minutes) else null
        update { d -> withStudyTick(d.copy(timer = null, sessions = if (s != null) d.sessions + s else d.sessions), s) }
        return s
    }

    fun discardTimer() = update { it.copy(timer = null) }

    fun addSession(kind: String, date: LocalDate, minutes: Int) {
        if (minutes <= 0) return
        val start = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val s = Session(UUID.randomUUID().toString(), kind, date.toString(), start, minutes, manual = true)
        update { d -> withStudyTick(d.copy(sessions = d.sessions + s), s) }
    }

    fun deleteSession(id: String) = update { d -> d.copy(sessions = d.sessions.filterNot { it.id == id }) }

    fun setGoals(f: (Goals) -> Goals) = update { it.copy(goals = f(it.goals)) }

    /** Studying counts as a step: tick the Study habit for that day if there is one. */
    private fun withStudyTick(d: AppData, s: Session?): AppData {
        if (s == null || s.kind != KIND_STUDY) return d
        val habit = d.habits.firstOrNull { !it.archived && (it.id == "study" || it.name.equals("study", true)) } ?: return d
        val e = d.days[s.date] ?: DayEntry(s.date)
        return d.copy(days = d.days + (s.date to e.copy(done = e.done + habit.id)))
    }

    // ---- focus & settings ----
    fun setFocus(weekStart: LocalDate, text: String) = update { it.copy(focus = it.focus + (weekStart.toString() to text)) }
    fun updateSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }
}
