package com.nwbort.workouts.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nwbort.workouts.Graph
import com.nwbort.workouts.data.PlanSummary
import com.nwbort.workouts.data.Repository
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.data.VideoEntry
import com.nwbort.workouts.garmin.GarminSync
import com.nwbort.workouts.notify.DailyScheduler
import com.nwbort.workouts.video.VideoState
import com.nwbort.workouts.widget.TodayWidget
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Graph.repo
    private val library = Graph.videos
    private val garmin = Graph.garmin
    val thumbnails = Graph.thumbnails

    val snapshot: StateFlow<Snapshot?> =
        repo.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val videos: StateFlow<VideoState> = library.state

    private val _today = MutableStateFlow(LocalDate.now())
    /** Re-read on every resume, so leaving the app open overnight still lands on the new day. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    private val _todayRequests = Channel<Unit>(Channel.CONFLATED)
    /** Fires when the widget or the reminder asks for today's page. */
    val todayRequests: Flow<Unit> = _todayRequests.receiveAsFlow()

    fun showToday() {
        _todayRequests.trySend(Unit)
    }

    val plans: List<PlanSummary> get() = repo.planIndex
    val videoEntries: List<VideoEntry> get() = repo.videos

    fun onResume() {
        _today.value = LocalDate.now()
        library.refresh()
        viewModelScope.launch {
            _garminSyncing.value = true
            try { garmin.syncIfStale() } finally { _garminSyncing.value = false }
        }
    }

    private val _garminSyncing = MutableStateFlow(false)
    val garminSyncing: StateFlow<Boolean> = _garminSyncing.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** Short notes for a toast, like how a Garmin sync went. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun syncGarmin() = viewModelScope.launch {
        if (_garminSyncing.value) return@launch
        _garminSyncing.value = true
        val r = try { garmin.sync() } finally { _garminSyncing.value = false }
        _messages.trySend(
            when (r) {
                is GarminSync.Result.Synced ->
                    "Garmin: ${r.activities} activit${if (r.activities == 1) "y" else "ies"}" +
                        if (r.ticked > 0) ", ${r.ticked} session${if (r.ticked == 1) "" else "s"} ticked off" else ""
                is GarminSync.Result.Failed -> r.message
                GarminSync.Result.NotConnected -> "Garmin isn't connected"
            }
        )
    }

    private val _garminRefreshing = MutableStateFlow(false)
    /** A pull to refresh is waiting on Garmin. Background syncs on resume don't show. */
    val garminRefreshing: StateFlow<Boolean> = _garminRefreshing.asStateFlow()

    /** Pull to refresh: sync now, or wait out a sync that's already going. */
    fun refreshGarmin() = viewModelScope.launch {
        if (_garminRefreshing.value) return@launch
        _garminRefreshing.value = true
        try {
            if (_garminSyncing.value) _garminSyncing.first { !it } else syncGarmin().join()
        } finally {
            _garminRefreshing.value = false
        }
    }

    fun connectGarmin(key: String, expires: LocalDate) = viewModelScope.launch {
        repo.setGarminKey(key.trim(), expires)
        GarminSync.schedule(app, connected = true)
        syncGarmin().join()
    }

    fun disconnectGarmin() = viewModelScope.launch {
        repo.setGarminKey(null, null)
        GarminSync.schedule(app, connected = false)
    }

    fun setDone(key: String, done: Boolean) = viewModelScope.launch {
        repo.setDone(key, done)
        TodayWidget.refresh(app)
    }

    fun updateSettings(block: Repository.SettingsEditor.() -> Unit) = viewModelScope.launch {
        repo.updateSettings(block)
        DailyScheduler.schedule(app)
        TodayWidget.refresh(app)
    }

    fun resetProgress(slug: String) = viewModelScope.launch {
        repo.resetProgress(slug)
        TodayWidget.refresh(app)
    }

    fun setVideoFolder(tree: Uri) = viewModelScope.launch { library.setFolder(tree) }

    fun linkVideo(id: String, file: Uri?) = viewModelScope.launch { library.link(id, file) }

    fun rescanVideos() = library.refresh()

    fun videoUri(id: String?): Uri? = library.uriFor(id)
}
