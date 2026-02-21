package dev.danielc.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.danielc.R
import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.SessionState
import dev.danielc.core.data.SessionNotReadyCode
import kotlinx.coroutines.flow.collectLatest

@Composable
fun rememberCameraSessionState(
  sessionManager: CameraSessionManager
): State<SessionState> {
  return produceState<SessionState>(
    initialValue = SessionState.NotReady(code = SessionNotReadyCode.CHECKING),
    key1 = sessionManager
  ) {
    value = sessionManager.assertReady()
    sessionManager.sessionState.collectLatest { state ->
      value = state
    }
  }
}

@Composable
fun SessionGate(
  sessionState: SessionState,
  onNavigateToConnect: () -> Unit,
  modifier: Modifier = Modifier
) {
  val notReady = sessionState as? SessionState.NotReady ?: return

  ElevatedCard(
    modifier = modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    colors = CardDefaults.elevatedCardColors(
      containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
      contentColor = MaterialTheme.colorScheme.onErrorContainer
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Icon(
        imageVector = Icons.Outlined.WifiOff,
        contentDescription = null
      )
      Text(
        text = stringResource(id = sessionMessageResIdFor(notReady.code)),
        modifier = Modifier.weight(1f)
      )
      TextButton(
        onClick = {
          onNavigateToConnect()
        }
      ) {
        Text(text = stringResource(id = R.string.session_gate_go_connect))
      }
    }
  }
}

@StringRes
internal fun sessionMessageResIdFor(code: SessionNotReadyCode): Int {
  return when (code) {
    SessionNotReadyCode.CHECKING -> R.string.connect_error_checking
    SessionNotReadyCode.WIFI_DISCONNECTED -> R.string.connect_error_wifi_disconnected
    SessionNotReadyCode.SDK_UNREACHABLE -> R.string.connect_error_sdk_unreachable
    SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING -> R.string.connect_error_sdk_library_missing
  }
}
