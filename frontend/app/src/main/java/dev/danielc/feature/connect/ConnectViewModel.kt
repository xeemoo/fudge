package dev.danielc.feature.connect

import androidx.lifecycle.viewModelScope
import dev.danielc.R
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.analytics.NoOpAnalyticsTracker
import dev.danielc.core.analytics.WifiConnectFailReason
import dev.danielc.core.analytics.WifiConnectSource
import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.HotspotHistoryRepository
import dev.danielc.core.data.SessionNotReadyCode
import dev.danielc.core.data.SessionState
import dev.danielc.core.mvi.MviViewModel
import dev.danielc.core.wifi.WifiConnectState
import dev.danielc.core.wifi.WifiConnector
import dev.danielc.core.wifi.WifiConnectorImpl
import dev.danielc.core.wifi.WifiConnectionMonitor
import dev.danielc.core.wifi.WifiScanner
import dev.danielc.core.wifi.WifiScannerImpl
import dev.danielc.core.wifi.model.ScanResult
import dev.danielc.core.wifi.model.WifiPermissionState
import dev.danielc.feature.connect.permission.HotspotHistoryUi
import dev.danielc.ui.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class ConnectViewModel(
  private val wifiScanner: WifiScanner,
  private val wifiConnector: WifiConnector,
  private val wifiConnectionMonitor: WifiConnectionMonitor,
  private val cameraSessionManager: CameraSessionManager,
  private val hotspotHistoryRepository: HotspotHistoryRepository,
  private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker,
  private val nowMillisProvider: () -> Long = { System.currentTimeMillis() }
) : MviViewModel<ConnectContract.Intent, ConnectContract.State, ConnectContract.Effect>(
  initialState = ConnectContract.State()
) {

  private var connectJob: Job? = null

  init {
    viewModelScope.launch {
      hotspotHistoryRepository.observeHistory().collect { history ->
        setState {
          copy(
            history = history.map { entity ->
              HotspotHistoryUi(
                ssid = entity.ssid,
                connectCount = entity.connectCount,
                lastConnectedAtEpochMillis = entity.lastConnectedAtEpochMillis
              )
            }
          )
        }
      }
    }
    viewModelScope.launch {
      combine(
        cameraSessionManager.sessionState,
        wifiConnectionMonitor.currentSsid
      ) { sessionState, currentSsid ->
        if (sessionState is SessionState.Ready) {
          currentSsid
        } else {
          null
        }
      }.collect { activeSessionSsid ->
        setState {
          copy(activeSessionSsid = activeSessionSsid)
        }
      }
    }
  }

  override suspend fun reduce(intent: ConnectContract.Intent) {
    when (intent) {
      is ConnectContract.Intent.OnEnter -> {
        handlePermissionChanged(intent.permission, triggerScan = true)
      }

      is ConnectContract.Intent.OnPermissionChanged -> {
        handlePermissionChanged(intent.permission, triggerScan = intent.permission == WifiPermissionState.GRANTED)
      }

      ConnectContract.Intent.OnClickGrantPermission -> {
        if (state.value.permission == WifiPermissionState.DENIED_PERMANENT) {
          postEffect { ConnectContract.Effect.OpenAppSettings }
        }
      }

      ConnectContract.Intent.OnRetryScan -> {
        if (state.value.permission != WifiPermissionState.GRANTED) {
          postEffect { ConnectContract.Effect.Toast(UiText.Res(R.string.connect_toast_scan_permission_required)) }
          return
        }
        runScan()
      }

      is ConnectContract.Intent.OnClickConnect -> {
        if (state.value.permission != WifiPermissionState.GRANTED) {
          postEffect { ConnectContract.Effect.Toast(UiText.Res(R.string.connect_toast_connect_permission_required)) }
          return
        }
        analyticsTracker.track(AnalyticsEvent.WifiConnectClick(intent.source.toAnalyticsSource()))
        val activeSessionSsid = state.value.activeSessionSsid
        if (activeSessionSsid != null) {
          setState {
            copy(
              pendingConnectSsid = intent.ssid,
              showSwitchConfirmDialog = true
            )
          }
          return
        }
        runConnect(intent.ssid)
      }

      ConnectContract.Intent.OnClickOpenActiveSession -> {
        val activeSessionSsid = state.value.activeSessionSsid ?: return
        postEffect {
          ConnectContract.Effect.NavigateToPhotoList(activeSessionSsid)
        }
      }

      ConnectContract.Intent.OnClickDisconnectActiveSession -> {
        disconnectCurrentSession()
      }

      ConnectContract.Intent.OnConfirmSwitchConnect -> {
        val pendingSsid = state.value.pendingConnectSsid ?: return
        dismissSwitchDialog()
        disconnectCurrentSession()
        runConnect(pendingSsid)
      }

      ConnectContract.Intent.OnDismissSwitchConnect -> {
        dismissSwitchDialog()
      }
    }
  }

  override fun onCleared() {
    connectJob?.cancel()
    // Keep local-only Wi-Fi request alive after navigation to maintain camera session.
    super.onCleared()
  }

  private suspend fun handlePermissionChanged(
    permission: WifiPermissionState,
    triggerScan: Boolean
  ) {
    setState {
      copy(permission = permission)
    }

    if (permission != WifiPermissionState.GRANTED) {
      connectJob?.cancel()
      wifiConnector.disconnectActiveRequest()
      setState {
        copy(
          activeSessionSsid = null,
          isScanning = false,
          scanResult = null,
          connectingSsid = null,
          pendingConnectSsid = null,
          showSwitchConfirmDialog = false,
          failedSsid = null,
          errorMessage = null
        )
      }
      return
    }

    if (!triggerScan) {
      return
    }
    runScan()
  }

  private suspend fun runScan() {
    analyticsTracker.track(AnalyticsEvent.WifiScanStart)
    setState {
      copy(
        isScanning = true,
        scanResult = null
      )
    }

    val result = wifiScanner.scanOnce().invoke()
    if (result is ScanResult.Success) {
      analyticsTracker.track(AnalyticsEvent.WifiScanResultCount(result.hotspots.size))
    }
    setState {
      copy(
        isScanning = false,
        scanResult = result
      )
    }
  }

  private fun runConnect(ssid: String) {
    connectJob?.cancel()
    connectJob = viewModelScope.launch {
      wifiConnector.connect(ssid).collect { connectState ->
        when (connectState) {
          WifiConnectState.Connecting -> {
            setState {
              copy(
                connectingSsid = ssid,
                pendingConnectSsid = null,
                showSwitchConfirmDialog = false,
                failedSsid = null,
                errorMessage = null
              )
            }
          }

          is WifiConnectState.Connected -> {
            delay(CONNECT_ASSERT_DELAY_MS)
            when (val sessionState = cameraSessionManager.assertReady()) {
              SessionState.Ready -> {
                analyticsTracker.track(AnalyticsEvent.WifiConnectSuccess)
                runCatching {
                  hotspotHistoryRepository.markConnected(
                    ssid = connectState.ssid,
                    atEpochMillis = nowMillisProvider()
                  )
                }.onFailure {
                  postEffect {
                    ConnectContract.Effect.Toast(UiText.Res(R.string.connect_toast_history_save_failed))
                  }
                }
                setState {
                  copy(
                    connectingSsid = null,
                    failedSsid = null,
                    errorMessage = null
                  )
                }
                postEffect {
                  ConnectContract.Effect.NavigateToPhotoList(connectState.ssid)
                }
              }

              is SessionState.NotReady -> {
                analyticsTracker.track(AnalyticsEvent.WifiConnectFail(sessionState.toAnalyticsReason()))
                setState {
                  copy(
                    connectingSsid = null,
                    failedSsid = connectState.ssid,
                    errorMessage = sessionState.toUiText()
                  )
                }
              }
            }
          }

          is WifiConnectState.Failed -> {
            analyticsTracker.track(AnalyticsEvent.WifiConnectFail(connectState.message.toWifiConnectFailReason()))
            setState {
              copy(
                connectingSsid = null,
                failedSsid = ssid,
                errorMessage = connectState.message.toConnectErrorUiText()
              )
            }
          }
        }
      }
    }
  }

  private fun disconnectCurrentSession() {
    connectJob?.cancel()
    wifiConnector.disconnectActiveRequest()
    setState {
      copy(
        activeSessionSsid = null,
        connectingSsid = null,
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        failedSsid = null,
        errorMessage = null
      )
    }
  }

  private fun dismissSwitchDialog() {
    setState {
      copy(
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false
      )
    }
  }
}

private fun ConnectContract.ConnectSource.toAnalyticsSource(): WifiConnectSource {
  return when (this) {
    ConnectContract.ConnectSource.AVAILABLE -> WifiConnectSource.AVAILABLE
    ConnectContract.ConnectSource.HISTORY -> WifiConnectSource.HISTORY
  }
}

private fun SessionState.NotReady.toAnalyticsReason(): WifiConnectFailReason {
  return when (code) {
    SessionNotReadyCode.WIFI_DISCONNECTED -> WifiConnectFailReason.TIMEOUT
    SessionNotReadyCode.CHECKING,
    SessionNotReadyCode.SDK_UNREACHABLE,
    SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING -> WifiConnectFailReason.UNKNOWN
  }
}

private fun String.toWifiConnectFailReason(): WifiConnectFailReason {
  val normalized = lowercase()
  return when {
    "auth" in normalized || "password" in normalized -> WifiConnectFailReason.AUTH
    "unsupported" in normalized || "permission" in normalized || "restricted" in normalized -> WifiConnectFailReason.SYSTEM_RESTRICT
    "timeout" in normalized || this == WifiConnectorImpl.ERROR_UNAVAILABLE -> WifiConnectFailReason.TIMEOUT
    else -> WifiConnectFailReason.UNKNOWN
  }
}

internal fun ConnectContract.State.toScanUiState(): dev.danielc.feature.connect.permission.WifiScanUiState {
  if (isScanning) {
    return dev.danielc.feature.connect.permission.WifiScanUiState.Scanning
  }
  return when (val result = scanResult) {
    null -> dev.danielc.feature.connect.permission.WifiScanUiState.Idle
    is ScanResult.Error -> dev.danielc.feature.connect.permission.WifiScanUiState.Error(result.message.toScanErrorUiText())
    is ScanResult.Success -> {
      if (result.hotspots.isEmpty()) {
        dev.danielc.feature.connect.permission.WifiScanUiState.Empty
      } else {
        dev.danielc.feature.connect.permission.WifiScanUiState.Success(result.hotspots)
      }
    }
  }
}

internal fun ConnectContract.State.toConnectUiState(): dev.danielc.feature.connect.permission.WifiConnectUiState {
  val connectingSsid = connectingSsid
  if (connectingSsid != null) {
    return dev.danielc.feature.connect.permission.WifiConnectUiState.Connecting(connectingSsid)
  }

  val failedSsid = failedSsid
  val errorMessage = errorMessage
  if (failedSsid != null && errorMessage != null) {
    return dev.danielc.feature.connect.permission.WifiConnectUiState.Failed(
      ssid = failedSsid,
      message = errorMessage
    )
  }

  return dev.danielc.feature.connect.permission.WifiConnectUiState.Idle
}

private fun SessionState.NotReady.toUiText(): UiText {
  return when (code) {
    SessionNotReadyCode.CHECKING -> UiText.Res(R.string.connect_error_checking)
    SessionNotReadyCode.WIFI_DISCONNECTED -> UiText.Res(R.string.connect_error_wifi_disconnected)
    SessionNotReadyCode.SDK_UNREACHABLE -> UiText.Res(R.string.connect_error_sdk_unreachable)
    SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING -> UiText.Res(R.string.connect_error_sdk_library_missing)
  }
}

private fun String.toScanErrorUiText(): UiText {
  return when (this) {
    WifiScannerImpl.ERROR_PERMISSION_REQUIRED -> UiText.Res(R.string.connect_error_permission_required)
    WifiScannerImpl.ERROR_WIFI_DISABLED -> UiText.Res(R.string.connect_error_wifi_disabled)
    WifiScannerImpl.ERROR_LOCATION_DISABLED -> UiText.Res(R.string.connect_error_location_disabled)
    WifiScannerImpl.ERROR_SECURITY_EXCEPTION -> UiText.Res(R.string.connect_error_security_exception)
    WifiScannerImpl.ERROR_GENERIC -> UiText.Res(R.string.connect_error_scan_failed)
    else -> UiText.Dynamic(this)
  }
}

private fun String.toConnectErrorUiText(): UiText {
  return when (this) {
    WifiConnectorImpl.ERROR_UNAVAILABLE -> UiText.Res(R.string.connect_error_unavailable)
    WifiConnectorImpl.ERROR_LOST -> UiText.Res(R.string.connect_error_lost)
    WifiConnectorImpl.ERROR_REQUEST_FAILED -> UiText.Res(R.string.connect_error_request_failed)
    WifiConnectorImpl.ERROR_UNSUPPORTED_VERSION -> UiText.Res(R.string.connect_error_unsupported_version)
    else -> UiText.Dynamic(this)
  }
}

private const val CONNECT_ASSERT_DELAY_MS = 1_000L
