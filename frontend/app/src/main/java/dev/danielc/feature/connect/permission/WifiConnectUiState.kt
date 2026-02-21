package dev.danielc.feature.connect.permission

import dev.danielc.ui.UiText

sealed interface WifiConnectUiState {
  data object Idle : WifiConnectUiState
  data class Connecting(val ssid: String) : WifiConnectUiState
  data class Failed(val ssid: String, val message: UiText) : WifiConnectUiState
}
