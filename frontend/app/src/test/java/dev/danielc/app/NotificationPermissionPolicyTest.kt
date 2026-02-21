package dev.danielc.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {

  @Test
  fun shouldRequestPermission_whenApiBelow33_returnsFalse() {
    val shouldRequest = NotificationPermissionPolicy.shouldRequestPermission(
      sdkInt = 32,
      isGranted = false,
      hasRequestedBefore = false
    )

    assertFalse(shouldRequest)
  }

  @Test
  fun shouldRequestPermission_whenApi33NotGrantedAndNotRequested_returnsTrue() {
    val shouldRequest = NotificationPermissionPolicy.shouldRequestPermission(
      sdkInt = 33,
      isGranted = false,
      hasRequestedBefore = false
    )

    assertTrue(shouldRequest)
  }

  @Test
  fun shouldRequestPermission_whenAlreadyGranted_returnsFalse() {
    val shouldRequest = NotificationPermissionPolicy.shouldRequestPermission(
      sdkInt = 33,
      isGranted = true,
      hasRequestedBefore = false
    )

    assertFalse(shouldRequest)
  }

  @Test
  fun shouldRequestPermission_whenAlreadyRequested_returnsFalse() {
    val shouldRequest = NotificationPermissionPolicy.shouldRequestPermission(
      sdkInt = 33,
      isGranted = false,
      hasRequestedBefore = true
    )

    assertFalse(shouldRequest)
  }
}
