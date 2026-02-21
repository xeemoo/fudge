package dev.danielc.core.wifi.fake

import dev.danielc.core.wifi.model.WifiHotspot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeWifiRuntimeState(
  initialHotspots: List<WifiHotspot> = defaultHotspots()
) {
  private val hotspotsState = MutableStateFlow(initialHotspots)
  private val connectedSsidState = MutableStateFlow<String?>(null)

  val hotspots: StateFlow<List<WifiHotspot>> = hotspotsState.asStateFlow()
  val connectedSsid: StateFlow<String?> = connectedSsidState.asStateFlow()

  fun currentHotspots(): List<WifiHotspot> = hotspotsState.value

  fun setConnectedSsid(ssid: String?) {
    connectedSsidState.value = ssid?.takeIf { it.isNotBlank() }
  }

  companion object {
    fun defaultHotspots(): List<WifiHotspot> {
      return listOf(
        WifiHotspot(ssid = "FUJIFILM-XT5", rssi = -34),
        WifiHotspot(ssid = "FUJIFILM-XH2", rssi = -52),
        WifiHotspot(ssid = "Studio-Network", rssi = -70)
      )
    }
  }
}
