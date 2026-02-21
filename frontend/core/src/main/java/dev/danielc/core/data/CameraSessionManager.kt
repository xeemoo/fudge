package dev.danielc.core.data

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.wifi.WifiConnectionMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

sealed interface SessionState {
  data object Ready : SessionState
  data class NotReady(
    val code: SessionNotReadyCode,
    val reason: String = code.defaultReason()
  ) : SessionState
}

enum class SessionNotReadyCode {
  CHECKING,
  WIFI_DISCONNECTED,
  SDK_UNREACHABLE,
  SDK_NATIVE_LIBRARY_MISSING
}

interface CameraSessionManager {
  val sessionState: Flow<SessionState>
  suspend fun assertReady(): SessionState
}

class CameraSessionManagerImpl(
  private val wifiConnectionMonitor: WifiConnectionMonitor,
  private val cameraClient: FujifilmCameraClient,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : CameraSessionManager {

  override val sessionState: Flow<SessionState> = wifiConnectionMonitor.isWifiConnected
    .distinctUntilChanged()
    .map { isConnected ->
      if (!isConnected) {
        buildNotReady(SessionNotReadyCode.WIFI_DISCONNECTED)
      } else {
        checkSdkReachable()
      }
    }

  override suspend fun assertReady(): SessionState {
    val isConnected = wifiConnectionMonitor.isWifiConnected.first()
    if (!isConnected) {
      return buildNotReady(SessionNotReadyCode.WIFI_DISCONNECTED)
    }
    return checkSdkReachable()
  }

  private suspend fun checkSdkReachable(): SessionState = withContext(ioDispatcher) {
    val reachableResult = runCatching { cameraClient.isReachable() }
    val throwable = reachableResult.exceptionOrNull()
    if (throwable != null && throwable.isMissingNativeLibrary()) {
      return@withContext buildNotReady(SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING)
    }

    val reachable = reachableResult.getOrDefault(false)
    if (reachable) {
      SessionState.Ready
    } else {
      buildNotReady(SessionNotReadyCode.SDK_UNREACHABLE)
    }
  }

  private fun buildNotReady(code: SessionNotReadyCode): SessionState.NotReady {
    return SessionState.NotReady(
      code = code,
      reason = code.defaultReason()
    )
  }

  companion object {
    const val REASON_CHECKING = "Checking connection state..."
    const val REASON_WIFI_DISCONNECTED = "Connection disconnected. Reconnect the camera."
    const val REASON_SDK_UNREACHABLE = "Unable to reach camera. Verify camera WLAN state."
    const val REASON_SDK_NATIVE_LIBRARY_MISSING = "Camera SDK native library missing (libfudge.so not packaged)."
  }
}

private fun SessionNotReadyCode.defaultReason(): String {
  return when (this) {
    SessionNotReadyCode.CHECKING -> CameraSessionManagerImpl.REASON_CHECKING
    SessionNotReadyCode.WIFI_DISCONNECTED -> CameraSessionManagerImpl.REASON_WIFI_DISCONNECTED
    SessionNotReadyCode.SDK_UNREACHABLE -> CameraSessionManagerImpl.REASON_SDK_UNREACHABLE
    SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING -> CameraSessionManagerImpl.REASON_SDK_NATIVE_LIBRARY_MISSING
  }
}

private fun Throwable.isMissingNativeLibrary(): Boolean {
  if (this is UnsatisfiedLinkError) {
    return true
  }
  val text = buildString {
    append(message.orEmpty())
    append(' ')
    append(cause?.message.orEmpty())
  }
  if ("libfudge.so" in text || "Legacy native library 'fudge' is not available" in text) {
    return true
  }
  return cause?.isMissingNativeLibrary() == true
}
