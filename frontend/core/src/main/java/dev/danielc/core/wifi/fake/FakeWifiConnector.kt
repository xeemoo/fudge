package dev.danielc.core.wifi.fake

import dev.danielc.core.wifi.WifiConnectState
import dev.danielc.core.wifi.WifiConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeWifiConnector(
  private val runtimeState: FakeWifiRuntimeState
) : WifiConnector {

  override fun connect(ssid: String): Flow<WifiConnectState> = flow {
    emit(WifiConnectState.Connecting)
    val normalizedSsid = ssid.trim()
    if (normalizedSsid.isEmpty()) {
      emit(WifiConnectState.Failed("Connection failed. Verify hotspot availability and retry."))
      return@flow
    }
    runtimeState.setConnectedSsid(normalizedSsid)
    emit(WifiConnectState.Connected(normalizedSsid))
  }

  override fun disconnectActiveRequest() {
    runtimeState.setConnectedSsid(null)
  }
}
