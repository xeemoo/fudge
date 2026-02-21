package dev.danielc.feature.connect

import dev.danielc.core.wifi.model.ScanResult
import dev.danielc.core.wifi.model.WifiPermissionState
import dev.danielc.feature.connect.permission.HotspotHistoryUi
import dev.danielc.ui.UiText

object ConnectContract {

  data class State(
    val permission: WifiPermissionState = WifiPermissionState.DENIED_CAN_REQUEST,
    val scanResult: ScanResult? = null,
    val isScanning: Boolean = false,
    val history: List<HotspotHistoryUi> = emptyList(),
    val activeSessionSsid: String? = null,
    val pendingConnectSsid: String? = null,
    val showSwitchConfirmDialog: Boolean = false,
    val connectingSsid: String? = null,
    val failedSsid: String? = null,
    val errorMessage: UiText? = null
  )

  sealed interface Intent {
    data class OnEnter(val permission: WifiPermissionState) : Intent
    data class OnPermissionChanged(val permission: WifiPermissionState) : Intent
    object OnClickGrantPermission : Intent
    object OnRetryScan : Intent
    data class OnClickConnect(val ssid: String, val source: ConnectSource) : Intent
    object OnClickOpenActiveSession : Intent
    object OnClickDisconnectActiveSession : Intent
    object OnConfirmSwitchConnect : Intent
    object OnDismissSwitchConnect : Intent
  }

  sealed interface Effect {
    data class NavigateToPhotoList(val ssid: String) : Effect
    data class Toast(val message: UiText) : Effect
    data object OpenAppSettings : Effect
  }

  enum class ConnectSource {
    AVAILABLE,
    HISTORY
  }
}
