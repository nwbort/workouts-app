package com.nwbort.workouts.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.GlossaryEntry
import com.nwbort.workouts.data.Snapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeekNotesScreen(snap: Snapshot, week: Int, onWeek: (Int) -> Unit, onBack: () -> Unit) {
    val notes = snap.plan.weekInfo.firstOrNull { it.week == week }?.notes.orEmpty()
    // Keep the same tab (say, Nutrition) when stepping between weeks.
    var label by rememberSaveable { mutableStateOf(notes.firstOrNull()?.label) }
    val index = notes.indexOfFirst { it.label == label }.takeIf { it >= 0 } ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text("Week $week notes")
                        val start = snap.schedule.weekStart(week)
                        Text(
                            "${start.dayMonth()} – ${start.plusDays(6).dayMonth()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onWeek(week - 1) }, enabled = week > 1) {
                        Icon(Icons.Filled.ChevronLeft, "Previous week")
                    }
                    IconButton(onClick = { onWeek(week + 1) }, enabled = week < snap.plan.weeks) {
                        Icon(Icons.Filled.ChevronRight, "Next week")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (notes.isEmpty()) {
                EmptyMessage("No notes this week")
                return@Column
            }
            if (notes.size > 1) {
                PrimaryScrollableTabRow(selectedTabIndex = index, edgePadding = 16.dp) {
                    notes.forEachIndexed { i, n ->
                        Tab(selected = i == index, onClick = { label = n.label }, text = { Text(n.label) })
                    }
                }
            }
            SelectionContainer {
                Text(
                    notes[index].text,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlossaryScreen(entries: List<GlossaryEntry>, onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableIntStateOf(-1) }
    val shown = remember(entries, query) {
        entries.withIndex().filter {
            query.isBlank() || it.value.term.contains(query, true) || it.value.definition.contains(query, true)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text("Glossary") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search terms") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                )
            }
            items(shown, key = { it.index }) { (i, e) ->
                val expanded = open == i || query.isNotBlank()
                Card(Modifier.fillMaxWidth().clickable { open = if (open == i) -1 else i }) {
                    Column(Modifier.padding(16.dp).animateContentSize()) {
                        Text(e.term, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            SelectionContainer { Text(e.definition, style = MaterialTheme.typography.bodyMedium) }
                        } else {
                            Text(
                                e.definition.lineSequence().first(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
