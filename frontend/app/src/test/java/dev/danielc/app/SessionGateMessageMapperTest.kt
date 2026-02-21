package dev.danielc.app

import dev.danielc.R
import dev.danielc.core.data.SessionNotReadyCode
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionGateMessageMapperTest {

  @Test
  fun sessionMessageResIdFor_wifiDisconnected_mapsToRecoverableHint() {
    assertEquals(
      R.string.connect_error_wifi_disconnected,
      sessionMessageResIdFor(SessionNotReadyCode.WIFI_DISCONNECTED)
    )
  }
}
