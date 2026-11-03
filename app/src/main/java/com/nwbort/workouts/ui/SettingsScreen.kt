package com.nwbort.workouts.ui

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.Garmin
import com.nwbort.workouts.data.PlanSummary
import com.nwbort.workouts.data.Repository
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.video.VideoLibrary
import com.nwbort.workouts.video.VideoState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

private val DAY_NAMES = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    snap: Snapshot,
    plans: List<PlanSummary>,
    onUpdate: (Repository.SettingsEditor.() -> Unit) -> Unit,
    onReset: () -> Unit,
    videos: VideoState,
    videoCount: Int,
    onChooseVideoFolder: (Uri) -> Unit,
    onRescanVideos: () -> Unit,
    garminSyncing: Boolean,
    onConnectGarmin: (String, LocalDate) -> Unit,
    onSyncGarmin: () -> Unit,
    onDisconnectGarmin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = snap.settings
    val plan = snap.plan
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val askNotify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onChooseVideoFolder(uri)
    }

    Column(modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Settings") }, windowInsets = WindowInsets(0))
        Column(Modifier.verticalScroll(rememberScrollState()).readingWidth()) {
            Section("Plan")
            ListItem(
                modifier = Modifier.clickable { dialog = "plan" },
                headlineContent = { Text("Training plan") },
                supportingContent = { Text("${plan.title} · ${plan.weeks} weeks" + (plan.level?.let { " · $it" } ?: "")) },
            )
            val race = plan.race
            ListItem(
                modifier = Modifier.clickable { dialog = "date" },
                headlineContent = { Text(if (race != null) "Race date" else "Start date") },
                supportingContent = {
                    Text(
                        s.anchor.fullDate() + "\n" +
                            if (race != null) "The race is the ${DAY_NAMES[race.day]} of week ${race.week}; everything counts back from it."
                            else "This plan has no race, so it runs forward from this date."
                    )
                },
            )
            var name by remember(plan.slug) { mutableStateOf(s.name) }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; onUpdate { this.name = it } },
                label = { Text("Name") },
                placeholder = { Text(plan.title) },
                supportingText = { Text("What to call this plan in the app") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            if (race != null) {
                var raceKm by remember(plan.slug) { mutableStateOf(s.raceKm) }
                OutlinedTextField(
                    value = raceKm,
                    onValueChange = { v -> raceKm = v.filter { it.isDigit() || it == '.' }; onUpdate { this.raceKm = raceKm } },
                    label = { Text("Race distance (km)") },
                    placeholder = { Text(plan.days.firstOrNull { it.isRace }?.km?.let(::km) ?: "") },
                    supportingText = { Text("Leave blank to use the plan's own") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }

            Section("Morning reminder")
            ListItem(
                headlineContent = { Text("Daily notification") },
                supportingContent = { Text("Today's workout, every morning. Skipped on rest days.") },
                trailingContent = {
                    Switch(checked = s.notify, onCheckedChange = { on ->
                        if (on) askNotify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        onUpdate { notify = on }
                    })
                },
            )
            ListItem(
                modifier = if (s.notify) Modifier.clickable { dialog = "time" } else Modifier,
                headlineContent = { Text("Time") },
                supportingContent = { Text("%d:%02d".format(s.notifyAt / 60, s.notifyAt % 60)) },
            )

            Section("Videos")
            val tree = videos.tree
            ListItem(
                modifier = Modifier.clickable { pickFolder.launch(tree) },
                headlineContent = { Text("Videos folder") },
                supportingContent = {
                    Text(
                        when {
                            tree == null -> "Not chosen yet"
                            !videos.treeReadable -> VideoLibrary.describe(tree) + "\nCan't be read any more. Tap to choose it again."
                            else -> VideoLibrary.describe(tree) + "\n${videos.found.size} of $videoCount videos found. " +
                                "Missing ones can be linked by hand from their menu on the Videos tab."
                        }
                    )
                },
            )
            if (tree != null) {
                ListItem(
                    modifier = if (videos.scanning) Modifier else Modifier.clickable(onClick = onRescanVideos),
                    headlineContent = { Text("Rescan folder") },
                    supportingContent = { Text("Look again for newly downloaded videos") },
                    trailingContent = if (videos.scanning) {
                        { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp) }
                    } else null,
                )
            }

            Section("Garmin Connect")
            val g = snap.garmin
            if (!g.connected) {
                ListItem(
                    modifier = Modifier.clickable { dialog = "garmin" },
                    headlineContent = { Text("Connect Garmin") },
                    supportingContent = {
                        Text("Tick runs off automatically once your watch has synced them, and see the distance you actually ran.")
                    },
                )
            } else {
                val today = LocalDate.now()
                ListItem(
                    modifier = if (garminSyncing) Modifier else Modifier.clickable(onClick = onSyncGarmin),
                    headlineContent = { Text("Sync now") },
                    supportingContent = {
                        val runs = g.activities.count { it.isRun }
                        Text(
                            listOfNotNull(
                                g.lastSync?.let { "Last synced ${syncTime(it)} · ${plural(runs.toLong(), "run")} found" }
                                    ?: "Not synced yet",
                                g.lastError,
                            ).joinToString("\n")
                        )
                    },
                    trailingContent = if (garminSyncing) {
                        { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp) }
                    } else null,
                )
                ListItem(
                    modifier = Modifier.clickable { dialog = "garmin" },
                    headlineContent = { Text("API key") },
                    supportingContent = {
                        val left = g.daysLeft(today)
                        Text(
                            when {
                                g.keyRejected -> "Turned down by Garmin. Tap to paste a new one."
                                left == null -> "Tap to replace it"
                                left < 0 -> "Expired ${g.keyExpires?.fullDate()}. Tap to paste a new one."
                                else -> "Expires ${g.keyExpires?.fullDate()} (in ${plural(left, "day")}). Tap to replace it."
                            },
                            color = if (g.needsNewKey(today)) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified,
                        )
                    },
                )
                ListItem(
                    modifier = Modifier.clickable { dialog = "garmin-off" },
                    headlineContent = { Text("Disconnect") },
                    supportingContent = { Text("Forget the key and Garmin's activities. Ticks stay.") },
                )
            }

            Section("Progress")
            ListItem(
                modifier = Modifier.clickable { dialog = "reset" },
                headlineContent = { Text("Clear ticks for this plan") },
                supportingContent = { Text("Un-tick every session in ${snap.name}") },
            )

            Section("About")
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            }
            ListItem(
                headlineContent = { Text("Workouts ${version ?: ""}") },
                supportingContent = { Text("Plans and videos from Streek, imported from the streek repo.") },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    when (dialog) {
        "plan" -> PlanPicker(plans, plan.slug, onDismiss = { dialog = null }) { picked ->
            dialog = null
            if (picked.slug != plan.slug) onUpdate {
                planSlug = picked.slug
                // A name and race distance belong to the old plan's event.
                name = ""
                raceKm = ""
            }
        }
        "date" -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = s.anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            onUpdate { anchor = d }
                        }
                        dialog = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            ) { DatePicker(state) }
        }
        "time" -> {
            val state = rememberTimePickerState(s.notifyAt / 60, s.notifyAt % 60, is24Hour = true)
            AlertDialog(
                onDismissRequest = { dialog = null },
                confirmButton = {
                    TextButton(onClick = {
                        onUpdate { notifyAt = state.hour * 60 + state.minute }
                        dialog = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
                text = { TimePicker(state) },
            )
        }
        "garmin" -> GarminKeyDialog(
            onDismiss = { dialog = null },
            onConnect = { key, expires -> dialog = null; onConnectGarmin(key, expires) },
        )
        "garmin-off" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Disconnect Garmin?") },
            text = { Text("The app forgets the key and the activities it fetched. Sessions already ticked stay ticked.") },
            confirmButton = { TextButton(onClick = { onDisconnectGarmin(); dialog = null }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        "reset" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear all ticks?") },
            text = { Text("Every session in ${snap.name} goes back to not done. This can't be undone.") },
            confirmButton = { TextButton(onClick = { onReset(); dialog = null }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GarminKeyDialog(onDismiss: () -> Unit, onConnect: (String, LocalDate) -> Unit) {
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    var days by rememberSaveable { mutableStateOf(365) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect Garmin") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "In Garmin Connect, open Settings → Personal API token, choose Create Key, " +
                        "and copy the key: Garmin shows it only once. The app uses it to read your " +
                        "activities; it can't change anything in your account.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { openLink(context, Garmin.KEY_PAGE) }) { Text("Open Garmin Connect") }
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("Personal API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Expiry you chose for it", style = MaterialTheme.typography.labelLarge)
                Garmin.KEY_LIFETIMES.forEach { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { days = d },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == d, onClick = { days = d })
                        Text(if (d == 365) "1 year" else "$d days")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = key.isNotBlank(),
                onClick = { onConnect(key, LocalDate.now().plusDays(days.toLong())) },
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun syncTime(ms: Long): String {
    val t = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    val time = "%d:%02d".format(t.hour, t.minute)
    return if (t.toLocalDate() == LocalDate.now()) "today at $time" else "${t.toLocalDate().dayMonth()} at $time"
}

@Composable
private fun Section(title: String) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun PlanPicker(
    plans: List<PlanSummary>,
    current: String,
    onDismiss: () -> Unit,
    onPick: (PlanSummary) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Training plan") },
        text = {
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(plans, key = { it.slug }) { p ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(p) },
                        leadingContent = { RadioButton(selected = p.slug == current, onClick = { onPick(p) }) },
                        headlineContent = { Text(p.title) },
                        supportingContent = { Text("${p.weeks} weeks" + if (p.race == null) " · no race day" else "") },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
