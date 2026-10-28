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
import com.nwbort.workouts.notify.DailyScheduler
import com.nwbort.workouts.video.VideoState
import com.nwbort.workouts.widget.TodayWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = Graph.repo
    private val library = Graph.videos
    val thumbnails = Graph.thumbnails

    val snapshot: StateFlow<Snapshot?> =
        repo.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val videos: StateFlow<VideoState> = library.state

    private val _today = MutableStateFlow(LocalDate.now())
    /** Re-read on every resume, so leaving the app open overnight still lands on the new day. */
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    val plans: List<PlanSummary> get() = repo.planIndex
    val videoEntries: List<VideoEntry> get() = repo.videos

    fun onResume() {
        _today.value = LocalDate.now()
        library.refresh()
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
