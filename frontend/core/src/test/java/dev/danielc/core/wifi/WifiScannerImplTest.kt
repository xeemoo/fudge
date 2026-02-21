package dev.danielc.core.wifi

import dev.danielc.core.wifi.model.ScanResult
import dev.danielc.core.wifi.model.WifiHotspot
import dev.danielc.core.wifi.model.WifiPermissionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiScannerImplTest {

  @Test
  fun scanOnce_permissionDenied_returnsError() = runTest {
    val scanner = WifiScannerImpl(
      wifiPermissionChecker = FakePermissionChecker(WifiPermissionState.DENIED_CAN_REQUEST),
      startScanAction = { true },
      scanResultsProvider = { listOf(WifiHotspot("FUJIFILM-X", -20)) }
    )

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Error)
    assertEquals(
      WifiScannerImpl.ERROR_PERMISSION_REQUIRED,
      (result as ScanResult.Error).message
    )
  }

  @Test
  fun scanOnce_securityException_returnsReadableError() = runTest {
    val scanner = WifiScannerImpl(
      wifiPermissionChecker = FakePermissionChecker(WifiPermissionState.GRANTED),
      startScanAction = { throw SecurityException("denied") },
      scanResultsProvider = { emptyList() }
    )

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Error)
    assertEquals(
      WifiScannerImpl.ERROR_SECURITY_EXCEPTION,
      (result as ScanResult.Error).message
    )
  }

  @Test
  fun scanOnce_success_sortsFujifilmFirstAndFiltersInvalidSsid() = runTest {
    val scanner = WifiScannerImpl(
      wifiPermissionChecker = FakePermissionChecker(WifiPermissionState.GRANTED),
      startScanAction = { true },
      scanResultsProvider = {
        listOf(
          WifiHotspot("Cafe-Wifi", -30),
          WifiHotspot("   ", -10),
          WifiHotspot("fujifilm-cam", -80),
          WifiHotspot("Home", -40),
          WifiHotspot("Cafe-Wifi", -20)
        )
      }
    )

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Success)
    val hotspots = (result as ScanResult.Success).hotspots
    assertEquals(3, hotspots.size)
    assertEquals("fujifilm-cam", hotspots[0].ssid)
    assertEquals("Cafe-Wifi", hotspots[1].ssid)
    assertEquals(-20, hotspots[1].rssi)
    assertEquals("Home", hotspots[2].ssid)
  }

  @Test
  fun scanOnce_successEmptyList_returnsSuccessWithEmptyHotspots() = runTest {
    val scanner = WifiScannerImpl(
      wifiPermissionChecker = FakePermissionChecker(WifiPermissionState.GRANTED),
      startScanAction = { true },
      scanResultsProvider = { emptyList() }
    )

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Success)
    assertTrue((result as ScanResult.Success).hotspots.isEmpty())
  }

  @Test
  fun scanOnce_wifiDisabled_returnsError() = runTest {
    val scanner = WifiScannerImpl(
      wifiPermissionChecker = FakePermissionChecker(WifiPermissionState.GRANTED),
      startScanAction = { true },
      scanResultsProvider = { emptyList() },
      isWifiEnabledProvider = { false }
    )

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Error)
    assertEquals(
      WifiScannerImpl.ERROR_WIFI_DISABLED,
      (result as ScanResult.Error).message
    )
  }

  @Test
  fun scanOnce_locationDisabled_returnsError() = runTest {
    val scanner = WifiScannerImpl(
      wifiPermissionChecker = FakePermissionChecker(WifiPermissionState.GRANTED),
      startScanAction = { true },
      scanResultsProvider = { emptyList() },
      isLocationEnabledProvider = { false }
    )

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Error)
    assertEquals(
      WifiScannerImpl.ERROR_LOCATION_DISABLED,
      (result as ScanResult.Error).message
    )
  }

  @Test
  fun scanOnce_startScanThrottled_usesCachedResultsInsteadOfError() = runTest {
    val scanner = WifiScannerImpl(
      wifiPermissionChecker = FakePermissionChecker(WifiPermissionState.GRANTED),
      startScanAction = { false },
      scanResultsProvider = { listOf(WifiHotspot("FUJIFILM-X", -35)) }
    )

    val result = scanner.scanOnce().invoke()

    assertTrue(result is ScanResult.Success)
    val hotspots = (result as ScanResult.Success).hotspots
    assertEquals(1, hotspots.size)
    assertEquals("FUJIFILM-X", hotspots.first().ssid)
  }

  private class FakePermissionChecker(
    private val state: WifiPermissionState
  ) : WifiPermissionChecker {
    override fun currentState(): WifiPermissionState = state
    override fun requiredPermissions(): Array<String> = emptyArray()
  }
}
