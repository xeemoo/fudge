package dev.danielc.core.work.worker

import android.content.Context
import android.app.Notification
import androidx.test.core.app.ApplicationProvider
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import dev.danielc.core.work.AppWorkerFactory
import dev.danielc.core.work.DummyWorkerDependency
import dev.danielc.core.work.notification.DownloadNotificationFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DownloadQueueWorkerTest {

  @Test
  fun worker_createdByFactory_readsQueueIdInputData_andBuildsForegroundInfo() = runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val dependency = DownloadQueueWorkerDependency()
    val notificationFactory = RecordingNotificationFactory(context)

    val worker = TestListenableWorkerBuilder<DownloadQueueWorker>(context)
      .setWorkerFactory(
        AppWorkerFactory(
          dummyDependency = DummyWorkerDependency(),
          downloadQueueDependency = dependency,
          downloadNotificationFactory = notificationFactory
        )
      )
      .setInputData(workDataOf(DownloadQueueWorker.KEY_QUEUE_ID to "queue-123"))
      .build()

    val result = worker.doWork()

    assertTrue(result is ListenableWorker.Result.Success)
    assertEquals("queue-123", dependency.lastQueueId)
    assertEquals(listOf("Downloading..."), notificationFactory.progressTexts)
  }
}

private class RecordingNotificationFactory(
  private val context: Context
) : DownloadNotificationFactory {
  val progressTexts = mutableListOf<String>()

  override fun createForegroundInfo(progressText: String): ForegroundInfo {
    progressTexts += progressText
    val notification: Notification = NotificationCompat.Builder(context, "test-channel")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("test")
      .setContentText(progressText)
      .build()
    return ForegroundInfo(1, notification)
  }
}
