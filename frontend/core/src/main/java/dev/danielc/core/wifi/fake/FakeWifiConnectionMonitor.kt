package dev.danielc.core.wifi.fake

import dev.danielc.core.wifi.WifiConnectionMonitor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class FakeWifiConnectionMonitor(
  runtimeState: FakeWifiRuntimeState
) : WifiConnectionMonitor {
  override val currentSsid: Flow<String?> = runtimeState.connectedSsid

  override val isWifiConnected: Flow<Boolean> = runtimeState.connectedSsid
    .map { ssid -> !ssid.isNullOrBlank() }
    .distinctUntilChanged()
}
