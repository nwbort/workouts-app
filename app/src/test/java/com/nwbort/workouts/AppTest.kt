package com.nwbort.workouts

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.nwbort.workouts.data.Garmin
import com.nwbort.workouts.data.GarminActivity
import com.nwbort.workouts.notify.DailyScheduler
import com.nwbort.workouts.ui.longDate
import com.nwbort.workouts.video.PlayerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/**
 * Drives the real app through every screen on the JVM, so a crash anywhere in
 * composition fails the build. The PNGs it leaves in app/build/screenshots/
 * are for checking layout without a phone to hand.
 *
 * One test method on purpose: the app's singletons and DataStore files belong
 * to one Application, and Robolectric makes a new one per test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun every_screen_renders_and_today_can_be_ticked_off() {
        // Pin "today" to week 11's Wednesday of the default plan, whatever the
        // real date: the race is 38 days after it.
        runBlocking { Graph.repo.updateSettings { anchor = LocalDate.now().plusDays(38) } }
        waitForText("Run 10km + Strength")
        waitForText("0 of 3 done")
        shot("1-today")

        compose.onNodeWithContentDescription("Mark Strength Workout 9 done").performClick()
        waitForText("1 of 3 done")

        compose.onNodeWithText("Plan").performClick()
        compose.waitForIdle()
        shot("2-plan")

        compose.onNodeWithContentDescription("Glossary").performClick()
        compose.waitForIdle()
        shot("3-glossary")
        back()

        compose.onNodeWithContentDescription("Week 11 notes").performClick()
        waitForText("Week 11 notes")
        shot("4-week-notes")
        back()

        // One video linked by hand, so the list has a playable row (and tries
        // for its thumbnail) next to the ones without a folder.
        runBlocking { Graph.repo.updateSettings { linkVideo("1014769015", "content://none/strength-workout-1.mp4") } }
        compose.onNodeWithText("Videos").performClick()
        waitForText("Linked by hand")
        shot("5-videos")

        compose.onNodeWithText("Settings").performClick()
        compose.waitForIdle()
        shot("6-settings")
        compose.onNodeWithText("Videos folder").performScrollTo()
        compose.waitForIdle()
        shot("7-settings-videos")

        // Tapping the widget lands on today's page from anywhere, even with a
        // screen open on top of another tab. (Settings has a "Plan" heading too.)
        compose.onNode(hasText("Plan") and isTab).performClick()
        compose.onNodeWithContentDescription("Week 11 notes").performClick()
        waitForText("Week 11 notes")
        compose.runOnUiThread { compose.activity.onNewIntent(MainActivity.showToday(compose.activity)) }
        waitForText("1 of 3 done")
        assertEquals(0, compose.onAllNodesWithText("Week 11 notes").fetchSemanticsNodes().size)

        // The morning notification carries the same title as the calendar event.
        val app = compose.activity.application
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { DailyScheduler.postToday(app) }
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("W11 · Run 10km + Strength", posted.extras.getString(Notification.EXTRA_TITLE))

        // Garmin: a synced run shows on today's page, ticks the run off, and
        // the Plan tab shows the distance. Last-synced is now, so the app
        // doesn't try to reach Garmin itself.
        runBlocking {
            Graph.repo.setGarminKey("test-key", LocalDate.now().plusDays(5))
            val run = GarminActivity(1, LocalDate.now().toString(), "running", "Morning Run", 10234.0, 3489.0, "${LocalDate.now()} 06:30:00")
            val snap = Graph.repo.currentSnapshot()!!
            Graph.repo.saveGarminSync(LocalDate.now(), LocalDate.now(), listOf(run), Garmin.autoTicks(snap, listOf(run), emptySet()))
        }
        waitForText("Garmin · 10.2 km · 58:09 · 5:40 /km")
        waitForText("2 of 3 done")
        waitForText("Garmin key expires in 5 days")
        shot("8-today-garmin")
        // Opening the activity hands it to Garmin Connect (or the browser) as
        // a task of its own, not stacked inside this app's.
        compose.onNodeWithText("Garmin · 10.2 km · 58:09 · 5:40 /km").performClick()
        val opened = generateSequence { shadowOf(app).nextStartedActivity }.last { it.action == Intent.ACTION_VIEW }
        assertEquals(Garmin.activityUrl(1), opened.dataString)
        assertTrue(opened.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)

        // A tablet or desktop window: tabs on a rail, and the plan beside the
        // day rather than on a tab of its own.
        RuntimeEnvironment.setQualifiers("w1280dp-h800dp-mdpi")
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("Today") and isTab).performClick()
        waitForText("Week 11")
        waitForText("2 of 3 done")
        assertEquals(listOf("Today", "Videos", "Settings"), tabLabels())
        shot("11-wide-today")

        // Picking a day in the plan shows it beside it. (One whose title no
        // other day shares, so the row to tap is unambiguous.)
        val snap = runBlocking { Graph.repo.currentSnapshot()!! }
        val titles = snap.plan.days.groupingBy { snap.title(it) }.eachCount()
        val later = (2L..6L).map { LocalDate.now().plusDays(it) }
            .first { titles[snap.title(snap.schedule.dayAt(it)!!)] == 1 }
        compose.onNodeWithText(snap.title(snap.schedule.dayAt(later)!!)).performClick()
        waitForText(later.longDate())
        shot("12-wide-other-day")
        compose.onNodeWithText("Back to today").performClick()
        waitForText("2 of 3 done")

        compose.onNodeWithContentDescription("Week 11 notes").performClick()
        waitForText("Week 11 notes")
        shot("13-wide-week-notes")
        back()
        compose.onNode(hasText("Videos") and isTab).performClick()
        compose.waitForIdle()
        shot("14-wide-videos")
        compose.onNode(hasText("Settings") and isTab).performClick()
        compose.waitForIdle()
        shot("15-wide-settings")

        // Back to the phone for the rest.
        RuntimeEnvironment.setQualifiers("w411dp-h891dp-xxhdpi")
        compose.activityRule.scenario.recreate()
        compose.onNode(hasText("Settings") and isTab).performClick()
        compose.onNodeWithText("Sync now").performScrollTo()
        compose.waitForIdle()
        shot("9-settings-garmin")
        compose.onNodeWithText("Disconnect").performClick()
        compose.waitForIdle()
        shot("10-garmin-disconnect")

        // The player starts and stops cleanly. (Nothing to decode on the JVM.)
        val intent = PlayerActivity.intent(app, Uri.parse("content://none/strength-workout-9.mp4"), "Strength Workout 9", "1015976604")
        ActivityScenario.launch<PlayerActivity>(intent).use {
            it.moveToState(Lifecycle.State.RESUMED)
            it.moveToState(Lifecycle.State.CREATED)
        }
    }

    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun tabLabels() = compose.onAllNodes(isTab).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.Text].joinToString() }

    private fun waitForText(text: String) =
        compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    private fun back() {
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
