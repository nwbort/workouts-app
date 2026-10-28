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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.nwbort.workouts.Graph
import com.nwbort.workouts.MainActivity
import com.nwbort.workouts.data.Kind
import com.nwbort.workouts.data.Snapshot
import com.nwbort.workouts.data.shortLabel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Today's workout on the home screen; tap to open the app. */
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
    private fun Body(snap: Snapshot?, today: LocalDate) {
        val colors = GlanceTheme.colors
        val muted = TextStyle(color = colors.onSurfaceVariant, fontSize = 12.sp)
        Column(
            GlanceModifier.fillMaxSize().background(colors.widgetBackground).cornerRadius(20.dp)
                .padding(14.dp).clickable(actionStartActivity<MainActivity>())
        ) {
            if (snap == null) return@Column
            val day = snap.schedule.dayAt(today)
            val week = snap.schedule.weekOf(today)
            val date = today.format(DateTimeFormatter.ofPattern("EEE d MMM"))
            Text(if (week != null) "$date · Week $week" else date, style = muted, maxLines = 1)
            Spacer(GlanceModifier.height(2.dp))
            val title = when {
                day != null -> snap.title(day)
                today.isBefore(snap.schedule.start) -> "Plan starts ${snap.schedule.start.format(DateTimeFormatter.ofPattern("d MMM"))}"
                else -> "Plan complete"
            }
            Text(
                title,
                style = TextStyle(color = colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            if (day == null) return@Column
            Spacer(GlanceModifier.height(6.dp))
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

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
