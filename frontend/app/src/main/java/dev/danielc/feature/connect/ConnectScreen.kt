package dev.danielc.feature.connect

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Scaffold
import dev.danielc.feature.connect.permission.WifiPermissionContent

@Composable
fun ConnectScreen(
  state: ConnectContract.State,
  onIntent: (ConnectContract.Intent) -> Unit,
  onRequestPermission: () -> Unit
) {
  Scaffold(
    containerColor = Color.Transparent
  ) { innerPadding ->
    WifiPermissionContent(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      permissionState = state.permission,
      scanUiState = state.toScanUiState(),
      connectUiState = state.toConnectUiState(),
      activeSessionSsid = state.activeSessionSsid,
      pendingConnectSsid = state.pendingConnectSsid,
      showSwitchConfirmDialog = state.showSwitchConfirmDialog,
      historyHotspots = state.history,
      onRequestPermissionClick = {
        onIntent(ConnectContract.Intent.OnClickGrantPermission)
        onRequestPermission()
      },
      onOpenSettingsClick = {
        onIntent(ConnectContract.Intent.OnClickGrantPermission)
      },
      onRetryScanClick = {
        onIntent(ConnectContract.Intent.OnRetryScan)
      },
      onConnectClick = { ssid ->
        onIntent(
          ConnectContract.Intent.OnClickConnect(
            ssid = ssid,
            source = ConnectContract.ConnectSource.AVAILABLE
          )
        )
      },
      onRetryConnectClick = { ssid ->
        onIntent(
          ConnectContract.Intent.OnClickConnect(
            ssid = ssid,
            source = ConnectContract.ConnectSource.AVAILABLE
          )
        )
      },
      onHistoryConnectClick = { ssid ->
        onIntent(
          ConnectContract.Intent.OnClickConnect(
            ssid = ssid,
            source = ConnectContract.ConnectSource.HISTORY
          )
        )
      },
      onOpenActiveSessionClick = {
        onIntent(ConnectContract.Intent.OnClickOpenActiveSession)
      },
      onDisconnectActiveSessionClick = {
        onIntent(ConnectContract.Intent.OnClickDisconnectActiveSession)
      },
      onConfirmSwitchConnect = {
        onIntent(ConnectContract.Intent.OnConfirmSwitchConnect)
      },
      onDismissSwitchConnect = {
        onIntent(ConnectContract.Intent.OnDismissSwitchConnect)
      }
    )
  }
}
