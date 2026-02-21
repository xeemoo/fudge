package dev.danielc.core.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dev.danielc.core.wifi.model.WifiPermissionState

interface WifiPermissionChecker {
  fun currentState(): WifiPermissionState
  fun requiredPermissions(): Array<String>
}

class AndroidWifiPermissionChecker(
  private val context: Context,
  private val sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT },
  private val permissionStatusProvider: (Context, String) -> Int = { targetContext, permission ->
    targetContext.checkSelfPermission(permission)
  }
) : WifiPermissionChecker {

  override fun currentState(): WifiPermissionState {
    val isGranted = requiredPermissions().all { permission ->
      permissionStatusProvider(context, permission) == PackageManager.PERMISSION_GRANTED
    }
    return if (isGranted) {
      WifiPermissionState.GRANTED
    } else {
      WifiPermissionState.DENIED_CAN_REQUEST
    }
  }

  override fun requiredPermissions(): Array<String> {
    return if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
      arrayOf(
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    } else {
      arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
      )
    }
  }
}
