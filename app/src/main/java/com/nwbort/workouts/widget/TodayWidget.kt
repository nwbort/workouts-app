package com.nwbort.workouts.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.nwbort.workouts.Graph
import com.nwbort.workouts.MainActivity
import com.nwbort.workouts.data.Day
import com.nwbort.workouts.data.Kind
import com.nwbort.workouts.data.Session
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.data.sessionKey
import com.nwbort.workouts.data.shortLabel
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Today's workout on the home screen, one tile per session to tick off with a tap; tap anywhere
 * else to open the app on today's page. Once today is all ticked off, or on a rest day, it shows
 * tomorrow's as well.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Graph.init(context)
        val repo = Graph.repo
        provideContent {
            val snap by repo.snapshot.collectAsState(initial = null)
            GlanceTheme { Body(snap, LocalDate.now()) }
        }
    }

    /**
     * What the home screen's widget picker shows: today's real workout,
     * rendered by the widget itself rather than a static image.
     */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        Graph.init(context)
        val snap = Graph.repo.currentSnapshot()
        provideContent { GlanceTheme { Body(snap, LocalDate.now()) } }
    }

    @Composable
    internal fun Body(snap: Snapshot?, today: LocalDate) {
        val colors = GlanceTheme.colors
        val muted = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp)
        Column(
            GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(20.dp)
                .padding(14.dp).clickable(actionStartActivity(MainActivity.showToday(LocalContext.current)))
        ) {
            if (snap == null) return@Column
            val day = snap.schedule.dayAt(today)
            val week = snap.schedule.weekOf(today)
            val date = today.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            Text(if (week != null) "$date · Week $week" else date, style = muted, maxLines = 1)
            Spacer(GlanceModifier.height(2.dp))
            if (day == null) {
                val title = if (today.isBefore(snap.schedule.start)) {
                    "Plan starts ${snap.schedule.start.format(DateTimeFormatter.ofPattern("d MMM"))}"
                } else {
                    "Plan complete"
                }
                Text(title, style = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold), maxLines = 2)
                return@Column
            }
            // Once today is ticked off (or is a rest day), make room for what's next.
            val finished = snap.isComplete(day)
            Text(
                if (finished) "✓  ${snap.title(day)}" else snap.title(day),
                style = TextStyle(
                    color = if (finished) colors.primary else colors.onSurface,
                    fontSize = if (finished) 16.sp else 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = if (finished) 1 else 2,
            )
            if (!finished && !day.isRest) {
                Spacer(GlanceModifier.height(8.dp))
                Tiles(snap, day, GlanceModifier.fillMaxWidth().defaultWeight())
                return@Column
            }
            val tomorrow = snap.schedule.dayAt(today.plusDays(1)) ?: return@Column
            Spacer(GlanceModifier.height(8.dp))
            Text("Tomorrow", style = muted, maxLines = 1)
            Text(
                snap.title(tomorrow),
                style = TextStyle(color = colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Sessions(snap, tomorrow)
        }
    }

    @Composable
    private fun Sessions(snap: Snapshot, day: Day) {
        val colors = GlanceTheme.colors
        Spacer(GlanceModifier.height(4.dp))
        day.sessions.forEachIndexed { i, s ->
            if (s.kind == Kind.REST) return@forEachIndexed
            val done = snap.isDone(day, i)
            Row(GlanceModifier.padding(vertical = 1.dp)) {
                Text(
                    (if (done) "✓  " else "○  ") + s.shortLabel(),
                    style = TextStyle(color = if (done) colors.primary else colors.onSurface, fontSize = 13.sp),
                    maxLines = 1,
                )
            }
        }
    }

    /**
     * Today's sessions as big squares that tick off with a tap, in up to two rows so
     * five- and six-session rehab days stay tappable.
     */
    @Composable
    private fun Tiles(snap: Snapshot, day: Day, modifier: GlanceModifier) {
        val sessions = day.sessions.indices.filter { day.sessions[it].kind != Kind.REST }
        val rows = (sessions.size + 2) / 3
        val perRow = (sessions.size + rows - 1) / rows
        Column(modifier) {
            sessions.chunked(perRow).forEachIndexed { r, chunk ->
                if (r > 0) Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    chunk.forEachIndexed { c, i ->
                        if (c > 0) Spacer(GlanceModifier.width(6.dp))
                        Tile(snap, day, i, GlanceModifier.defaultWeight().fillMaxHeight())
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(snap: Snapshot, day: Day, index: Int, modifier: GlanceModifier) {
        val colors = GlanceTheme.colors
        val s = day.sessions[index]
        val done = snap.isDone(day, index)
        val fg = when {
            done -> colors.onPrimary
            s.optional -> colors.onSurfaceVariant
            else -> colors.onSecondaryContainer
        }
        Column(
            modifier
                .background(if (done) colors.primary else if (s.optional) colors.surfaceVariant else colors.secondaryContainer)
                .cornerRadius(14.dp)
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .clickable(actionRunCallback<ToggleSession>(actionParametersOf(KEY to sessionKey(snap.plan.slug, day, index)))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (done) "✓" else "○",
                style = TextStyle(color = fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                maxLines = 1,
            )
            Text(
                s.tileLabel(),
                style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center),
                maxLines = 2,
            )
            if (s.optional) {
                Text("optional", style = TextStyle(color = fg, fontSize = 10.sp, textAlign = TextAlign.Center), maxLines = 1)
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { TodayWidget().updateAll(context) }
        }

        /** Re-render the picker preview. The platform rate-limits this, so failures are fine. */
        suspend fun publishPreview(context: Context) {
            runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(TodayWidgetReceiver::class) }
        }
    }
}

internal val KEY = ActionParameters.Key<String>("session")

/** Ticks a session off from its tile, or unticks it. */
class ToggleSession : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[KEY] ?: return
        Graph.init(context)
        Graph.repo.setDone(key, key !in Graph.repo.done.first())
        TodayWidget().update(context, glanceId)
    }
}

/** A name short enough for a tile; "optional" goes on its own line. */
internal fun Session.tileLabel(): String {
    val calf = title.startsWith("Calf")
    return when (kind) {
        Kind.RUN, Kind.WALK -> shortLabel().removePrefix("Optional ").replaceFirstChar { it.uppercase() }
        Kind.STRENGTH -> if (calf) "Calf strength" else title.replace("Strength Workout", "Strength")
        Kind.STRETCHING -> if (calf) "Calf stretch" else "Stretch"
        Kind.ROLLING -> "Foam roll"
        Kind.CROSS_TRAINING -> "Cross train"
        Kind.RACE -> "Race"
        else -> title
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
