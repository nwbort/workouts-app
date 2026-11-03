package com.nwbort.workouts

import com.nwbort.workouts.data.Garmin
import com.nwbort.workouts.data.GarminActivity
import com.nwbort.workouts.data.GarminState
import com.nwbort.workouts.data.Kind
import com.nwbort.workouts.data.Plan
import com.nwbort.workouts.data.Schedule
import com.nwbort.workouts.data.Settings
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.data.sessionKey
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.Base64

/** Reading Garmin's activity list and matching it to the plan. */
class GarminTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val plan = json.decodeFromString<Plan>(File("src/main/assets/plans/uta100-training-plan.json").readText())
    private val settings = Settings(
        planSlug = plan.slug,
        anchor = Settings.DEFAULT_ANCHOR,
        name = Settings.DEFAULT_NAME,
        raceKm = Settings.DEFAULT_RACE_KM,
        videoTree = null,
        videoLinks = emptyMap(),
        notify = true,
        notifyAt = Settings.DEFAULT_NOTIFY_AT,
        askedNotifyPermission = true,
    )
    private val schedule = Schedule(plan, settings.anchor)

    // Week 11's Wednesday, "Run 10km + Strength".
    private val wed = LocalDate.of(2026, 10, 28)
    private val wedDay = schedule.dayAt(wed)!!
    private val runIndex = wedDay.sessions.indexOfFirst { it.kind == Kind.RUN }

    private fun snap(done: Set<String> = emptySet()) = Snapshot(settings, schedule, done)

    private fun activity(id: Long, date: LocalDate, type: String, time: String = "06:30:00", km: Double = 10.0) =
        GarminActivity(id, date.toString(), type, "Morning", km * 1000, 3600.0, "$date $time")

    @Test
    fun parses_the_activity_search_response() {
        val body = """
            [
              {"activityId": 21000000001, "activityName": "Sydney Running", "startTimeLocal": "2026-10-28 06:31:12",
               "activityType": {"typeId": 1, "typeKey": "running"}, "distance": 10234.5, "duration": 3489.2,
               "averageHR": 148.0, "someNewField": {"nested": true}},
              {"activityId": 21000000002, "activityName": "Strength", "startTimeLocal": "2026-10-28 18:00:00",
               "activityType": {"typeKey": "strength_training"}, "duration": 1800},
              {"activityName": "no id, skipped", "startTimeLocal": "2026-10-28 18:00:00"}
            ]
        """.trimIndent()
        val list = Garmin.parseActivities(body)
        assertEquals(2, list.size)
        val run = list[0]
        assertEquals(21000000001L, run.id)
        assertEquals("2026-10-28", run.date)
        assertEquals("running", run.type)
        assertEquals(10.2345, run.km!!, 1e-9)
        assertEquals(3489.2, run.durationS!!, 1e-9)
        assertTrue(run.isRun)
        assertNull(list[1].km)
        assertEquals(emptyList<GarminActivity>(), Garmin.parseActivities("not json"))
    }

    @Test
    fun reads_the_expiry_from_a_jwt() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1793000000,"sub":"x"}""".toByteArray())
        assertEquals(1793000000L, Garmin.tokenExpiry("eyJhbGciOiJIUzI1NiJ9.$payload.sig"))
        assertNull(Garmin.tokenExpiry("opaque"))
    }

    @Test
    fun a_run_ticks_off_the_days_run_and_nothing_else() {
        val acts = listOf(
            activity(1, wed, "trail_running"),
            activity(2, wed, "strength_training", "18:00:00"),
            activity(3, wed.minusDays(400), "running"), // outside the plan
        )
        val ticks = Garmin.autoTicks(snap(), acts, emptySet())
        assertEquals(setOf(sessionKey(plan.slug, wedDay, runIndex)), ticks)
    }

    @Test
    fun a_session_unticked_by_hand_is_not_ticked_again() {
        val key = sessionKey(plan.slug, wedDay, runIndex)
        val acts = listOf(activity(1, wed, "running"))
        assertEquals(emptySet<String>(), Garmin.autoTicks(snap(), acts, applied = setOf(key)))
        assertEquals(emptySet<String>(), Garmin.autoTicks(snap(done = setOf(key)), acts, emptySet()))
    }

    @Test
    fun runs_fill_required_runs_before_optional_ones() {
        // Find a day with an optional run alongside a required one, if the plan has one.
        val day = plan.days.firstOrNull { d ->
            d.sessions.any { it.kind == Kind.RUN && it.optional } && d.sessions.any { it.kind == Kind.RUN && !it.optional }
        } ?: return
        val date = schedule.dateOf(day)
        val required = day.sessions.indices.first { day.sessions[it].kind == Kind.RUN && !day.sessions[it].optional }
        val ticks = Garmin.autoTicks(snap(), listOf(activity(1, date, "running")), emptySet())
        assertEquals(setOf(sessionKey(plan.slug, day, required)), ticks)
    }

    @Test
    fun weekly_distance_sums_runs_only() {
        val s = Snapshot(
            settings, schedule, emptySet(),
            GarminState(apiKey = "k", activities = listOf(
                activity(1, wed, "running", km = 10.0),
                activity(2, wed.plusDays(1), "trail_running", km = 20.5),
                activity(3, wed, "cycling", km = 40.0),
            )),
        )
        assertEquals(30.5, s.kmRun(wed.minusDays(2), wed.plusDays(4))!!, 1e-9)
        assertNull(s.kmRun(wed.plusDays(10), wed.plusDays(12)))
        assertEquals(listOf(1L, 3L), s.activitiesOn(wed).map { it.id })
    }

    @Test
    fun key_expiry_warning() {
        val g = GarminState(apiKey = "k", keyExpires = wed.plusDays(30))
        assertEquals(false, g.needsNewKey(wed))
        assertEquals(true, g.needsNewKey(wed.plusDays(24)))
        assertEquals(true, g.copy(keyRejected = true).needsNewKey(wed))
        assertEquals(false, GarminState().needsNewKey(wed))
    }
}
