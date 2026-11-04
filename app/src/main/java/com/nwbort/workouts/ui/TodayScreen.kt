package com.nwbort.workouts.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.Day
import com.nwbort.workouts.data.Garmin
import com.nwbort.workouts.data.GarminActivity
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
    onGarminKey: () -> Unit = {},
    garminRefreshing: Boolean = false,
    onRefreshGarmin: () -> Unit = {},
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

    WithGutter(modifier = modifier.fillMaxSize()) { gutter ->
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.padding(start = 20.dp + gutter, end = 20.dp + gutter, top = 16.dp)) {
                Text(snap.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    snap.statusLine(today),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (snap.garmin.needsNewKey(today)) {
                GarminKeyBanner(snap, today, onGarminKey, Modifier.padding(horizontal = gutter))
            }
            val shown = first.plusDays(pager.currentPage.toLong())
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp + gutter, vertical = 4.dp),
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

            GarminRefresh(
                enabled = snap.garmin.connected,
                refreshing = garminRefreshing,
                onRefresh = onRefreshGarmin,
                modifier = Modifier.weight(1f),
            ) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
                    val date = first.plusDays(page.toLong())
                    val day = sch.dayAt(date)
                    if (day == null) {
                        EmptyMessage(
                            if (date.isBefore(sch.start)) "Before the plan starts" else "After the plan",
                            "${snap.name} runs ${sch.start.fullDate()} – ${sch.end.fullDate()}.",
                        )
                    } else {
                        DayPage(snap, day, videos, onToggle, onPlay, onNotes, onSetUpVideos, gutter)
                    }
                }
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
    gutter: Dp,
) {
    val (done, required) = snap.progress(day)
    val missingVideos = day.sessions.any { it.video != null && it.video !in videos.found }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp + gutter, end = 16.dp + gutter, top = 4.dp, bottom = 24.dp),
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
        // Activities that match a session show inside its card; any others
        // (a ride, a gym session) get a card of their own.
        val activities = snap.activitiesOn(snap.schedule.dateOf(day))
        val matched = Garmin.assign(snap, activities)
        val unmatched = activities.filter { it !in matched.values }
        if (unmatched.isNotEmpty()) {
            item { GarminCard(unmatched) }
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
                garmin = matched[sessionKey(snap.plan.slug, day, i)],
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
private fun GarminKeyBanner(snap: Snapshot, today: LocalDate, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val left = snap.garmin.daysLeft(today)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    snap.garmin.keyRejected || left == null || left < 0 -> "Garmin key no longer works: runs won't tick off"
                    left == 0L -> "Garmin key expires today"
                    else -> "Garmin key expires in ${plural(left, "day")}"
                },
                Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onFix) { Text("Replace") }
        }
    }
}

/** What Garmin recorded on a day, beside what the plan asked for. */
@Composable
private fun GarminCard(activities: List<GarminActivity>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Also on Garmin", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            activities.forEach { a ->
                Spacer(Modifier.height(4.dp))
                GarminRow(a, showName = true)
            }
        }
    }
}

/**
 * One Garmin activity as a line of stats. Tapping it opens the activity in
 * Garmin Connect: the app if it's installed and handles the link, otherwise
 * the browser. Either way in its own task, not on top of this app's.
 */
@Composable
private fun GarminRow(a: GarminActivity, showName: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Open in Garmin Connect") { openLink(context, Garmin.activityUrl(a.id)) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(kindIcon(if (a.isRun) Kind.RUN else if (a.isWalk) Kind.WALK else Kind.CROSS_TRAINING), null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            listOfNotNull(
                if (showName) a.name.ifBlank { null } else "Garmin",
                a.km?.let { "%.1f km".format(it) },
                a.durationS?.let(::duration),
                a.pace()?.let { "$it /km" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            "Open in Garmin Connect",
            Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Minutes per km for runs and walks, e.g. "5:26". */
private fun GarminActivity.pace(): String? {
    if (!isRun && !isWalk) return null
    val k = km ?: return null
    val secs = durationS ?: return null
    val per = (secs / k).toLong()
    return "%d:%02d".format(per / 60, per % 60)
}

private fun duration(seconds: Double): String {
    val s = seconds.toLong()
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
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
    garmin: GarminActivity? = null,
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
            if (garmin != null) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    GarminRow(garmin, showName = false, modifier = Modifier.padding(horizontal = 10.dp))
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
