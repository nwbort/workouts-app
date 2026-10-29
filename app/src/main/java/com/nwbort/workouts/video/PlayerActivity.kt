package com.nwbort.workouts.video

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nwbort.workouts.Graph
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Full-screen player for one downloaded video.
 *
 * A separate activity so rotating the phone doesn't restart playback (it
 * handles its own configuration changes) and the system bars can be hidden.
 * Opens in landscape, with the controls' full-screen button to swap to
 * portrait and back. Remembers where you stopped for a few hours, and ticks
 * the session off when the video plays to the end.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private var resumeAt: Long? = null
    private var restored = false
    private var landscape = true

    private val uri by lazy { Uri.parse(intent.getStringExtra(EXTRA_URI)) }
    private val videoId by lazy { intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty() }
    private val doneKey by lazy { intent.getStringExtra(EXTRA_DONE_KEY) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        savedInstanceState?.let {
            resumeAt = it.getLong(STATE_POSITION, -1).takeIf { p -> p >= 0 }
            restored = true
            landscape = it.getBoolean(STATE_LANDSCAPE, true)
        }
        lockOrientation()

        playerView = PlayerView(this).apply {
            setBackgroundColor(Color.BLACK)
            keepScreenOn = true
            setShowNextButton(false)
            setShowPreviousButton(false)
            setShowSubtitleButton(false)
            setFullscreenButtonState(landscape)
            setFullscreenButtonClickListener { full ->
                landscape = full
                lockOrientation()
            }
        }
        val title = TextView(this).apply {
            text = intent.getStringExtra(EXTRA_TITLE)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(playerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(title, FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.TOP or Gravity.START))
        }
        ViewCompat.setOnApplyWindowInsetsListener(title) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(left = bars.left + pad, top = bars.top + pad, right = pad)
            insets
        }
        // The title shows and hides with the playback controls.
        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { title.visibility = it })
        setContentView(root)
    }

    override fun onStart() {
        super.onStart()
        val p = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            setMediaItem(MediaItem.fromUri(uri))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) finished()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Toast.makeText(this@PlayerActivity, "Can't play this file: ${error.errorCodeName}", Toast.LENGTH_LONG).show()
                }
            })
        }
        player = p
        playerView.player = p
        val at = resumeAt
        if (at != null) {
            p.seekTo(at)
            p.prepare()
            p.play()
        } else if (!restored) {
            // First open: offer to carry on from last time, if that was recent.
            restored = true
            lifecycleScope.launch {
                val saved = Graph.repo.savedPosition(videoId)
                val fresh = saved != null && System.currentTimeMillis() - saved.second < TimeUnit.HOURS.toMillis(12)
                if (fresh && saved.first > 5_000) {
                    p.seekTo(saved.first)
                    Toast.makeText(this@PlayerActivity, "Resuming at ${clock(saved.first)}", Toast.LENGTH_SHORT).show()
                }
                p.prepare()
                p.play()
            }
        } else {
            p.prepare()
            p.play()
        }
    }

    override fun onStop() {
        super.onStop()
        val p = player ?: return
        val pos = p.currentPosition
        val dur = p.duration
        val nearEnd = p.playbackState == Player.STATE_ENDED || (dur != C.TIME_UNSET && dur - pos < 15_000)
        resumeAt = pos
        val id = videoId
        Graph.scope.launch { Graph.repo.savePosition(id, if (nearEnd) null else pos) }
        playerView.player = null
        p.release()
        player = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_POSITION, player?.currentPosition ?: resumeAt ?: 0L)
        outState.putBoolean(STATE_LANDSCAPE, landscape)
    }

    private fun lockOrientation() {
        requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }

    private fun finished() {
        val key = doneKey
        val id = videoId
        Graph.scope.launch {
            Graph.repo.savePosition(id, null)
            if (key != null) Graph.repo.setDone(key, true)
        }
        if (key != null) Toast.makeText(this, "Nice work — marked as done", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_VIDEO_ID = "video"
        private const val EXTRA_DONE_KEY = "done_key"
        private const val STATE_POSITION = "position"
        private const val STATE_LANDSCAPE = "landscape"

        fun intent(context: Context, uri: Uri, title: String, videoId: String, doneKey: String? = null) =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URI, uri.toString())
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .putExtra(EXTRA_DONE_KEY, doneKey)

        private fun clock(ms: Long): String {
            val s = ms / 1000
            return "%d:%02d".format(s / 60, s % 60)
        }
    }
}
