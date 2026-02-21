package dev.danielc.core.wifi.fake

import dev.danielc.core.wifi.WifiPermissionChecker
import dev.danielc.core.wifi.WifiScanner
import dev.danielc.core.wifi.model.ScanResult
import dev.danielc.core.wifi.model.WifiPermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FakeWifiScanner(
  private val permissionChecker: WifiPermissionChecker,
  private val runtimeState: FakeWifiRuntimeState
) : WifiScanner {

  override fun scanOnce(): suspend () -> ScanResult = {
    if (permissionChecker.currentState() != WifiPermissionState.GRANTED) {
      ScanResult.Error("Missing Wi-Fi permission. Grant permission and try again.")
    } else {
      ScanResult.Success(runtimeState.currentHotspots())
    }
  }

  override fun observeScan(intervalMs: Long): Flow<ScanResult> {
    return runtimeState.hotspots.map { hotspots ->
      if (permissionChecker.currentState() != WifiPermissionState.GRANTED) {
        ScanResult.Error("Missing Wi-Fi permission. Grant permission and try again.")
      } else {
        ScanResult.Success(hotspots)
      }
    }
  }
}
