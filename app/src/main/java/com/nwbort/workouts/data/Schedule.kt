package com.nwbort.workouts.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * A plan laid out on real dates.
 *
 * Works exactly like the streek calendar tool, so the app and the calendar
 * agree: a plan with a race is anchored by its race day and counted back from
 * it; one without (rehab, base, cross training) runs forward from its start
 * date. Day offsets are Monday-based within each week.
 */
class Schedule(val plan: Plan, anchor: LocalDate) {
    private val anchorOffset = plan.race?.let { offset(it.week, it.day) } ?: 0

    /** The date of week 1, day 0. */
    val start: LocalDate = anchor.minusDays(anchorOffset.toLong())
    val end: LocalDate = start.plusDays(plan.weeks * 7L - 1)
    val raceDate: LocalDate? = if (plan.race != null) anchor else null

    private val byOffset = plan.days.associateBy { offset(it.week, it.day) }

    fun dayAt(date: LocalDate): Day? = byOffset[ChronoUnit.DAYS.between(start, date).toInt()]

    fun dateOf(day: Day): LocalDate = start.plusDays(offset(day.week, day.day).toLong())

    /** 1-based week number for [date], or null when it falls outside the plan. */
    fun weekOf(date: LocalDate): Int? {
        val off = ChronoUnit.DAYS.between(start, date)
        return if (off < 0 || date.isAfter(end)) null else (off / 7).toInt() + 1
    }

    fun weekStart(week: Int): LocalDate = start.plusDays((week - 1) * 7L)

    fun daysOfWeek(week: Int): List<Day> = plan.days.filter { it.week == week }

    private fun offset(week: Int, day: Int) = (week - 1) * 7 + day
}

/** Stable across race-date changes: a session is identified by its place in the plan. */
fun sessionKey(slug: String, day: Day, index: Int) = "$slug|${day.week}|${day.day}|$index"
