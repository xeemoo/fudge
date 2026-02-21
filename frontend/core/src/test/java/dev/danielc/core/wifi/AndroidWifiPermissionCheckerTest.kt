package dev.danielc.core.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import dev.danielc.core.wifi.model.WifiPermissionState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidWifiPermissionCheckerTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  @Test
  fun requiredPermissions_android13AndAbove_returnsNearbyWifiAndFineLocation() {
    val checker = AndroidWifiPermissionChecker(
      context = context,
      sdkIntProvider = { 33 }
    )

    assertArrayEquals(
      arrayOf(
        Manifest.permission.NEARBY_WIFI_DEVICES,
        Manifest.permission.ACCESS_FINE_LOCATION
      ),
      checker.requiredPermissions()
    )
  }

  @Test
  fun requiredPermissions_android12AndBelow_returnsCoarseAndFineLocation() {
    val checker = AndroidWifiPermissionChecker(
      context = context,
      sdkIntProvider = { 32 }
    )

    assertArrayEquals(
      arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
      ),
      checker.requiredPermissions()
    )
  }

  @Test
  fun currentState_allRequiredPermissionsGranted_returnsGranted() {
    val checker = AndroidWifiPermissionChecker(
      context = context,
      sdkIntProvider = { 33 },
      permissionStatusProvider = { _, _ -> PackageManager.PERMISSION_GRANTED }
    )

    assertEquals(WifiPermissionState.GRANTED, checker.currentState())
  }

  @Test
  fun currentState_anyRequiredPermissionDenied_returnsDeniedCanRequest() {
    val checker = AndroidWifiPermissionChecker(
      context = context,
      sdkIntProvider = { 32 },
      permissionStatusProvider = { _, permission ->
        if (permission == Manifest.permission.ACCESS_FINE_LOCATION) {
          PackageManager.PERMISSION_DENIED
        } else {
          PackageManager.PERMISSION_GRANTED
        }
      }
    )

    assertEquals(WifiPermissionState.DENIED_CAN_REQUEST, checker.currentState())
  }
}
