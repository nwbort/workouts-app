package com.nwbort.workouts

import com.nwbort.workouts.data.Plan
import com.nwbort.workouts.data.PlanSummary
import com.nwbort.workouts.data.Schedule
import com.nwbort.workouts.data.VideoEntry
import com.nwbort.workouts.video.VideoLibrary
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Checks the bundled plan data and the date maths against the real calendar. */
class PlanDataTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val assets = File("src/main/assets")
    private fun plan(slug: String) = json.decodeFromString<Plan>(File(assets, "plans/$slug.json").readText())
    private val index = json.decodeFromString<List<PlanSummary>>(File(assets, "plans/index.json").readText())
    private val videos = json.decodeFromString<List<VideoEntry>>(File(assets, "videos.json").readText())

    @Test
    fun uta100_lines_up_with_the_google_calendar_import() {
        // The Kepler Challenge calendar: UTA100 counted back from Sat 5 Dec 2026.
        val s = Schedule(plan("uta100-training-plan"), LocalDate.of(2026, 12, 5))
        val wed = s.dayAt(LocalDate.of(2026, 10, 28))!!
        assertEquals(11, wed.week)
        assertEquals("Run 10km + Strength", wed.title)
        assertEquals(listOf("Run", "Strength Workout 9", "Foam Rolling and Massage Ball - 5 Minutes"), wed.sessions.map { it.title })
        assertEquals("Run 20km", s.dayAt(LocalDate.of(2026, 10, 29))!!.title)
        assertEquals("Strength + opt. cross training", s.dayAt(LocalDate.of(2026, 10, 30))!!.title)
        assertEquals("Long Run 43km", s.dayAt(LocalDate.of(2026, 11, 1))!!.title)
        assertTrue(s.dayAt(LocalDate.of(2026, 12, 5))!!.isRace)
        assertEquals(11, s.weekOf(LocalDate.of(2026, 10, 28)))
        assertEquals(LocalDate.of(2026, 8, 17), s.start)
        assertNull(s.dayAt(s.start.minusDays(1)))
        assertNull(s.dayAt(s.end.plusDays(1)))
    }

    @Test
    fun a_plan_without_a_race_runs_forward_from_its_start() {
        val p = index.first { it.race == null }
        val s = Schedule(plan(p.slug), LocalDate.of(2026, 1, 5))
        assertEquals(LocalDate.of(2026, 1, 5), s.start)
        assertEquals(1, s.dayAt(LocalDate.of(2026, 1, 5))!!.week)
        assertNull(s.raceDate)
    }

    @Test
    fun every_plan_parses_with_one_entry_per_day() {
        assertEquals(57, index.size)
        for (summary in index) {
            val p = plan(summary.slug)
            assertEquals(summary.slug, summary.weeks, p.weeks)
            val offsets = p.days.map { (it.week - 1) * 7 + it.day }
            assertEquals(summary.slug, offsets.size, offsets.toSet().size)
            assertTrue(summary.slug, p.days.all { it.sessions.isNotEmpty() && it.title.isNotBlank() })
        }
    }

    @Test
    fun every_session_video_is_in_the_library() {
        val ids = videos.map { it.id }.toSet()
        assertEquals(23, ids.size)
        for (summary in index) {
            plan(summary.slug).days.flatMap { it.sessions }.mapNotNull { it.video }.forEach {
                assertTrue("${summary.slug}: $it", it in ids)
            }
        }
    }

    @Test
    fun downloaded_files_match_their_sessions() {
        // As the streek downloader names them.
        val asDownloaded = videos.associate { VideoLibrary.normalise(it.file.substringBeforeLast('.')) to it.id }
        videos.forEach { assertEquals(it.id, VideoLibrary.match(it, asDownloaded)) }

        // Renamed by hand to the plain title, or given another extension.
        val renamed = videos.associate { VideoLibrary.normalise(it.title) to it.id }
        videos.forEach { assertEquals(it.id, VideoLibrary.match(it, renamed)) }
        assertEquals("strength-workout-9", VideoLibrary.normalise("Strength Workout 9"))

        // Strength Workout 1 must not pick up Strength Workout 10's file.
        val one = videos.first { it.title == "Strength Workout 1" }
        assertNull(VideoLibrary.match(one, mapOf("strength-workout-10" to "x")))
    }
}
