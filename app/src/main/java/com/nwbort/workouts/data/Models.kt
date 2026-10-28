package com.nwbort.workouts.data

import kotlinx.serialization.Serializable

// Mirrors the JSON written by tools/import_streek.py.

/** Where the race sits in a plan: [day] counts from Monday = 0. */
@Serializable
data class RaceDay(val week: Int, val day: Int)

@Serializable
data class PlanSummary(
    val slug: String,
    val title: String,
    val weeks: Int,
    val level: String? = null,
    val race: RaceDay? = null,
)

@Serializable
data class Plan(
    val slug: String,
    val title: String,
    val level: String? = null,
    val weeks: Int,
    val race: RaceDay? = null,
    val days: List<Day>,
    val weekInfo: List<WeekInfo> = emptyList(),
    val glossary: List<GlossaryEntry> = emptyList(),
)

@Serializable
data class Day(
    val week: Int,
    /** Monday = 0 … Sunday = 6, as in the plan; the real date comes from [Schedule]. */
    val day: Int,
    val type: String = "",
    /** The same title the calendar tool gives the day, e.g. "Run 10km + Strength". */
    val title: String,
    val km: Double? = null,
    val sessions: List<Session>,
) {
    val isRest: Boolean get() = sessions.all { it.kind == Kind.REST }
    val isRace: Boolean get() = sessions.any { it.kind == Kind.RACE }

    /** The sessions that count towards finishing the day. */
    val required: List<Int>
        get() = sessions.indices.filter { sessions[it].kind != Kind.REST && !sessions[it].optional }
}

@Serializable
data class Session(
    val title: String,
    val kind: String,
    val text: String? = null,
    /** Vimeo id; the key into the video library. */
    val video: String? = null,
    val tags: List<String> = emptyList(),
    val optional: Boolean = false,
)

object Kind {
    const val RUN = "run"
    const val STRENGTH = "strength"
    const val ROLLING = "rolling"
    const val STRETCHING = "stretching"
    const val CROSS_TRAINING = "cross_training"
    const val DRILL = "drill"
    const val WALK = "walk"
    const val RACE = "race"
    const val REST = "rest"
}

@Serializable
data class WeekInfo(val week: Int, val km: Double? = null, val notes: List<Note> = emptyList())

@Serializable
data class Note(val label: String, val text: String)

@Serializable
data class GlossaryEntry(val term: String, val definition: String)

@Serializable
data class VideoEntry(val id: String, val title: String, val file: String, val group: String)
