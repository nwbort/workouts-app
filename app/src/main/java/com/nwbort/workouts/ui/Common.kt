package com.nwbort.workouts.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nwbort.workouts.data.Kind
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun kindIcon(kind: String): ImageVector = when (kind) {
    Kind.RUN -> Icons.AutoMirrored.Filled.DirectionsRun
    Kind.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    Kind.STRENGTH -> Icons.Filled.FitnessCenter
    Kind.ROLLING -> Icons.Filled.SelfImprovement
    Kind.STRETCHING -> Icons.Filled.AccessibilityNew
    Kind.CROSS_TRAINING -> Icons.AutoMirrored.Filled.DirectionsBike
    Kind.DRILL -> Icons.Filled.SportsGymnastics
    Kind.RACE -> Icons.Filled.EmojiEvents
    Kind.REST -> Icons.Filled.Bedtime
    else -> Icons.Filled.Spa
}

private val dayMonth = DateTimeFormatter.ofPattern("d MMM")
private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val shortWeekday = DateTimeFormatter.ofPattern("EEE")
private val fullDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

fun LocalDate.dayMonth(): String = format(dayMonth)
fun LocalDate.longDate(): String = format(weekdayDayMonth)
fun LocalDate.shortWeekday(): String = format(shortWeekday)
fun LocalDate.fullDate(): String = format(fullDate)

fun km(x: Double): String = if (x % 1.0 == 0.0) "%.0f".format(x) else "%.1f".format(x)

fun plural(n: Long, word: String) = if (n == 1L) "1 $word" else "$n ${word}s"

@Composable
fun EmptyMessage(title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Opens a link in whatever handles it (the Garmin Connect app, or a browser)
 * as its own task, so it shows up as a separate app in Recents instead of
 * being stacked inside this one.
 */
fun openLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Nothing to open it with; leave it.
    }
}

/** Window widths, as Material's window size classes draw them. */
const val MEDIUM_WIDTH = 600
const val EXPANDED_WIDTH = 840

/** How wide a column of text or cards gets before it stops growing and centres. */
val READING_WIDTH: Dp = 720.dp

/**
 * Gives [content] the side gutter that keeps it [READING_WIDTH] wide and
 * centred, but never less than [min]. Used as padding inside scrolling
 * containers rather than narrowing them, so a mouse wheel or a swipe in the
 * margins still scrolls.
 */
@Composable
fun WithGutter(min: Dp = 0.dp, modifier: Modifier = Modifier, content: @Composable (gutter: Dp) -> Unit) {
    BoxWithConstraints(modifier) {
        content(maxOf(min, (maxWidth - READING_WIDTH) / 2 + min))
    }
}

/**
 * Centres content at [READING_WIDTH]. Goes after a scroll modifier, so the
 * scrolling area itself still spans the window.
 */
fun Modifier.readingWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = READING_WIDTH)

/**
 * Pull down to fetch Garmin activities now. Only when Garmin is connected;
 * otherwise the content sits in a plain box and pulling does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarminRefresh(
    enabled: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    if (enabled) {
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier, content = content)
    } else {
        Box(modifier, content = content)
    }
}
