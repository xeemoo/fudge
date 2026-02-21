package dev.danielc.core.wifi

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import dev.danielc.core.wifi.model.ScanResult
import dev.danielc.core.wifi.model.WifiHotspot
import dev.danielc.core.wifi.model.WifiPermissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class WifiScannerImpl(
  private val wifiPermissionChecker: WifiPermissionChecker,
  private val startScanAction: () -> Boolean,
  private val awaitScanResultsAvailable: suspend () -> Unit = {},
  private val scanResultsProvider: () -> List<WifiHotspot>,
  private val isWifiEnabledProvider: () -> Boolean = { true },
  private val isLocationEnabledProvider: () -> Boolean = { true },
  private val nowMillisProvider: () -> Long = { System.currentTimeMillis() }
) : WifiScanner {

  private var lastActiveScanAtMillis: Long = Long.MIN_VALUE

  constructor(
    context: Context,
    wifiPermissionChecker: WifiPermissionChecker
  ) : this(
    wifiPermissionChecker = wifiPermissionChecker,
    startScanAction = { context.requireWifiManagerForScan().startScan() },
    awaitScanResultsAvailable = { context.awaitWifiScanBroadcast() },
    scanResultsProvider = {
      context.requireWifiManagerForScan().scanResults.map { raw ->
        WifiHotspot(ssid = raw.SSID.orEmpty(), rssi = raw.level)
      }
    },
    isWifiEnabledProvider = { context.requireWifiManagerForScan().isWifiEnabled },
    isLocationEnabledProvider = { context.isLocationServiceEnabledForWifiScan() }
  )

  override fun scanOnce(): suspend () -> ScanResult = {
    performScan()
  }

  override fun observeScan(intervalMs: Long): Flow<ScanResult> = flow {
    emit(performScan())
  }

  @SuppressLint("MissingPermission")
  private suspend fun performScan(): ScanResult {
    val permissionState = wifiPermissionChecker.currentState()
    val wifiEnabled = isWifiEnabledProvider()
    val locationEnabled = isLocationEnabledProvider()

    if (permissionState != WifiPermissionState.GRANTED) {
      return ScanResult.Error(ERROR_PERMISSION_REQUIRED)
    }
    if (!wifiEnabled) {
      return ScanResult.Error(ERROR_WIFI_DISABLED)
    }
    if (!locationEnabled) {
      return ScanResult.Error(ERROR_LOCATION_DISABLED)
    }

    val fallbackHotspots = normalize(scanResultsProvider())
    return try {
      val shouldIssueActiveScan = shouldIssueActiveScanRequest()
      if (!shouldIssueActiveScan) {
        val passiveResults = readAfterPassiveWait(fallbackHotspots)
        return ScanResult.Success(passiveResults)
      }

      val didStartScan = startScanAction()
      if (didStartScan) {
        lastActiveScanAtMillis = nowMillisProvider()
        val latestHotspots = readAfterPassiveWait(fallbackHotspots)
        return ScanResult.Success(latestHotspots)
      }

      // When startScan() is throttled or rejected, fall back to existing scanResults.
      val throttledResults = readAfterPassiveWait(fallbackHotspots)
      ScanResult.Success(throttledResults)
    } catch (exception: SecurityException) {
      ScanResult.Error(ERROR_SECURITY_EXCEPTION)
    } catch (throwable: Throwable) {
      ScanResult.Error(ERROR_GENERIC)
    }
  }

  private suspend fun readAfterPassiveWait(fallbackHotspots: List<WifiHotspot>): List<WifiHotspot> {
    runCatching { awaitScanResultsAvailable() }
    val latestHotspots = normalize(scanResultsProvider())
    if (latestHotspots.isNotEmpty()) {
      return latestHotspots
    }
    return fallbackHotspots
  }

  private fun shouldIssueActiveScanRequest(): Boolean {
    if (lastActiveScanAtMillis == Long.MIN_VALUE) {
      return true
    }
    val elapsed = nowMillisProvider() - lastActiveScanAtMillis
    return elapsed >= ACTIVE_SCAN_MIN_INTERVAL_MILLIS
  }

  private fun normalize(rawHotspots: List<WifiHotspot>): List<WifiHotspot> {
    val trimmedHotspots = rawHotspots
      .asSequence()
      .map { hotspot -> hotspot.copy(ssid = hotspot.ssid.trim()) }
      .toList()
    val nonEmptyHotspots = trimmedHotspots
      .asSequence()
      .filter { hotspot -> hotspot.ssid.isNotEmpty() }
      .toList()
    val deduplicatedHotspots = nonEmptyHotspots
      .asSequence()
      .groupBy { hotspot -> hotspot.ssid }
      .mapNotNull { (_, grouped) ->
        grouped.maxByOrNull { hotspot -> hotspot.rssi ?: Int.MIN_VALUE }
      }
      .sortedWith(
        compareByDescending<WifiHotspot> { hotspot -> hotspot.isFujifilm }
          .thenByDescending { hotspot -> hotspot.rssi ?: Int.MIN_VALUE }
          .thenBy { hotspot -> hotspot.ssid.lowercase() }
      )
    return deduplicatedHotspots
  }

  companion object {
    private const val ACTIVE_SCAN_MIN_INTERVAL_MILLIS = 30_000L

    const val ERROR_PERMISSION_REQUIRED = "Missing Wi-Fi permission. Grant permission and try again."
    const val ERROR_WIFI_DISABLED = "Wi-Fi is disabled. Enable Wi-Fi and try again."
    const val ERROR_LOCATION_DISABLED = "Location service is disabled. Enable location and try again."
    const val ERROR_SECURITY_EXCEPTION = "Wi-Fi scan request was rejected by the system."
    const val ERROR_GENERIC = "Wi-Fi scan failed. Please try again."
  }
}

private fun Context.requireWifiManagerForScan(): WifiManager {
  return requireNotNull(getSystemService(Context.WIFI_SERVICE) as? WifiManager)
}

private suspend fun Context.awaitWifiScanBroadcast() {
  val appContext = applicationContext
  withTimeout(WIFI_SCAN_TIMEOUT_MILLIS) {
    suspendCancellableCoroutine { continuation ->
      var unregistered = false
      lateinit var receiver: BroadcastReceiver

      fun unregisterReceiverSafely() {
        if (!unregistered) {
          unregistered = true
          runCatching { appContext.unregisterReceiver(receiver) }
        }
      }

      receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
          if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
            return
          }
          unregisterReceiverSafely()
          if (continuation.isActive) {
            continuation.resume(Unit)
          }
        }
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        appContext.registerReceiver(
          receiver,
          IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
          Context.RECEIVER_NOT_EXPORTED
        )
      } else {
        @Suppress("DEPRECATION")
        appContext.registerReceiver(
          receiver,
          IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
      }

      continuation.invokeOnCancellation {
        unregisterReceiverSafely()
      }
    }
  }
}

private const val WIFI_SCAN_TIMEOUT_MILLIS = 6_000L

private fun Context.isLocationServiceEnabledForWifiScan(): Boolean {
  val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
    locationManager.isLocationEnabled
  } else {
    @Suppress("DEPRECATION")
    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
      locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
  }
}
