package dev.danielc.core.wifi.fake

import dev.danielc.core.wifi.WifiPermissionChecker
import dev.danielc.core.wifi.model.WifiPermissionState

class FakeWifiPermissionChecker : WifiPermissionChecker {
  override fun currentState(): WifiPermissionState = WifiPermissionState.GRANTED

  override fun requiredPermissions(): Array<String> = emptyArray()
}
