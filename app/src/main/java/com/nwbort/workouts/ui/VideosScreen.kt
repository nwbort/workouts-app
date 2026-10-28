package com.nwbort.workouts.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.VideoEntry
import com.nwbort.workouts.video.Thumbnails
import com.nwbort.workouts.video.VideoLibrary
import com.nwbort.workouts.video.VideoState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosScreen(
    entries: List<VideoEntry>,
    state: VideoState,
    thumbnails: Thumbnails,
    onChooseFolder: (Uri) -> Unit,
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
        if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        LazyColumn(Modifier.fillMaxSize()) {
            // Once a folder is chosen it's managed from Settings; this only
            // comes back if Android stops letting the app read it.
            if (state.tree == null || !state.treeReadable) {
                item { FolderCard(state, onChoose = { pickFolder.launch(state.tree) }) }
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
                        uri = state.found[entry.id],
                        linked = entry.id in state.linked,
                        searched = state.tree != null && !state.scanning,
                        thumbnails = thumbnails,
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
private fun FolderCard(state: VideoState, onChoose: () -> Unit) {
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
                if (tree == null) {
                    "Choose the folder your downloaded videos are in (for example Movies/Streek). " +
                        "Android remembers the choice, so this is a one-off; you can change it later " +
                        "in Settings. Videos are matched to sessions by file name, like strength-workout-9.mp4."
                } else {
                    "This folder can't be read any more. Choose it again."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onChoose) { Text(if (tree == null) "Choose folder" else "Choose again") }
        }
    }
}

@Composable
private fun VideoRow(
    entry: VideoEntry,
    /** Where the video is on the phone, if it was found. */
    uri: Uri?,
    linked: Boolean,
    /** Whether a folder has been looked through yet; until then nothing is "missing". */
    searched: Boolean,
    thumbnails: Thumbnails,
    onPlay: () -> Unit,
    onLink: () -> Unit,
    onUnlink: () -> Unit,
) {
    var menu by rememberSaveable { mutableStateOf(false) }
    val missing = uri == null && searched
    ListItem(
        modifier = if (uri != null) Modifier.clickable(onClick = onPlay) else Modifier,
        headlineContent = { Text(entry.title) },
        supportingContent = when {
            linked -> {
                { Text("Linked by hand", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            missing -> {
                { Text("Not found on this phone", color = MaterialTheme.colorScheme.error) }
            }
            else -> null
        },
        leadingContent = { Thumbnail(uri, thumbnails) },
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

@Composable
private fun Thumbnail(uri: Uri?, thumbnails: Thumbnails) {
    val frame by produceState(uri?.let(thumbnails::cached), uri) {
        if (value == null && uri != null) value = thumbnails.load(uri)
    }
    Box(
        Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val image = frame
        if (image != null) {
            Image(image.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (uri == null) {
            Icon(Icons.Filled.VideocamOff, null, tint = MaterialTheme.colorScheme.outline)
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                null,
                Modifier.size(28.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(3.dp),
                tint = Color.White,
            )
        }
    }
}
