package com.nwbort.workouts

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
        setContent {
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
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
