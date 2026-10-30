package com.aioffer.radar.feature.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

@Stable
class NotificationPermissionState(
    private val context: Context,
    initiallyGranted: Boolean
) {
    var isGranted by mutableStateOf(initiallyGranted)
        private set
    var wasDenied by mutableStateOf(false)
        private set
    internal var launchPermissionRequest: () -> Unit = {}

    fun request() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !wasDenied) {
            launchPermissionRequest()
        } else if (!isGranted) {
            openSettings()
        }
    }

    fun onPermissionResult(granted: Boolean) {
        isGranted = granted
        wasDenied = !granted
    }

    fun refresh() {
        isGranted = isNotificationPermissionGranted(context)
        if (isGranted) wasDenied = false
    }

    private fun openSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        )
    }
}

@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val state = remember {
        NotificationPermissionState(context, isNotificationPermissionGranted(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        state.onPermissionResult(granted)
    }
    state.launchPermissionRequest = {
        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.refresh()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return state
}

private fun isNotificationPermissionGranted(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED)
