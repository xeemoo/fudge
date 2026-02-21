package dev.danielc.core.work.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.danielc.core.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultDownloadNotificationFactoryTest {

  @Test
  fun createForegroundInfo_createsLowImportanceChannelAndNotificationContent() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val factory = DefaultDownloadNotificationFactory(context)

    val foregroundInfo = factory.createForegroundInfo("Downloading item 1...")

    assertEquals(DefaultDownloadNotificationFactory.NOTIFICATION_ID, foregroundInfo.notificationId)
    assertEquals("Downloading item 1...", foregroundInfo.notification.extras.getString(Notification.EXTRA_TEXT))

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val channel = notificationManager.getNotificationChannel(DefaultDownloadNotificationFactory.CHANNEL_ID)
    assertNotNull(channel)
    assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
    assertEquals(
      context.getString(R.string.download_notification_channel_name),
      channel.name.toString()
    )
  }
}
