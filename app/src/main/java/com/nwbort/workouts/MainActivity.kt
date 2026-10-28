package com.nwbort.workouts

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nwbort.workouts.ui.AppViewModel
import com.nwbort.workouts.ui.WorkoutsApp
import com.nwbort.workouts.ui.theme.WorkoutsTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        val crash = CrashReport.read(this)
        setContent {
            // After a crash, show what happened before touching any app state.
            var report by remember { mutableStateOf(crash) }
            val shown = report
            if (shown != null) {
                CrashScreen(shown, onDismiss = { CrashReport.clear(this); report = null })
                return@setContent
            }
            WorkoutsTheme {
                LifecycleResumeEffect(Unit) {
                    vm.onResume()
                    onPauseOrDispose {}
                }
                AskForNotificationsOnce()
                WorkoutsApp(vm)
            }
        }
    }

    // Public so the UI test can deliver one the way the launcher would.
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.action == ACTION_SHOW_TODAY) vm.showToday()
    }

    /** The morning reminder is on by default; ask for the permission it needs, once. */
    @androidx.compose.runtime.Composable
    private fun AskForNotificationsOnce() {
        val snap by vm.snapshot.collectAsStateWithLifecycle()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        val settings = snap?.settings ?: return
        LaunchedEffect(settings.askedNotifyPermission) {
            if (settings.notify && !settings.askedNotifyPermission) {
                vm.updateSettings { askedNotifyPermission = true }
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    companion object {
        private const val ACTION_SHOW_TODAY = "com.nwbort.workouts.SHOW_TODAY"

        /**
         * Opens the app on today's page, whatever it was last showing: the
         * widget and the morning reminder both use this. Clears anything
         * stacked on top (like the player) and reuses the running activity.
         */
        fun showToday(context: Context): Intent = Intent(context, MainActivity::class.java)
            .setAction(ACTION_SHOW_TODAY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
