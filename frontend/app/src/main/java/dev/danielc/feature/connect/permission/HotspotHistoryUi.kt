package dev.danielc.feature.connect.permission

data class HotspotHistoryUi(
  val ssid: String,
  val connectCount: Int,
  val lastConnectedAtEpochMillis: Long
)
