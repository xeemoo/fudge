package dev.danielc.core.wifi.model

sealed interface ScanResult {
  data class Success(val hotspots: List<WifiHotspot>) : ScanResult
  data class Error(val message: String) : ScanResult
}
