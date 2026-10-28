package com.nwbort.workouts.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nwbort.workouts.data.Day
import com.nwbort.workouts.data.Session
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.data.sessionKey
import com.nwbort.workouts.video.PlayerActivity
import java.time.LocalDate

enum class Tab(val label: String, val icon: ImageVector) {
    Today("Today", Icons.Filled.Today),
    Plan("Plan", Icons.Filled.CalendarMonth),
    Videos("Videos", Icons.Filled.VideoLibrary),
    Settings("Settings", Icons.Filled.Settings),
}

/**
 * Screens that sit on top of the tabs. Kept as a saveable list of strings
 * ("notes:11", "glossary") so a rotation doesn't lose them.
 */
private const val GLOSSARY = "glossary"
private const val NOTES = "notes:"

@Composable
fun WorkoutsApp(vm: AppViewModel) {
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val videos by vm.videos.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(Tab.Today) }
    // The day shown on the Today tab, as an epoch day; null follows today.
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var overlays by rememberSaveable { mutableStateOf(listOf<String>()) }
    val context = LocalContext.current

    BackHandler(enabled = overlays.isNotEmpty()) { overlays = overlays.dropLast(1) }
    BackHandler(enabled = overlays.isEmpty() && tab != Tab.Today) { tab = Tab.Today }

    val s = snap
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val play: (Day, Int, Session) -> Unit = { day, i, session ->
        val uri = vm.videoUri(session.video)
        if (uri == null || session.video == null) {
            tab = Tab.Videos
            overlays = emptyList()
        } else {
            context.startActivity(
                PlayerActivity.intent(context, uri, session.title, session.video, sessionKey(s.plan.slug, day, i))
            )
        }
    }
    val openNotes: (Int) -> Unit = { week -> overlays = overlays + "$NOTES$week" }
    val openGlossary = { overlays = overlays + GLOSSARY }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = {
                                // Tapping Today again jumps back to today.
                                if (t == Tab.Today && tab == Tab.Today) selected = null
                                tab = t
                            },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { padding ->
            val mod = Modifier.padding(padding)
            when (tab) {
                Tab.Today -> TodayScreen(
                    snap = s,
                    today = today,
                    selected = selected?.let(LocalDate::ofEpochDay) ?: today,
                    onSelect = { selected = if (it == today) null else it.toEpochDay() },
                    videos = videos,
                    onToggle = vm::setDone,
                    onPlay = play,
                    onNotes = openNotes,
                    onSetUpVideos = { tab = Tab.Videos },
                    modifier = mod,
                )
                Tab.Plan -> PlanScreen(
                    snap = s,
                    today = today,
                    onOpenDay = { date ->
                        selected = if (date == today) null else date.toEpochDay()
                        tab = Tab.Today
                    },
                    onNotes = openNotes,
                    onGlossary = openGlossary,
                    modifier = mod,
                )
                Tab.Videos -> VideosScreen(
                    entries = vm.videoEntries,
                    state = videos,
                    onChooseFolder = vm::setVideoFolder,
                    onRescan = vm::rescanVideos,
                    onLink = vm::linkVideo,
                    onPlay = { entry ->
                        vm.videoUri(entry.id)?.let {
                            context.startActivity(PlayerActivity.intent(context, it, entry.title, entry.id))
                        }
                    },
                    modifier = mod,
                )
                Tab.Settings -> SettingsScreen(
                    snap = s,
                    plans = vm.plans,
                    onUpdate = vm::updateSettings,
                    onReset = { vm.resetProgress(s.plan.slug); toast(context, "Ticks cleared") },
                    modifier = mod,
                )
            }
        }

        val top = overlays.lastOrNull()
        if (top != null) {
            // An opaque Surface over the tabs, so they keep their scroll and
            // pager positions underneath.
            Surface(Modifier.fillMaxSize()) {
                val back = { overlays = overlays.dropLast(1) }
                when {
                    top == GLOSSARY -> GlossaryScreen(s.plan.glossary, onBack = back)
                    top.startsWith(NOTES) -> WeekNotesScreen(
                        snap = s,
                        week = top.removePrefix(NOTES).toIntOrNull() ?: 1,
                        onWeek = { w -> overlays = overlays.dropLast(1) + "$NOTES$w" },
                        onBack = back,
                    )
                }
            }
        }
    }
}

private fun toast(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

fun Snapshot.statusLine(today: LocalDate): String {
    val sch = schedule
    val race = sch.raceDate
    val week = sch.weekOf(today)
    val daysTo = { d: LocalDate -> java.time.temporal.ChronoUnit.DAYS.between(today, d) }
    return when {
        today.isBefore(sch.start) -> "Starts ${sch.start.fullDate()} · in ${plural(daysTo(sch.start), "day")}"
        week == null -> "Plan finished"
        race != null && today == race -> "Week $week of ${plan.weeks} · Race day!"
        race != null && today.isBefore(race) -> "Week $week of ${plan.weeks} · ${plural(daysTo(race), "day")} to race day"
        else -> "Week $week of ${plan.weeks}"
    }
}
