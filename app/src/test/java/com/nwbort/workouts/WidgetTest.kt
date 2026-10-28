package com.nwbort.workouts

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import com.nwbort.workouts.data.Plan
import com.nwbort.workouts.data.Schedule
import com.nwbort.workouts.data.Settings
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.data.sessionKey
import com.nwbort.workouts.widget.TodayWidget
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.LocalDate

/** What the home screen widget shows as the day's sessions get ticked off. */
@RunWith(RobolectricTestRunner::class)
class WidgetTest {
    private val plan = Json { ignoreUnknownKeys = true }
        .decodeFromString<Plan>(File("src/main/assets/plans/uta100-training-plan.json").readText())
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

    // Week 11's Wednesday, "Run 10km + Strength"; Thursday is "Run 20km".
    private val wed = LocalDate.of(2026, 10, 28)

    private fun render(today: LocalDate, done: Set<String>, check: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(DpSize(250.dp, 180.dp))
            provideComposable { TodayWidget().Body(Snapshot(settings, schedule, done), today) }
            check()
        }

    private fun allDone(date: LocalDate): Set<String> {
        val day = schedule.dayAt(date)!!
        return day.required.map { sessionKey(plan.slug, day, it) }.toSet()
    }

    @Test
    fun shows_only_today_while_sessions_are_left() = render(wed, emptySet()) {
        onNode(hasTextEqualTo("Run 10km + Strength")).assertExists()
        onNode(hasTextEqualTo("○  Strength Workout 9")).assertExists()
        onAllNodes(hasTextEqualTo("Tomorrow")).assertCountEquals(0)
    }

    @Test
    fun ticks_today_and_shows_tomorrow_once_done() = render(wed, allDone(wed)) {
        onNode(hasTextEqualTo("✓  Run 10km + Strength")).assertExists()
        onNode(hasTextEqualTo("Tomorrow")).assertExists()
        onNode(hasTextEqualTo("Run 20km")).assertExists()
        // Today's own sessions make way for tomorrow's.
        onAllNodes(hasText("Strength Workout 9")).assertCountEquals(0)
    }

    @Test
    fun rest_days_show_tomorrow_too() {
        val rest = generateSequence(wed) { it.plusDays(1) }.first { schedule.dayAt(it)?.isRest == true }
        render(rest, emptySet()) {
            onNode(hasTextEqualTo("Tomorrow")).assertExists()
            onNode(hasTextEqualTo(schedule.dayAt(rest.plusDays(1))!!.title)).assertExists()
        }
    }
}
