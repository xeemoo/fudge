package dev.danielc.core.data

import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.wifi.WifiConnectionMonitor
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraSessionManagerImplTest {

  private val dispatcher = StandardTestDispatcher()

  @Test
  fun sessionState_whenWifiDisconnected_returnsNotReadyWithoutSdkPing() = runTest(dispatcher) {
    val wifiMonitor = FakeWifiConnectionMonitor(isConnected = false)
    val cameraClient = FakeCameraClient()
    val sessionManager = CameraSessionManagerImpl(wifiMonitor, cameraClient, dispatcher)

    val state = sessionManager.sessionState.first()

    assertEquals(
      SessionState.NotReady(code = SessionNotReadyCode.WIFI_DISCONNECTED),
      state
    )
    assertEquals(0, cameraClient.reachableCheckCount)
  }

  @Test
  fun sessionState_updatesFromDisconnectedToReadyWhenWifiRecovers() = runTest(dispatcher) {
    val wifiMonitor = FakeWifiConnectionMonitor(isConnected = false)
    val cameraClient = FakeCameraClient(reachable = true)
    val sessionManager = CameraSessionManagerImpl(wifiMonitor, cameraClient, dispatcher)
    val emittedStates = mutableListOf<SessionState>()

    val collectJob = launch {
      sessionManager.sessionState.take(2).toList(emittedStates)
    }
    advanceUntilIdle()

    wifiMonitor.connectedFlow.value = true
    advanceUntilIdle()
    collectJob.join()

    assertEquals(
      listOf(
        SessionState.NotReady(code = SessionNotReadyCode.WIFI_DISCONNECTED),
        SessionState.Ready
      ),
      emittedStates
    )
    assertEquals(1, cameraClient.reachableCheckCount)
  }

  @Test
  fun sessionState_duplicateConnectedEvents_doNotRepeatSdkReachabilityCheck() = runTest(dispatcher) {
    val wifiMonitor = FakeWifiConnectionMonitor(isConnected = true)
    val cameraClient = FakeCameraClient(reachable = true)
    val sessionManager = CameraSessionManagerImpl(wifiMonitor, cameraClient, dispatcher)

    val firstState = sessionManager.sessionState.first()
    assertEquals(SessionState.Ready, firstState)
    assertEquals(1, cameraClient.reachableCheckCount)

    wifiMonitor.connectedFlow.value = true
    advanceUntilIdle()

    assertEquals(1, cameraClient.reachableCheckCount)
  }

  @Test
  fun assertReady_whenSdkUnreachable_returnsNotReady() = runTest(dispatcher) {
    val wifiMonitor = FakeWifiConnectionMonitor(isConnected = true)
    val cameraClient = FakeCameraClient(reachable = false)
    val sessionManager = CameraSessionManagerImpl(wifiMonitor, cameraClient, dispatcher)

    val state = sessionManager.assertReady()

    assertEquals(
      SessionState.NotReady(code = SessionNotReadyCode.SDK_UNREACHABLE),
      state
    )
    assertEquals(1, cameraClient.reachableCheckCount)
  }

  @Test
  fun assertReady_whenSdkThrows_returnsNotReady() = runTest(dispatcher) {
    val wifiMonitor = FakeWifiConnectionMonitor(isConnected = true)
    val cameraClient = FakeCameraClient(error = IllegalStateException("boom"))
    val sessionManager = CameraSessionManagerImpl(wifiMonitor, cameraClient, dispatcher)

    val state = sessionManager.assertReady()

    assertEquals(
      SessionState.NotReady(code = SessionNotReadyCode.SDK_UNREACHABLE),
      state
    )
    assertEquals(1, cameraClient.reachableCheckCount)
  }

  @Test
  fun assertReady_whenNativeLibraryMissing_returnsSpecificNotReadyReason() = runTest(dispatcher) {
    val wifiMonitor = FakeWifiConnectionMonitor(isConnected = true)
    val cameraClient = FakeCameraClient(
      error = IllegalStateException("Legacy native library 'fudge' is not available")
    )
    val sessionManager = CameraSessionManagerImpl(wifiMonitor, cameraClient, dispatcher)

    val state = sessionManager.assertReady()

    assertEquals(
      SessionState.NotReady(code = SessionNotReadyCode.SDK_NATIVE_LIBRARY_MISSING),
      state
    )
    assertEquals(1, cameraClient.reachableCheckCount)
  }

  private class FakeWifiConnectionMonitor(
    isConnected: Boolean
  ) : WifiConnectionMonitor {
    val connectedFlow = MutableStateFlow(isConnected)
    override val currentSsid: Flow<String?> = flowOf(null)
    override val isWifiConnected: Flow<Boolean> = connectedFlow
  }

  private class FakeCameraClient(
    private val reachable: Boolean = true,
    private val error: Throwable? = null
  ) : FujifilmCameraClient {
    var reachableCheckCount: Int = 0

    override suspend fun isReachable(): Boolean {
      reachableCheckCount += 1
      error?.let { throw it }
      return reachable
    }

    override suspend fun fetchRemotePhotos(): List<RemotePhoto> = emptyList()

    override suspend fun fetchThumbnail(photoId: PhotoId): ByteArray = ByteArray(0)

    override suspend fun openPreview(photoId: PhotoId): InputStream {
      return ByteArrayInputStream(ByteArray(0))
    }

    override suspend fun openOriginal(photoId: PhotoId): InputStream {
      return ByteArrayInputStream(ByteArray(0))
    }
  }
}
