package dev.danielc.core.wifi.fake

import dev.danielc.core.wifi.WifiConnectState
import dev.danielc.core.wifi.model.ScanResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeWifiStackTest {

  @Test
  fun `scan returns configured hotspots`() = runTest {
    val runtimeState = FakeWifiRuntimeState()
    val scanner = FakeWifiScanner(FakeWifiPermissionChecker(), runtimeState)

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Success)
    val hotspots = (result as ScanResult.Success).hotspots
    assertTrue(hotspots.isNotEmpty())
    assertTrue(hotspots.any { it.ssid.contains("FUJIFILM") })
  }

  @Test
  fun `connect updates monitor state and disconnect clears it`() = runTest {
    val runtimeState = FakeWifiRuntimeState()
    val connector = FakeWifiConnector(runtimeState)
    val monitor = FakeWifiConnectionMonitor(runtimeState)

    val states = connector.connect("FUJIFILM-XT5").toList()

    assertEquals(WifiConnectState.Connecting, states[0])
    assertEquals(WifiConnectState.Connected("FUJIFILM-XT5"), states[1])
    assertTrue(monitor.isWifiConnected.first())
    assertEquals("FUJIFILM-XT5", monitor.currentSsid.first())

    connector.disconnectActiveRequest()
    assertFalse(monitor.isWifiConnected.first())
    assertEquals(null, monitor.currentSsid.first())
  }
}
