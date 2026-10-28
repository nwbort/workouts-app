package com.nwbort.workouts

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.nwbort.workouts.notify.DailyScheduler
import com.nwbort.workouts.video.PlayerActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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

        // The morning notification carries the same title as the calendar event.
        val app = compose.activity.application
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { DailyScheduler.postToday(app) }
        val posted = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications.single()
        assertEquals("W11 · Run 10km + Strength", posted.extras.getString(Notification.EXTRA_TITLE))

        // The player starts and stops cleanly. (Nothing to decode on the JVM.)
        val intent = PlayerActivity.intent(app, Uri.parse("content://none/strength-workout-9.mp4"), "Strength Workout 9", "1015976604")
        ActivityScenario.launch<PlayerActivity>(intent).use {
            it.moveToState(Lifecycle.State.RESUMED)
            it.moveToState(Lifecycle.State.CREATED)
        }
    }

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
