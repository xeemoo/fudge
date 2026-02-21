package dev.danielc.feature.connect.permission

import dev.danielc.core.wifi.model.WifiHotspot
import dev.danielc.ui.UiText

sealed interface WifiScanUiState {
  data object Idle : WifiScanUiState
  data object Scanning : WifiScanUiState
  data object Empty : WifiScanUiState
  data class Success(val hotspots: List<WifiHotspot>) : WifiScanUiState
  data class Error(val message: UiText) : WifiScanUiState
}
