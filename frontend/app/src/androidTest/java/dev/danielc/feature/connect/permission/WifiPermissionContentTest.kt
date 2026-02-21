package dev.danielc.feature.connect.permission

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import dev.danielc.R
import dev.danielc.core.wifi.model.WifiHotspot
import dev.danielc.core.wifi.model.WifiPermissionState
import dev.danielc.ui.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WifiPermissionContentTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun deniedCanRequest_showsPermissionCardWithAuthorizeCta() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.DENIED_CAN_REQUEST,
        scanUiState = WifiScanUiState.Idle,
        connectUiState = WifiConnectUiState.Idle,
        activeSessionSsid = null,
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        historyHotspots = emptyList(),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertTrue(
      composeRule.onAllNodesWithTag(WIFI_PERMISSION_CARD_TAG).fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_permission_required_cta))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun granted_hidesPermissionCard() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.GRANTED,
        scanUiState = WifiScanUiState.Empty,
        connectUiState = WifiConnectUiState.Idle,
        activeSessionSsid = null,
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        historyHotspots = emptyList(),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertEquals(
      0,
      composeRule.onAllNodesWithTag(WIFI_PERMISSION_CARD_TAG).fetchSemanticsNodes().size
    )
    assertEquals(
      1,
      composeRule.onAllNodesWithTag(WIFI_SCAN_EMPTY_TAG).fetchSemanticsNodes().size
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_retry_scan))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun granted_success_showsFujifilmRecommendationLabel() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.GRANTED,
        scanUiState = WifiScanUiState.Success(
          hotspots = listOf(
            WifiHotspot(ssid = "FUJIFILM-XT5", rssi = -30),
            WifiHotspot(ssid = "MyHome", rssi = -50)
          )
        ),
        connectUiState = WifiConnectUiState.Idle,
        activeSessionSsid = null,
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        historyHotspots = emptyList(),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertTrue(
      composeRule.onAllNodesWithTag(WIFI_SCAN_LIST_TAG).fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_hotspot_recommended, "FUJIFILM-XT5"))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun connecting_showsConnectingBanner() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.GRANTED,
        scanUiState = WifiScanUiState.Success(
          hotspots = listOf(WifiHotspot(ssid = "FUJIFILM-XT5", rssi = -30))
        ),
        connectUiState = WifiConnectUiState.Connecting(ssid = "FUJIFILM-XT5"),
        activeSessionSsid = null,
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        historyHotspots = emptyList(),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertTrue(
      composeRule.onAllNodesWithTag(WIFI_CONNECTING_TAG).fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_connecting, "FUJIFILM-XT5"))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun connectFailed_showsRetryConnectButton() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.GRANTED,
        scanUiState = WifiScanUiState.Success(
          hotspots = listOf(WifiHotspot(ssid = "FUJIFILM-XT5", rssi = -30))
        ),
        connectUiState = WifiConnectUiState.Failed(
          ssid = "FUJIFILM-XT5",
          message = UiText.Res(R.string.connect_error_unavailable)
        ),
        activeSessionSsid = null,
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        historyHotspots = emptyList(),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertTrue(
      composeRule.onAllNodesWithTag(WIFI_CONNECT_ERROR_TAG).fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_retry_connect))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun activeSession_showsResumeEntry() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.GRANTED,
        scanUiState = WifiScanUiState.Empty,
        connectUiState = WifiConnectUiState.Idle,
        activeSessionSsid = "FUJIFILM-XT5",
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        historyHotspots = emptyList(),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertTrue(
      composeRule.onAllNodesWithTag(WIFI_ACTIVE_SESSION_TAG).fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_active_session_connected, "FUJIFILM-XT5"))
        .fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_active_session_open_list))
        .fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_active_session_disconnect))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun switchConfirmDialog_showsWhenRequested() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.GRANTED,
        scanUiState = WifiScanUiState.Empty,
        connectUiState = WifiConnectUiState.Idle,
        activeSessionSsid = "FUJIFILM-XT5",
        pendingConnectSsid = "FUJIFILM-XH2",
        showSwitchConfirmDialog = true,
        historyHotspots = emptyList(),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_switch_dialog_title, "FUJIFILM-XT5"))
        .fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_switch_dialog_message, "FUJIFILM-XH2"))
        .fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_switch_dialog_confirm))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  @Test
  fun history_showsHistoryListAndItems() {
    composeRule.setContent {
      WifiPermissionContent(
        permissionState = WifiPermissionState.GRANTED,
        scanUiState = WifiScanUiState.Empty,
        connectUiState = WifiConnectUiState.Idle,
        activeSessionSsid = null,
        pendingConnectSsid = null,
        showSwitchConfirmDialog = false,
        historyHotspots = listOf(
          HotspotHistoryUi(
            ssid = "FUJIFILM-HISTORY",
            connectCount = 3,
            lastConnectedAtEpochMillis = 123L
          )
        ),
        onRequestPermissionClick = {},
        onOpenSettingsClick = {},
        onRetryScanClick = {},
        onConnectClick = {},
        onRetryConnectClick = {},
        onHistoryConnectClick = {},
        onOpenActiveSessionClick = {},
        onDisconnectActiveSessionClick = {},
        onConfirmSwitchConnect = {},
        onDismissSwitchConnect = {}
      )
    }

    assertTrue(
      composeRule.onAllNodesWithTag(WIFI_HISTORY_LIST_TAG).fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText("FUJIFILM-HISTORY").fetchSemanticsNodes().isNotEmpty()
    )
    assertTrue(
      composeRule.onAllNodesWithText(string(R.string.connect_history_connect_count, 3))
        .fetchSemanticsNodes().isNotEmpty()
    )
  }

  private fun string(@StringRes id: Int, vararg args: Any): String {
    return composeRule.activity.getString(id, *args)
  }
}
