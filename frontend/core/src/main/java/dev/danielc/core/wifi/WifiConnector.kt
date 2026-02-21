package dev.danielc.core.wifi

import kotlinx.coroutines.flow.Flow

sealed interface WifiConnectState {
  data object Connecting : WifiConnectState
  data class Connected(val ssid: String) : WifiConnectState
  data class Failed(val message: String) : WifiConnectState
}

interface WifiConnector {
  fun connect(ssid: String): Flow<WifiConnectState>
  fun disconnectActiveRequest()
}
