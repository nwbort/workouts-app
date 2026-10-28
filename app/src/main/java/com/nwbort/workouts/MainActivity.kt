package com.nwbort.workouts

import android.Manifest
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
}
