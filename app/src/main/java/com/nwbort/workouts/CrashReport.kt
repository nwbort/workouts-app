package com.nwbort.workouts

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.time.ZonedDateTime

/**
 * Keeps the stack trace of a crash so the next launch can show it, rather
 * than the app just closing again with nothing to go on. Installed first
 * thing in Application.onCreate, so it also catches crashes during startup.
 */
object CrashReport {
    private fun file(context: Context) = File(context.filesDir, "last-crash.txt")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            save(app, error, thread)
            // Let the system handle it as usual: log it and close the app.
            previous?.uncaughtException(thread, error)
        }
    }

    /** Also for errors caught before they can take the app down, e.g. during startup. */
    fun save(context: Context, error: Throwable, thread: Thread = Thread.currentThread()) {
        runCatching { file(context).writeText(describe(context, thread, error)) }
    }

    fun read(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun describe(context: Context, thread: Thread, error: Throwable): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return buildString {
            appendLine("Workouts $version crashed at ${ZonedDateTime.now()}")
            appendLine("${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), build ${Build.DISPLAY}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(error.stackTraceToString())
        }
    }
}

/** Shown instead of the app after a crash. Deliberately plain: no app state, no dynamic theme. */
@Composable
fun CrashScreen(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp)) {
                Text("Workouts crashed last time", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Copy or share this and send it over. Continue tries again.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Workouts crash", report))
                    }) { Text("Copy") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_SUBJECT, "Workouts crash")
                            .putExtra(Intent.EXTRA_TEXT, report)
                        context.startActivity(Intent.createChooser(send, "Share crash report"))
                    }) { Text("Share") }
                    OutlinedButton(onClick = onDismiss) { Text("Continue") }
                }
                SelectionContainer(
                    Modifier.padding(top = 12.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())
                ) {
                    Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
        }
    }
}
