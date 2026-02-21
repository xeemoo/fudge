package dev.danielc.core.wifi.model

data class WifiHotspot(
  val ssid: String,
  val rssi: Int?
) {
  val isFujifilm: Boolean
    get() = ssid.contains("FUJIFILM", ignoreCase = true)
}
