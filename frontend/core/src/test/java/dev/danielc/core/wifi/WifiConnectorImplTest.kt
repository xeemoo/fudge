package dev.danielc.core.wifi

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WifiConnectorImplTest {

  @Test
  fun connect_emitsConnectingThenConnected() = runTest {
    val requester = FakeNetworkRequester(autoEvent = AutoEvent.AVAILABLE)
    val connector = WifiConnectorImpl(requester)

    val states = async {
      connector.connect("FUJIFILM-X").take(2).toList()
    }.await()

    assertEquals(WifiConnectState.Connecting, states[0])
    assertEquals(WifiConnectState.Connected("FUJIFILM-X"), states[1])
  }

  @Test
  fun connect_onUnavailable_emitsFailed() = runTest {
    val requester = FakeNetworkRequester(autoEvent = AutoEvent.UNAVAILABLE)
    val connector = WifiConnectorImpl(requester)

    val states = async {
      connector.connect("FUJIFILM-X").take(2).toList()
    }.await()

    assertEquals(WifiConnectState.Connecting, states[0])
    assertTrue(states[1] is WifiConnectState.Failed)
    assertEquals(
      WifiConnectorImpl.ERROR_UNAVAILABLE,
      (states[1] as WifiConnectState.Failed).message
    )
  }

  @Test
  fun disconnectActiveRequest_unregistersCallback() = runTest {
    val requester = FakeNetworkRequester(autoEvent = AutoEvent.NONE)
    val connector = WifiConnectorImpl(requester)

    val collectJob = async {
      connector.connect("FUJIFILM-X").take(1).toList()
    }

    collectJob.await()
    connector.disconnectActiveRequest()

    assertEquals(1, requester.unregisterCount)
  }

  private class FakeNetworkRequester(
    private val autoEvent: AutoEvent
  ) : WifiNetworkRequester {
    var callback: WifiNetworkRequester.Callback? = null
    var unregisterCount: Int = 0

    override fun requestNetwork(ssid: String, callback: WifiNetworkRequester.Callback) {
      this.callback = callback
      when (autoEvent) {
        AutoEvent.NONE -> Unit
        AutoEvent.AVAILABLE -> callback.onAvailable(123L)
        AutoEvent.UNAVAILABLE -> callback.onUnavailable()
      }
    }

    override fun unregister(callback: WifiNetworkRequester.Callback) {
      unregisterCount += 1
      if (this.callback == callback) {
        this.callback = null
      }
    }
  }

  private enum class AutoEvent {
    NONE,
    AVAILABLE,
    UNAVAILABLE
  }
}
