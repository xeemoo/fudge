package dev.danielc.feature.connect.permission

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.SettingsSuggest
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.danielc.R
import dev.danielc.core.wifi.model.WifiHotspot
import dev.danielc.core.wifi.model.WifiPermissionState
import dev.danielc.ui.asString

const val WIFI_PERMISSION_CARD_TAG = "wifi_permission_card"
const val WIFI_SCAN_ERROR_TAG = "wifi_scan_error"
const val WIFI_SCAN_EMPTY_TAG = "wifi_scan_empty"
const val WIFI_SCAN_LIST_TAG = "wifi_scan_list"
const val WIFI_CONNECTING_TAG = "wifi_connecting"
const val WIFI_CONNECT_ERROR_TAG = "wifi_connect_error"
const val WIFI_HISTORY_LIST_TAG = "wifi_history_list"
const val WIFI_ACTIVE_SESSION_TAG = "wifi_active_session"

@Composable
fun WifiPermissionContent(
  modifier: Modifier = Modifier,
  permissionState: WifiPermissionState,
  scanUiState: WifiScanUiState,
  connectUiState: WifiConnectUiState,
  activeSessionSsid: String?,
  pendingConnectSsid: String?,
  showSwitchConfirmDialog: Boolean,
  historyHotspots: List<HotspotHistoryUi>,
  onRequestPermissionClick: () -> Unit,
  onOpenSettingsClick: () -> Unit,
  onRetryScanClick: () -> Unit,
  onConnectClick: (ssid: String) -> Unit,
  onRetryConnectClick: (ssid: String) -> Unit,
  onHistoryConnectClick: (ssid: String) -> Unit,
  onOpenActiveSessionClick: () -> Unit,
  onDisconnectActiveSessionClick: () -> Unit,
  onConfirmSwitchConnect: () -> Unit,
  onDismissSwitchConnect: () -> Unit
) {
  if (showSwitchConfirmDialog && activeSessionSsid != null && pendingConnectSsid != null) {
    AlertDialog(
      onDismissRequest = onDismissSwitchConnect,
      title = {
        Text(text = stringResource(id = R.string.connect_switch_dialog_title, activeSessionSsid))
      },
      text = {
        Text(
          text = stringResource(
            id = R.string.connect_switch_dialog_message,
            pendingConnectSsid
          )
        )
      },
      confirmButton = {
        TextButton(onClick = onConfirmSwitchConnect) {
          Text(text = stringResource(id = R.string.connect_switch_dialog_confirm))
        }
      },
      dismissButton = {
        TextButton(onClick = onDismissSwitchConnect) {
          Text(text = stringResource(id = R.string.connect_switch_dialog_cancel))
        }
      }
    )
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    HeroCard()

    when (permissionState) {
      WifiPermissionState.GRANTED -> {
        ScanResultSection(
          scanUiState = scanUiState,
          connectUiState = connectUiState,
          activeSessionSsid = activeSessionSsid,
          historyHotspots = historyHotspots,
          onRetryScanClick = onRetryScanClick,
          onConnectClick = onConnectClick,
          onRetryConnectClick = onRetryConnectClick,
          onHistoryConnectClick = onHistoryConnectClick,
          onOpenActiveSessionClick = onOpenActiveSessionClick,
          onDisconnectActiveSessionClick = onDisconnectActiveSessionClick
        )
      }

      WifiPermissionState.DENIED_CAN_REQUEST -> {
        PermissionCard(
          title = stringResource(id = R.string.connect_permission_required_title),
          description = stringResource(id = R.string.connect_permission_required_desc),
          ctaLabel = stringResource(id = R.string.connect_permission_required_cta),
          onCtaClick = onRequestPermissionClick
        )
      }

      WifiPermissionState.DENIED_PERMANENT -> {
        PermissionCard(
          title = stringResource(id = R.string.connect_permission_denied_title),
          description = stringResource(id = R.string.connect_permission_denied_desc),
          ctaLabel = stringResource(id = R.string.connect_permission_denied_cta),
          onCtaClick = onOpenSettingsClick
        )
      }
    }
  }
}

@Composable
private fun HeroCard() {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.primaryContainer,
      contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
      ) {
        Icon(
          imageVector = Icons.Outlined.Router,
          contentDescription = null,
          modifier = Modifier.padding(10.dp)
        )
      }
      Text(
        text = stringResource(id = R.string.connect_subtitle),
        style = MaterialTheme.typography.bodyLarge
      )
    }
  }
}

@Composable
private fun ScanResultSection(
  scanUiState: WifiScanUiState,
  connectUiState: WifiConnectUiState,
  activeSessionSsid: String?,
  historyHotspots: List<HotspotHistoryUi>,
  onRetryScanClick: () -> Unit,
  onConnectClick: (ssid: String) -> Unit,
  onRetryConnectClick: (ssid: String) -> Unit,
  onHistoryConnectClick: (ssid: String) -> Unit,
  onOpenActiveSessionClick: () -> Unit,
  onDisconnectActiveSessionClick: () -> Unit
) {
  if (activeSessionSsid != null && connectUiState !is WifiConnectUiState.Connecting) {
    Card(
      modifier = Modifier
        .fillMaxWidth()
        .testTag(WIFI_ACTIVE_SESSION_TAG),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
      )
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = stringResource(id = R.string.connect_active_session_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = stringResource(id = R.string.connect_active_session_connected, activeSessionSsid),
          style = MaterialTheme.typography.bodyMedium
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          FilledTonalButton(
            onClick = onOpenActiveSessionClick,
            modifier = Modifier.weight(1f)
          ) {
            Text(text = stringResource(id = R.string.connect_active_session_open_list))
          }
          OutlinedButton(
            onClick = onDisconnectActiveSessionClick,
            modifier = Modifier.weight(1f)
          ) {
            Text(text = stringResource(id = R.string.connect_active_session_disconnect))
          }
        }
      }
    }
  }

  SectionTitle(text = stringResource(id = R.string.connect_available_hotspots))

  when (connectUiState) {
    is WifiConnectUiState.Connecting -> {
      StatusBanner(
        tag = WIFI_CONNECTING_TAG,
        text = stringResource(id = R.string.connect_connecting, connectUiState.ssid),
        isError = false
      )
    }

    is WifiConnectUiState.Failed -> {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag(WIFI_CONNECT_ERROR_TAG),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = connectUiState.message.asString(),
            style = MaterialTheme.typography.bodyMedium
          )
          Button(onClick = { onRetryConnectClick(connectUiState.ssid) }) {
            Text(text = stringResource(id = R.string.connect_retry_connect))
          }
        }
      }
    }

    WifiConnectUiState.Idle -> Unit
  }

  when (scanUiState) {
    WifiScanUiState.Idle,
    WifiScanUiState.Scanning -> {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = stringResource(id = R.string.connect_scanning),
            style = MaterialTheme.typography.bodyMedium
          )
          LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
      }
    }

    WifiScanUiState.Empty -> {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag(WIFI_SCAN_EMPTY_TAG),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = stringResource(id = R.string.connect_scan_empty),
            style = MaterialTheme.typography.bodyMedium
          )
          Button(onClick = onRetryScanClick) {
            Text(text = stringResource(id = R.string.connect_retry_scan))
          }
        }
      }
    }

    is WifiScanUiState.Success -> {
      Text(
        text = stringResource(id = R.string.connect_scan_found_count, scanUiState.hotspots.size),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      HotspotList(
        hotspots = scanUiState.hotspots,
        connecting = connectUiState is WifiConnectUiState.Connecting,
        onConnectClick = onConnectClick
      )
    }

    is WifiScanUiState.Error -> {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag(WIFI_SCAN_ERROR_TAG),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
      ) {
        Column(
          modifier = Modifier.padding(16.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = scanUiState.message.asString(),
            style = MaterialTheme.typography.bodyMedium
          )
          Button(onClick = onRetryScanClick) {
            Text(text = stringResource(id = R.string.connect_retry_scan))
          }
        }
      }
    }
  }

  if (historyHotspots.isNotEmpty()) {
    SectionTitle(text = stringResource(id = R.string.connect_history_title), icon = {
      Icon(imageVector = Icons.Outlined.History, contentDescription = null)
    })
    HistoryHotspotList(
      historyHotspots = historyHotspots,
      connecting = connectUiState is WifiConnectUiState.Connecting,
      onHistoryConnectClick = onHistoryConnectClick
    )
  }
}

@Composable
private fun StatusBanner(
  tag: String,
  text: String,
  isError: Boolean
) {
  val (containerColor, contentColor) = if (isError) {
    MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
  } else {
    MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
  }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(tag),
    colors = CardDefaults.cardColors(
      containerColor = containerColor,
      contentColor = contentColor
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = if (isError) Icons.Outlined.WifiOff else Icons.Outlined.Wifi,
        contentDescription = null
      )
      Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun HotspotList(
  hotspots: List<WifiHotspot>,
  connecting: Boolean,
  onConnectClick: (ssid: String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(WIFI_SCAN_LIST_TAG),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    hotspots.forEach { hotspot ->
      val displayTitle = if (hotspot.isFujifilm) {
        stringResource(id = R.string.connect_hotspot_recommended, hotspot.ssid)
      } else {
        hotspot.ssid
      }
      val displaySignal = hotspot.rssi?.let { rssi ->
        stringResource(id = R.string.connect_hotspot_signal, rssi)
      } ?: stringResource(id = R.string.connect_hotspot_signal_unknown)
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            enabled = !connecting,
            onClick = { onConnectClick(hotspot.ssid) }
          ),
        border = BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.42f)
        ),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
          ) {
            Icon(
              imageVector = Icons.Outlined.Wifi,
              contentDescription = null,
              modifier = Modifier.padding(8.dp)
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = displayTitle,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = displaySignal,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun HistoryHotspotList(
  historyHotspots: List<HotspotHistoryUi>,
  connecting: Boolean,
  onHistoryConnectClick: (ssid: String) -> Unit
) {
  Column(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(WIFI_HISTORY_LIST_TAG),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    historyHotspots.forEach { hotspot ->
      val subtitle = stringResource(id = R.string.connect_history_connect_count, hotspot.connectCount)
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clickable(
            enabled = !connecting,
            onClick = { onHistoryConnectClick(hotspot.ssid) }
          ),
        border = BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
        ),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer
          ) {
            Icon(
              imageVector = Icons.Outlined.History,
              contentDescription = null,
              modifier = Modifier.padding(8.dp)
            )
          }
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = hotspot.ssid,
              style = MaterialTheme.typography.bodyLarge,
              fontWeight = FontWeight.SemiBold
            )
            Text(
              text = subtitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionCard(
  title: String,
  description: String,
  ctaLabel: String,
  onCtaClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag(WIFI_PERMISSION_CARD_TAG),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)
    )
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = CircleShape,
          color = MaterialTheme.colorScheme.primaryContainer
        ) {
          Icon(
            imageVector = Icons.Outlined.SettingsSuggest,
            contentDescription = null,
            modifier = Modifier.padding(8.dp)
          )
        }
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium
        )
      }
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Button(onClick = onCtaClick) {
        Text(text = ctaLabel)
      }
    }
  }
}

@Composable
private fun SectionTitle(
  text: String,
  icon: (@Composable () -> Unit)? = null
) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    if (icon != null) {
      Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        icon()
      }
    }
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.SemiBold
    )
  }
}
