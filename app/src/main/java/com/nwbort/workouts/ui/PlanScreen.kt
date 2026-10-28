package com.nwbort.workouts.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.Day
import com.nwbort.workouts.data.Snapshot
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlanScreen(
    snap: Snapshot,
    today: LocalDate,
    onOpenDay: (LocalDate) -> Unit,
    onNotes: (Int) -> Unit,
    onGlossary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sch = snap.schedule
    val weeks = remember(snap.plan) { (1..snap.plan.weeks).map { w -> w to sch.daysOfWeek(w) } }
    // Open on the current week: each week is one header plus its days.
    val startIndex = remember(snap.plan, sch.start) {
        val current = sch.weekOf(today) ?: if (today.isBefore(sch.start)) 1 else snap.plan.weeks
        weeks.takeWhile { it.first < current }.sumOf { 1 + it.second.size }
    }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)

    Column(modifier.fillMaxSize()) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = {
                Column {
                    Text(snap.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${snap.plan.title} · ${sch.start.dayMonth()} – ${sch.end.fullDate()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                if (snap.plan.glossary.isNotEmpty()) {
                    IconButton(onClick = onGlossary) { Icon(Icons.AutoMirrored.Filled.MenuBook, "Glossary") }
                }
            },
        )
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            weeks.forEach { (week, days) ->
                stickyHeader(key = "w$week") { WeekHeader(snap, week, days, onNotes) }
                items(days, key = { "d${it.week}-${it.day}" }) { day ->
                    DayRow(snap, day, today, onClick = { onOpenDay(sch.dateOf(day)) })
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(snap: Snapshot, week: Int, days: List<Day>, onNotes: (Int) -> Unit) {
    val start = snap.schedule.weekStart(week)
    val km = snap.plan.weekInfo.firstOrNull { it.week == week }?.km ?: days.sumOf { it.km ?: 0.0 }
    val (done, required) = days.fold(0 to 0) { acc, d -> snap.progress(d).let { acc.first + it.first to acc.second + it.second } }
    val hasNotes = snap.plan.weekInfo.any { it.week == week && it.notes.isNotEmpty() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Week $week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${start.dayMonth()} – ${start.plusDays(6).dayMonth()}" +
                        (if (km > 0) " · ${km(km)} km" else "") +
                        (if (required > 0) " · $done/$required done" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasNotes) {
                IconButton(onClick = { onNotes(week) }) { Icon(Icons.AutoMirrored.Filled.Notes, "Week $week notes") }
            }
        }
    }
}

@Composable
private fun DayRow(snap: Snapshot, day: Day, today: LocalDate, onClick: () -> Unit) {
    val date = snap.schedule.dateOf(day)
    val isToday = date == today
    val past = date.isBefore(today)
    val (done, required) = snap.progress(day)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth()
            .then(if (isToday) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.widthIn(min = 44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.shortWeekday(), style = MaterialTheme.typography.labelMedium, color = muted)
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
        Spacer(Modifier.width(12.dp))
        Icon(kindIcon(mainKind(day)), null, Modifier.size(20.dp), tint = if (day.isRest) muted else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            snap.title(day),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (day.isRest) muted else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        when {
            required == 0 -> {}
            done == required -> Icon(Icons.Filled.CheckCircle, "Done", tint = MaterialTheme.colorScheme.primary)
            done > 0 || past -> Text("$done/$required", style = MaterialTheme.typography.labelLarge, color = muted)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

/** The session that best describes a day, for its icon. */
private fun mainKind(day: Day): String {
    val order = listOf("race", "run", "strength", "cross_training", "drill", "walk", "rolling", "stretching", "rest")
    return day.sessions.map { it.kind }.minByOrNull { order.indexOf(it).let { i -> if (i < 0) order.size else i } } ?: "rest"
}
