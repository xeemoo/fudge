package dev.danielc.feature.connect

import dev.danielc.R
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.analytics.WifiConnectSource
import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.HotspotHistoryRepository
import dev.danielc.core.data.SessionState
import dev.danielc.core.data.SessionNotReadyCode
import dev.danielc.core.db.entity.HotspotHistoryEntity
import dev.danielc.core.wifi.WifiConnectState
import dev.danielc.core.wifi.WifiConnector
import dev.danielc.core.wifi.WifiConnectorImpl
import dev.danielc.core.wifi.WifiConnectionMonitor
import dev.danielc.core.wifi.WifiScanner
import dev.danielc.core.wifi.model.ScanResult
import dev.danielc.core.wifi.model.WifiHotspot
import dev.danielc.core.wifi.model.WifiPermissionState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import dev.danielc.ui.UiText

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun onEnterGranted_scansAndUpdatesState() = runTest {
    val scanner = FakeWifiScanner(
      result = ScanResult.Success(
        hotspots = listOf(WifiHotspot(ssid = "FUJIFILM-XT5", rssi = -35))
      )
    )
    val analytics = RecordingAnalyticsTracker()
    val connector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) }
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val historyRepository = FakeHotspotHistoryRepository()
    val viewModel = ConnectViewModel(
      wifiScanner = scanner,
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(),
      cameraSessionManager = sessionManager,
      hotspotHistoryRepository = historyRepository,
      analyticsTracker = analytics
    )

    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()

    assertEquals(WifiPermissionState.GRANTED, viewModel.state.value.permission)
    assertTrue(viewModel.state.value.scanResult is ScanResult.Success)
    assertTrue(!viewModel.state.value.isScanning)
    assertEquals(1, scanner.scanInvocationCount)
    assertTrue(analytics.events.contains(AnalyticsEvent.WifiScanStart))
    assertTrue(analytics.events.contains(AnalyticsEvent.WifiScanResultCount(1)))
  }

  @Test
  fun onConnectSuccess_marksHistoryAndNavigates() = runTest {
    val scanner = FakeWifiScanner(result = ScanResult.Success(emptyList()))
    val connector = FakeWifiConnector { ssid ->
      flow {
        emit(WifiConnectState.Connecting)
        emit(WifiConnectState.Connected(ssid))
      }
    }
    val historyRepository = FakeHotspotHistoryRepository()
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val now = 123456L
    val viewModel = ConnectViewModel(
      wifiScanner = scanner,
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(),
      cameraSessionManager = sessionManager,
      hotspotHistoryRepository = historyRepository,
      nowMillisProvider = { now }
    )

    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()

    val effects = mutableListOf<ConnectContract.Effect>()
    val effectJob = launch(start = CoroutineStart.UNDISPATCHED) {
      viewModel.effect.collect { effect ->
        effects += effect
      }
    }
    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XT5",
        source = ConnectContract.ConnectSource.AVAILABLE
      )
    )
    advanceUntilIdle()
    effectJob.cancel()

    val navigateEffect = effects.filterIsInstance<ConnectContract.Effect.NavigateToPhotoList>().firstOrNull()
    requireNotNull(navigateEffect)
    assertEquals("FUJIFILM-XT5", navigateEffect.ssid)
    assertEquals("FUJIFILM-XT5", historyRepository.lastMarkedSsid)
    assertEquals(now, historyRepository.lastMarkedAtMillis)
    assertEquals(1, sessionManager.assertReadyCallCount)
  }

  @Test
  fun onConnectSuccessButSessionNotReady_staysOnConnectAndShowsReason() = runTest {
    val scanner = FakeWifiScanner(result = ScanResult.Success(emptyList()))
    val connector = FakeWifiConnector { ssid ->
      flow {
        emit(WifiConnectState.Connecting)
        emit(WifiConnectState.Connected(ssid))
      }
    }
    val sessionManager = FakeCameraSessionManager(
      SessionState.NotReady(code = SessionNotReadyCode.SDK_UNREACHABLE)
    )
    val historyRepository = FakeHotspotHistoryRepository()
    val viewModel = ConnectViewModel(
      wifiScanner = scanner,
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(),
      cameraSessionManager = sessionManager,
      hotspotHistoryRepository = historyRepository
    )

    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()

    val effects = mutableListOf<ConnectContract.Effect>()
    val effectJob = launch(start = CoroutineStart.UNDISPATCHED) {
      viewModel.effect.collect { effect ->
        effects += effect
      }
    }
    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XT5",
        source = ConnectContract.ConnectSource.AVAILABLE
      )
    )
    advanceUntilIdle()
    effectJob.cancel()

    assertTrue(effects.none { it is ConnectContract.Effect.NavigateToPhotoList })
    assertEquals("FUJIFILM-XT5", viewModel.state.value.failedSsid)
    assertEquals(UiText.Res(R.string.connect_error_sdk_unreachable), viewModel.state.value.errorMessage)
    assertEquals(null, historyRepository.lastMarkedSsid)
    assertEquals(1, sessionManager.assertReadyCallCount)
  }

  @Test
  fun onConnectFailed_updatesErrorStateForRetry() = runTest {
    val scanner = FakeWifiScanner(result = ScanResult.Success(emptyList()))
    val connector = FakeWifiConnector {
      flow {
        emit(WifiConnectState.Connecting)
        emit(WifiConnectState.Failed(WifiConnectorImpl.ERROR_REQUEST_FAILED))
      }
    }
    val viewModel = ConnectViewModel(
      wifiScanner = scanner,
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )

    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()

    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XT5",
        source = ConnectContract.ConnectSource.AVAILABLE
      )
    )
    advanceUntilIdle()

    assertEquals("FUJIFILM-XT5", viewModel.state.value.failedSsid)
    assertEquals(UiText.Res(R.string.connect_error_request_failed), viewModel.state.value.errorMessage)
    assertEquals(null, viewModel.state.value.connectingSsid)
  }

  @Test
  fun onRetryScanWithoutPermission_emitsPermissionToast() = runTest {
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(ScanResult.Success(emptyList())),
      wifiConnector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) },
      wifiConnectionMonitor = FakeWifiConnectionMonitor(),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )
    val effect = async { viewModel.effect.first() }
    runCurrent()

    viewModel.accept(ConnectContract.Intent.OnRetryScan)
    advanceUntilIdle()

    assertEquals(
      ConnectContract.Effect.Toast(UiText.Res(R.string.connect_toast_scan_permission_required)),
      effect.await()
    )
  }

  @Test
  fun onConnectWithoutPermission_emitsPermissionToast() = runTest {
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(ScanResult.Success(emptyList())),
      wifiConnector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) },
      wifiConnectionMonitor = FakeWifiConnectionMonitor(),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )
    val effect = async { viewModel.effect.first() }
    runCurrent()

    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XT5",
        source = ConnectContract.ConnectSource.AVAILABLE
      )
    )
    advanceUntilIdle()

    assertEquals(
      ConnectContract.Effect.Toast(UiText.Res(R.string.connect_toast_connect_permission_required)),
      effect.await()
    )
  }

  @Test
  fun onClickConnect_withActiveSession_showsSwitchConfirmDialog() = runTest {
    val connector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) }
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(ScanResult.Success(emptyList())),
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(ssid = "FUJIFILM-XT5"),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )
    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()

    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XH2",
        source = ConnectContract.ConnectSource.AVAILABLE
      )
    )
    advanceUntilIdle()

    assertEquals("FUJIFILM-XH2", viewModel.state.value.pendingConnectSsid)
    assertTrue(viewModel.state.value.showSwitchConfirmDialog)
    assertEquals(0, connector.connectRequests.size)
  }

  @Test
  fun onConfirmSwitchConnect_disconnectsThenConnectsPendingHotspot() = runTest {
    val connector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) }
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(ScanResult.Success(emptyList())),
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(ssid = "FUJIFILM-XT5"),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )
    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()
    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XH2",
        source = ConnectContract.ConnectSource.AVAILABLE
      )
    )
    advanceUntilIdle()

    viewModel.accept(ConnectContract.Intent.OnConfirmSwitchConnect)
    advanceUntilIdle()

    assertEquals(1, connector.disconnectCallCount)
    assertEquals(listOf("FUJIFILM-XH2"), connector.connectRequests)
    assertEquals(null, viewModel.state.value.pendingConnectSsid)
    assertTrue(!viewModel.state.value.showSwitchConfirmDialog)
  }

  @Test
  fun onDismissSwitchConnect_keepsOriginalConnectionAndDoesNotConnectNewHotspot() = runTest {
    val connector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) }
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(ScanResult.Success(emptyList())),
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(ssid = "FUJIFILM-XT5"),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )
    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()
    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XH2",
        source = ConnectContract.ConnectSource.AVAILABLE
      )
    )
    advanceUntilIdle()

    viewModel.accept(ConnectContract.Intent.OnDismissSwitchConnect)
    advanceUntilIdle()

    assertEquals(0, connector.disconnectCallCount)
    assertEquals(0, connector.connectRequests.size)
    assertEquals(null, viewModel.state.value.pendingConnectSsid)
    assertTrue(!viewModel.state.value.showSwitchConfirmDialog)
    assertEquals("FUJIFILM-XT5", viewModel.state.value.activeSessionSsid)
  }

  @Test
  fun onClickDisconnectActiveSession_disconnectsCurrentConnection() = runTest {
    val connector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) }
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(ScanResult.Success(emptyList())),
      wifiConnector = connector,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(ssid = "FUJIFILM-XT5"),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )
    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()

    viewModel.accept(ConnectContract.Intent.OnClickDisconnectActiveSession)
    advanceUntilIdle()

    assertEquals(1, connector.disconnectCallCount)
    assertEquals(null, viewModel.state.value.activeSessionSsid)
  }

  @Test
  fun onClickOpenActiveSession_navigatesToPhotoList() = runTest {
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(ScanResult.Success(emptyList())),
      wifiConnector = FakeWifiConnector { flowOf(WifiConnectState.Connecting) },
      wifiConnectionMonitor = FakeWifiConnectionMonitor(ssid = "FUJIFILM-XT5"),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository()
    )
    val effect = async { viewModel.effect.first() }
    advanceUntilIdle()

    assertEquals("FUJIFILM-XT5", viewModel.state.value.activeSessionSsid)

    viewModel.accept(ConnectContract.Intent.OnClickOpenActiveSession)
    advanceUntilIdle()

    assertEquals(
      ConnectContract.Effect.NavigateToPhotoList("FUJIFILM-XT5"),
      effect.await()
    )
  }

  @Test
  fun onConnectFromHistory_tracksConnectSourceAndSuccess() = runTest {
    val analytics = RecordingAnalyticsTracker()
    val viewModel = ConnectViewModel(
      wifiScanner = FakeWifiScanner(result = ScanResult.Success(emptyList())),
      wifiConnector = FakeWifiConnector { ssid ->
        flow {
          emit(WifiConnectState.Connecting)
          emit(WifiConnectState.Connected(ssid))
        }
      },
      wifiConnectionMonitor = FakeWifiConnectionMonitor(),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      hotspotHistoryRepository = FakeHotspotHistoryRepository(),
      analyticsTracker = analytics
    )
    viewModel.accept(ConnectContract.Intent.OnEnter(WifiPermissionState.GRANTED))
    advanceUntilIdle()

    viewModel.accept(
      ConnectContract.Intent.OnClickConnect(
        ssid = "FUJIFILM-XT5",
        source = ConnectContract.ConnectSource.HISTORY
      )
    )
    advanceUntilIdle()

    assertTrue(analytics.events.contains(AnalyticsEvent.WifiConnectClick(WifiConnectSource.HISTORY)))
    assertTrue(analytics.events.contains(AnalyticsEvent.WifiConnectSuccess))
  }
}

private class FakeWifiScanner(
  private val result: ScanResult
) : WifiScanner {

  var scanInvocationCount: Int = 0
    private set

  override fun scanOnce(): suspend () -> ScanResult = {
    scanInvocationCount += 1
    result
  }

  override fun observeScan(intervalMs: Long): Flow<ScanResult> {
    return flowOf(result)
  }
}

private class FakeWifiConnector(
  private val connectFlowProvider: (ssid: String) -> Flow<WifiConnectState>
) : WifiConnector {

  val connectRequests = mutableListOf<String>()
  var disconnectCallCount: Int = 0
    private set

  override fun connect(ssid: String): Flow<WifiConnectState> {
    connectRequests += ssid
    return connectFlowProvider(ssid)
  }

  override fun disconnectActiveRequest() {
    disconnectCallCount += 1
  }
}

private class FakeCameraSessionManager(
  private val assertReadyResult: SessionState
) : CameraSessionManager {

  private val _sessionState = MutableStateFlow(assertReadyResult)
  var assertReadyCallCount: Int = 0
    private set

  override val sessionState: Flow<SessionState> = _sessionState

  override suspend fun assertReady(): SessionState {
    assertReadyCallCount += 1
    return assertReadyResult
  }
}

private class FakeWifiConnectionMonitor(
  ssid: String? = null
) : WifiConnectionMonitor {

  private val connected = ssid != null

  override val currentSsid: Flow<String?> = MutableStateFlow(ssid)
  override val isWifiConnected: Flow<Boolean> = MutableStateFlow(connected)
}

private class FakeHotspotHistoryRepository : HotspotHistoryRepository {

  var lastMarkedSsid: String? = null
    private set
  var lastMarkedAtMillis: Long? = null
    private set

  override fun observeHistory(): Flow<List<HotspotHistoryEntity>> = flowOf(emptyList())

  override suspend fun markConnected(ssid: String, atEpochMillis: Long) {
    lastMarkedSsid = ssid
    lastMarkedAtMillis = atEpochMillis
  }
}

private class RecordingAnalyticsTracker : AnalyticsTracker {
  val events = mutableListOf<AnalyticsEvent>()

  override fun track(event: AnalyticsEvent) {
    events += event
  }
}
