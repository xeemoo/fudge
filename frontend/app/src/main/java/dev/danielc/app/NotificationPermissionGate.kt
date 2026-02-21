package dev.danielc.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun NotificationPermissionGate() {
  val context = LocalContext.current
  var hasRequestedPermission by rememberSaveable {
    mutableStateOf(context.hasRequestedNotificationPermission())
  }
  val launcher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { _ ->
    // Permission result is handled by system; we only need to avoid re-prompt loops.
  }

  LaunchedEffect(context, hasRequestedPermission) {
    val isGranted = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
    if (NotificationPermissionPolicy.shouldRequestPermission(
        sdkInt = Build.VERSION.SDK_INT,
        isGranted = isGranted,
        hasRequestedBefore = hasRequestedPermission
      )
    ) {
      context.markNotificationPermissionRequested()
      hasRequestedPermission = true
      launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }
}

object NotificationPermissionPolicy {
  fun shouldRequestPermission(
    sdkInt: Int,
    isGranted: Boolean,
    hasRequestedBefore: Boolean
  ): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU &&
      !isGranted &&
      !hasRequestedBefore
  }
}

private fun Context.hasRequestedNotificationPermission(): Boolean {
  val prefs = applicationContext.getSharedPreferences(
    NOTIFICATION_PERMISSION_PREFS_NAME,
    Context.MODE_PRIVATE
  )
  return prefs.getBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, false)
}

private fun Context.markNotificationPermissionRequested() {
  val prefs = applicationContext.getSharedPreferences(
    NOTIFICATION_PERMISSION_PREFS_NAME,
    Context.MODE_PRIVATE
  )
  prefs.edit()
    .putBoolean(KEY_NOTIFICATION_PERMISSION_REQUESTED, true)
    .apply()
}

private const val NOTIFICATION_PERMISSION_PREFS_NAME = "notification_permission_state"
private const val KEY_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested"
