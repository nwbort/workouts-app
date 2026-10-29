package com.nwbort.workouts.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.Day
import com.nwbort.workouts.data.Kind
import com.nwbort.workouts.data.Session
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.data.sessionKey
import com.nwbort.workouts.data.shortLabel
import com.nwbort.workouts.video.VideoState
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun TodayScreen(
    snap: Snapshot,
    today: LocalDate,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    videos: VideoState,
    onToggle: (String, Boolean) -> Unit,
    onPlay: (Day, Int, Session) -> Unit,
    onNotes: (Int) -> Unit,
    onSetUpVideos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sch = snap.schedule
    // A week of slack either side of the plan, stretched to reach today.
    val first = minOf(sch.start.minusDays(7), today)
    val last = maxOf(sch.end.plusDays(7), today)
    val count = ChronoUnit.DAYS.between(first, last).toInt() + 1
    fun pageOf(d: LocalDate) = ChronoUnit.DAYS.between(first, d).toInt().coerceIn(0, count - 1)

    val pager = rememberPagerState(initialPage = pageOf(selected)) { count }
    val scope = rememberCoroutineScope()

    LaunchedEffect(selected, first) {
        val target = pageOf(selected)
        if (pager.currentPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager, first) {
        snapshotFlow { pager.settledPage }.collect { onSelect(first.plusDays(it.toLong())) }
    }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(snap.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                snap.statusLine(today),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val shown = first.plusDays(pager.currentPage.toLong())
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                enabled = pager.currentPage > 0,
            ) { Icon(Icons.Filled.ChevronLeft, "Previous day") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (shown) {
                        today -> "Today"
                        today.plusDays(1) -> "Tomorrow"
                        today.minusDays(1) -> "Yesterday"
                        else -> shown.shortWeekday()
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    shown.longDate(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                enabled = pager.currentPage < count - 1,
            ) { Icon(Icons.Filled.ChevronRight, "Next day") }
        }
        if (shown != today) {
            TextButton(
                onClick = { scope.launch { pager.animateScrollToPage(pageOf(today)) } },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) { Text("Back to today") }
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            val date = first.plusDays(page.toLong())
            val day = sch.dayAt(date)
            if (day == null) {
                EmptyMessage(
                    if (date.isBefore(sch.start)) "Before the plan starts" else "After the plan",
                    "${snap.name} runs ${sch.start.fullDate()} – ${sch.end.fullDate()}.",
                )
            } else {
                DayPage(snap, day, videos, onToggle, onPlay, onNotes, onSetUpVideos)
            }
        }
    }
}

@Composable
private fun DayPage(
    snap: Snapshot,
    day: Day,
    videos: VideoState,
    onToggle: (String, Boolean) -> Unit,
    onPlay: (Day, Int, Session) -> Unit,
    onNotes: (Int) -> Unit,
    onSetUpVideos: () -> Unit,
) {
    val (done, required) = snap.progress(day)
    val missingVideos = day.sessions.any { it.video != null && it.video !in videos.found }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 4.dp)) {
                Text(
                    "Week ${day.week}" + (snap.km(day)?.let { " · ${km(it)} km" } ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(snap.title(day), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (required > 0) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / required },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        drawStopIndicator = {},
                    )
                    Spacer(Modifier.height(4.dp))
                    val extras = day.sessions.count { it.optional }
                    Text(
                        (if (done == required) "All done — nice work" else "$done of $required done") +
                            (if (extras > 0) " · optional extras not counted" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (missingVideos && videos.tree == null) {
            item { VideoSetupBanner(onSetUpVideos) }
        }
        itemsIndexed(day.sessions) { i, session ->
            SessionCard(
                session = session,
                done = snap.isDone(day, i),
                hasVideo = session.video != null && session.video in videos.found,
                folderSet = videos.tree != null,
                onToggle = { onToggle(sessionKey(snap.plan.slug, day, i), it) },
                onPlay = { onPlay(day, i, session) },
            )
        }
        if (snap.plan.weekInfo.any { it.week == day.week && it.notes.isNotEmpty() }) {
            item {
                OutlinedButton(onClick = { onNotes(day.week) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Filled.Notes, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Week ${day.week} coach's notes")
                }
            }
        }
    }
}

@Composable
private fun VideoSetupBanner(onSetUp: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VideoLibrary, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                "Point the app at your downloaded videos to watch them here, offline.",
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            TextButton(onClick = onSetUp) { Text("Set up") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SessionCard(
    session: Session,
    done: Boolean,
    hasVideo: Boolean,
    folderSet: Boolean,
    onToggle: (Boolean) -> Unit,
    onPlay: () -> Unit,
) {
    val rest = session.kind == Kind.REST
    val race = session.kind == Kind.RACE
    // Optional sessions sit back a little: flat and outlined, as extras rather than the day's work.
    val optional = session.optional
    ElevatedCard(
        colors = when {
            race -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            optional -> CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            else -> CardDefaults.elevatedCardColors()
        },
        elevation = if (optional) CardDefaults.elevatedCardElevation(0.dp) else CardDefaults.elevatedCardElevation(),
        modifier = if (optional) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CardDefaults.elevatedShape) else Modifier,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        done -> MaterialTheme.colorScheme.primary
                        optional -> MaterialTheme.colorScheme.surfaceContainerHighest
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = when {
                        done -> MaterialTheme.colorScheme.onPrimary
                        optional -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    },
                ) {
                    Icon(kindIcon(session.kind), null, Modifier.padding(8.dp).size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(session.shortLabel(), style = MaterialTheme.typography.titleMedium)
                    if (optional) {
                        Spacer(Modifier.height(2.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Text(
                                "Optional · doesn't count",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                if (!rest) {
                    Checkbox(
                        checked = done,
                        onCheckedChange = onToggle,
                        modifier = Modifier.semantics { contentDescription = "Mark ${session.shortLabel()} done" },
                    )
                }
            }
            val text = session.text
            if (!text.isNullOrBlank() && !text.startsWith("Follow this")) {
                Spacer(Modifier.height(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
            }
            if (session.tags.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    session.tags.forEach { Tag(it) }
                }
            }
            if (session.video != null) {
                Spacer(Modifier.height(12.dp))
                if (hasVideo) {
                    FilledTonalButton(onClick = onPlay) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Play video")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (folderSet) "Video not found on this phone" else "Videos folder not set up yet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onPlay) { Text(if (folderSet) "Find it" else "Set up") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
