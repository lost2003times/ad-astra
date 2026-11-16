package com.adastra.app.data

import kotlinx.serialization.Serializable

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val emoji: String = "✦",
    val archived: Boolean = false,
)

@Serializable
data class DayEntry(
    val date: String,                 // yyyy-MM-dd
    val done: Set<String> = emptySet(),
    val rest: Boolean = false,        // intentional rest counts as a step
    val sleep: Float? = null,         // hours slept the night before
    val note: String = "",            // one line about the day
) {
    /** "Never a zero": any habit ticked, or a conscious rest day. */
    val isStep: Boolean get() = done.isNotEmpty() || rest
}

@Serializable
data class Word(
    val id: String,
    val text: String,
    val fromDate: String? = null,     // set when saved from a journal line
)

@Serializable
data class Settings(
    val name: String = "",
    val reminderOn: Boolean = true,
    val reminderHour: Int = 21,
    val reminderMinute: Int = 30,
    val newsOn: Boolean = true,
    val newsHour: Int = 8,
    val newsMinute: Int = 0,
)

@Serializable
data class AppData(
    val habits: List<Habit> = emptyList(),
    val days: Map<String, DayEntry> = emptyMap(),
    val words: List<Word> = emptyList(),
    val settings: Settings = Settings(),
    val focus: Map<String, String> = emptyMap(),   // week start (Monday) -> focus text
    val since: String? = null,                     // first day of using the app
)

object Defaults {
    fun data(): AppData = AppData(
        habits = listOf(
            Habit("study", "Study", "📚"),
            Habit("reading", "Reading", "📖"),
            Habit("exercise", "Exercise", "💪"),
            Habit("water", "Water", "💧"),
            Habit("food", "Eat well", "🥗"),
        ),
        words = listOf(
            "Ad astra per aspera: to the stars through difficulties.",
            "One step a day is never a zero.",
            "Small steps add up.",
            "A rest day is part of the plan.",
        ).mapIndexed { i, t -> Word("seed$i", t) },
    )
}
