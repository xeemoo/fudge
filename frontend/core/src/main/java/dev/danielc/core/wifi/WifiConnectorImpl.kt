package dev.danielc.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.RouteInfo
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import dev.danielc.common.WiFiComm
import dev.danielc.fujiapp.MySettings
import java.net.Inet4Address
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class WifiConnectorImpl(
  private val networkRequester: WifiNetworkRequester
) : WifiConnector {

  constructor(context: Context) : this(
    networkRequester = AndroidWifiNetworkRequester(
      connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    )
  )

  private var activeCallback: WifiNetworkRequester.Callback? = null

  override fun connect(ssid: String): Flow<WifiConnectState> = callbackFlow {
    disconnectActiveRequest()
    trySend(WifiConnectState.Connecting)

    val callback = object : WifiNetworkRequester.Callback {
      override fun onAvailable(networkHandle: Long) {
        trySend(WifiConnectState.Connected(ssid))
      }

      override fun onUnavailable() {
        handleFailure(this, ERROR_UNAVAILABLE, this@callbackFlow)
      }

      override fun onLost(networkHandle: Long) {
        handleFailure(this, ERROR_LOST, this@callbackFlow)
      }
    }

    activeCallback = callback
    val requestResult = runCatching {
      networkRequester.requestNetwork(ssid, callback)
    }

    if (requestResult.isFailure) {
      val exception = requestResult.exceptionOrNull()
      activeCallback = null
      trySend(
        WifiConnectState.Failed(
          exception?.message ?: ERROR_REQUEST_FAILED
        )
      )
      close()
      return@callbackFlow
    }

    awaitClose {
      // Keep callback alive across navigation to preserve local-only Wi-Fi session.
    }
  }

  override fun disconnectActiveRequest() {
    val callback = activeCallback ?: return
    activeCallback = null
    runCatching { networkRequester.unregister(callback) }
  }

  private fun handleFailure(
    callback: WifiNetworkRequester.Callback,
    message: String,
    channel: kotlinx.coroutines.channels.ProducerScope<WifiConnectState>
  ) {
    if (activeCallback != callback) {
      return
    }
    channel.trySend(WifiConnectState.Failed(message))
    disconnectActiveRequest()
    channel.close()
  }

  companion object {
    const val ERROR_UNAVAILABLE = "Connection failed. Verify hotspot availability and retry."
    const val ERROR_LOST = "Connection lost. Please retry."
    const val ERROR_REQUEST_FAILED = "Unable to start Wi-Fi connection request."
    const val ERROR_UNSUPPORTED_VERSION = "In-app Wi-Fi connection is not supported on this Android version."
  }
}

interface WifiNetworkRequester {
  fun requestNetwork(ssid: String, callback: Callback)
  fun unregister(callback: Callback)

  interface Callback {
    fun onAvailable(networkHandle: Long)
    fun onUnavailable()
    fun onLost(networkHandle: Long)
  }
}

private class AndroidWifiNetworkRequester(
  private val connectivityManager: ConnectivityManager
) : WifiNetworkRequester {

  private val callbackMap = mutableMapOf<WifiNetworkRequester.Callback, ConnectivityManager.NetworkCallback>()

  override fun requestNetwork(ssid: String, callback: WifiNetworkRequester.Callback) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      throw IllegalStateException(WifiConnectorImpl.ERROR_UNSUPPORTED_VERSION)
    }

    val specifier = WifiNetworkSpecifier.Builder()
      .setSsid(ssid)
      .build()

    val request = NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
      .setNetworkSpecifier(specifier)
      .build()

    val androidCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        val networkHandle = network.networkHandle
        WiFiComm.registerConnectivityManager(connectivityManager)
        WiFiComm.setFoundWiFiDevice(network)
        syncLegacyCameraIp(network)
        callback.onAvailable(networkHandle)
      }

      override fun onUnavailable() {
        WiFiComm.clearFoundWiFiDevice()
        callback.onUnavailable()
      }

      override fun onLost(network: Network) {
        val networkHandle = network.networkHandle
        WiFiComm.clearFoundWiFiDeviceIfMatches(network)
        callback.onLost(networkHandle)
      }

      override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
        syncLegacyCameraIp(network)
      }
    }

    callbackMap[callback] = androidCallback
    WiFiComm.registerConnectivityManager(connectivityManager)
    connectivityManager.requestNetwork(request, androidCallback)
  }

  override fun unregister(callback: WifiNetworkRequester.Callback) {
    val androidCallback = callbackMap.remove(callback) ?: return
    // Keep handle while connected; it will be cleared via onLost/onUnavailable.
    runCatching { connectivityManager.unregisterNetworkCallback(androidCallback) }
  }

  private fun syncLegacyCameraIp(network: Network) {
    val linkProperties = connectivityManager.getLinkProperties(network) ?: return
    val candidates = linkedSetOf<String>()

    linkProperties.routes
      .mapNotNull(RouteInfo::getGateway)
      .mapNotNull { address -> address.hostAddress }
      .filter { host -> isUsableIpv4Host(host) }
      .forEach(candidates::add)

    linkProperties.dnsServers
      .mapNotNull { address -> address.hostAddress }
      .filter { host -> isUsableIpv4Host(host) }
      .forEach(candidates::add)

    linkProperties.linkAddresses
      .mapNotNull { linkAddress ->
        val inet = linkAddress.address
        if (inet is Inet4Address) inet.hostAddress else null
      }
      .filter { host -> isUsableIpv4Host(host) }
      .mapNotNull(::toSubnetGatewayCandidate)
      .forEach(candidates::add)

    // Keep common defaults as final fallback.
    candidates.add("192.168.0.1")
    candidates.add("192.168.1.1")

    val list = candidates.toList()
    if (list.isEmpty()) {
      return
    }

    MySettings.setIpCandidates(list.toTypedArray())
  }
}

private fun isIpv4Host(host: String?): Boolean {
  if (host.isNullOrBlank()) return false
  return host.count { it == '.' } == 3
}

private fun isUsableIpv4Host(host: String?): Boolean {
  if (!isIpv4Host(host)) return false
  if (host == "0.0.0.0") return false
  if (host == "255.255.255.255") return false
  return true
}

private fun toSubnetGatewayCandidate(ipv4: String): String? {
  val parts = ipv4.split('.')
  if (parts.size != 4) return null
  val candidate = "${parts[0]}.${parts[1]}.${parts[2]}.1"
  return candidate.takeIf(::isUsableIpv4Host)
}
