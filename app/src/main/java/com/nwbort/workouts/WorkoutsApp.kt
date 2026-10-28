package com.nwbort.workouts

import android.app.Application
import android.content.Context
import com.nwbort.workouts.data.Repository
import com.nwbort.workouts.notify.DailyScheduler
import com.nwbort.workouts.video.VideoLibrary
import com.nwbort.workouts.widget.TodayWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WorkoutsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Graph.scope.launch {
            DailyScheduler.schedule(this@WorkoutsApp)
            TodayWidget.publishPreview(this@WorkoutsApp)
        }
    }
}

/** The app's few singletons. Receivers and the widget reach them here too. */
object Graph {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var repo: Repository
        private set
    lateinit var videos: VideoLibrary
        private set

    fun init(context: Context) {
        if (::repo.isInitialized) return
        repo = Repository(context.applicationContext)
        videos = VideoLibrary(context.applicationContext, repo, scope)
    }
}
