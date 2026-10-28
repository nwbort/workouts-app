package com.nwbort.workouts.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.VideoEntry
import com.nwbort.workouts.video.VideoLibrary
import com.nwbort.workouts.video.VideoState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    entries: List<VideoEntry>,
    state: VideoState,
    onChooseFolder: (Uri) -> Unit,
    onRescan: () -> Unit,
    onLink: (String, Uri?) -> Unit,
    onPlay: (VideoEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onChooseFolder(uri)
    }
    // Which video a single-file pick is for; saveable, as the picker can
    // outlive this activity.
    var linking by rememberSaveable { mutableStateOf<String?>(null) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = linking
        if (uri != null && id != null) onLink(id, uri)
        linking = null
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Videos") }, windowInsets = WindowInsets(0))
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                FolderCard(
                    state = state,
                    total = entries.size,
                    onChoose = { pickFolder.launch(state.tree) },
                    onRescan = onRescan,
                )
            }
            entries.groupBy { it.group }.forEach { (group, items) ->
                item(key = "g:$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(items, key = { it.id }) { entry ->
                    VideoRow(
                        entry = entry,
                        found = entry.id in state.found,
                        linked = entry.id in state.linked,
                        searched = state.tree != null && !state.scanning,
                        onPlay = { onPlay(entry) },
                        onLink = {
                            linking = entry.id
                            pickFile.launch(arrayOf("video/*"))
                        },
                        onUnlink = { onLink(entry.id, null) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FolderCard(state: VideoState, total: Int, onChoose: () -> Unit, onRescan: () -> Unit) {
    val tree = state.tree
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Folder, null)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (tree == null) "No videos folder yet" else VideoLibrary.describe(tree),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    tree == null ->
                        "Choose the folder your downloaded videos are in (for example Movies/Streek). " +
                            "Android remembers the choice, so this is a one-off. Videos are matched to " +
                            "sessions by file name, like strength-workout-9.mp4."
                    !state.treeReadable -> "This folder can't be read any more. Choose it again."
                    else -> "${state.found.size} of $total videos found. Anything missing can be " +
                        "linked to a file by hand from its menu."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.scanning) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tree == null) {
                    Button(onClick = onChoose) { Text("Choose folder") }
                } else {
                    OutlinedButton(onClick = onChoose) { Text("Change folder") }
                    OutlinedButton(onClick = onRescan) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Rescan")
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoRow(
    entry: VideoEntry,
    found: Boolean,
    linked: Boolean,
    /** Whether a folder has been looked through yet; until then nothing is "missing". */
    searched: Boolean,
    onPlay: () -> Unit,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    ListItem(
        modifier = if (found) Modifier.clickable(onClick = onPlay) else Modifier,
        headlineContent = { Text(entry.title) },
        supportingContent = {
            Text(
                when {
                    linked -> "Linked by hand"
                    found || !searched -> entry.file
                    else -> "Not found — expected ${entry.file}"
                },
                color = if (found || !searched) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        },
        leadingContent = {
            Icon(
                Icons.Filled.PlayArrow,
                null,
                tint = if (found) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(if (linked) "Link a different file…" else "Link a file…") },
                        leadingIcon = { Icon(Icons.Filled.Link, null) },
                        onClick = { menu = false; onLink() },
                    )
                    if (linked) {
                        DropdownMenuItem(
                            text = { Text("Remove link") },
                            leadingIcon = { Icon(Icons.Filled.LinkOff, null) },
                            onClick = { menu = false; onUnlink() },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(),
    )
}
