package com.nwbort.workouts.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nwbort.workouts.Graph
import com.nwbort.workouts.MainActivity
import com.nwbort.workouts.R
import com.nwbort.workouts.data.shortLabel
import com.nwbort.workouts.widget.TodayWidget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Two daily alarms: the morning notification (when it's switched on) and a
 * refresh just after midnight so the widget moves on to the new day. Both are
 * inexact, which needs no special permission; Android never fires them early,
 * only sometimes a few minutes late.
 */
object DailyScheduler {
    const val ACTION_NOTIFY = "com.nwbort.workouts.NOTIFY"
    const val ACTION_MIDNIGHT = "com.nwbort.workouts.MIDNIGHT"
    private const val CHANNEL = "today"
    private const val NOTIFICATION_ID = 1

    suspend fun schedule(context: Context) {
        val settings = Graph.repo.settings.first()
        val am = context.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()

        val midnight = LocalDate.now().plusDays(1).atTime(0, 1)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(midnight), pending(context, ACTION_MIDNIGHT))

        val notify = pending(context, ACTION_NOTIFY)
        if (settings.notify) {
            val at = LocalTime.of(settings.notifyAt / 60, settings.notifyAt % 60)
            var next = LocalDate.now().atTime(at)
            if (!next.isAfter(now)) next = next.plusDays(1)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis(next), notify)
        } else {
            am.cancel(notify)
        }
    }

    suspend fun postToday(context: Context) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val snap = Graph.repo.currentSnapshot() ?: return
        if (!snap.settings.notify) return
        val today = LocalDate.now()
        val day = snap.schedule.dayAt(today) ?: return
        if (day.isRest) return

        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Today's workout", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "A morning reminder of what today's plan holds" }
        )
        val work = day.sessions.filter { it.kind != "rest" }
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_run)
            .setContentTitle("W${day.week} · ${snap.title(day)}")
            .setContentText(work.joinToString(" · ") { it.shortLabel() })
            .setStyle(NotificationCompat.BigTextStyle().bigText(work.joinToString("\n") { "• " + it.shortLabel() }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, n) }
    }

    private fun pending(context: Context, action: String) = PendingIntent.getBroadcast(
        context, action.hashCode(),
        Intent(context, DailyReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

class DailyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Graph.init(context)
        val pending = goAsync()
        Graph.scope.launch {
            try {
                if (intent.action == DailyScheduler.ACTION_NOTIFY) DailyScheduler.postToday(context)
                // Every trigger (midnight, boot, clock or time zone change) can
                // mean a new "today" for the widget.
                TodayWidget.refresh(context)
                DailyScheduler.schedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
