package dev.danielc.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import dev.danielc.common.WiFiComm
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface WifiConnectionMonitor {
  val currentSsid: Flow<String?>
  val isWifiConnected: Flow<Boolean>
}

class WifiConnectionMonitorImpl(
  context: Context
) : WifiConnectionMonitor {

  private val connectivityManager =
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager

  init {
    WiFiComm.registerConnectivityManager(connectivityManager)
  }

  override val isWifiConnected: Flow<Boolean> = callbackFlow {
    fun emitState() {
      val connected = connectivityManager.hasAnyWifiTransportNetwork()
      connectivityManager.activeNetwork?.let { activeNetwork ->
        val activeCaps = connectivityManager.getNetworkCapabilities(activeNetwork)
        if (activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
          WiFiComm.setDefaultWiFiDevice(activeNetwork)
        }
      }
      trySend(connected)
    }

    emitState()

    val request = NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .build()

    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        WiFiComm.setDefaultWiFiDevice(network)
        emitState()
      }

      override fun onLost(network: Network) {
        WiFiComm.clearDefaultWiFiDeviceIfMatches(network)
        emitState()
      }

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
          WiFiComm.setDefaultWiFiDevice(network)
        }
        emitState()
      }
    }

    connectivityManager.registerNetworkCallback(request, callback)
    awaitClose {
      runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
  }

  override val currentSsid: Flow<String?> = isWifiConnected
    .map { connected ->
      if (!connected) {
        return@map null
      }
      wifiManager.currentSanitizedSsid()
    }
    .distinctUntilChanged()
}

private fun ConnectivityManager.hasAnyWifiTransportNetwork(): Boolean {
  val hasWifi = allNetworks.any { network ->
    val capabilities = getNetworkCapabilities(network) ?: return@any false
    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
  }
  if (hasWifi) {
    return true
  }
  return currentWifiConnected()
}

private fun WifiManager.currentSanitizedSsid(): String? {
  return connectionInfo?.ssid
        ?.removePrefix("\"")
        ?.removeSuffix("\"")
        ?.takeUnless { it.equals("<unknown ssid>", ignoreCase = true) }
}

private fun ConnectivityManager.currentWifiConnected(): Boolean {
  val activeNetwork = activeNetwork ?: return false
  val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
  return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}
