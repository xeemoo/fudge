package dev.danielc.core.work

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.danielc.core.work.notification.DownloadNotificationFactory
import dev.danielc.core.work.worker.DownloadQueueWorker
import dev.danielc.core.work.worker.DownloadQueueWorkerDependency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DownloadQueueSchedulerImplTest {

  private lateinit var context: Context
  private lateinit var workManager: WorkManager

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()

    val configuration = Configuration.Builder()
      .setMinimumLoggingLevel(Log.DEBUG)
      .setExecutor(SynchronousExecutor())
      .setWorkerFactory(
        AppWorkerFactory(
          dummyDependency = DummyWorkerDependency(),
          downloadQueueDependency = DownloadQueueWorkerDependency(),
          downloadNotificationFactory = NoOpNotificationFactory(context)
        )
      )
      .build()

    WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    workManager = WorkManager.getInstance(context)
  }

  @Test
  fun kick_enqueuesUniqueWork() {
    val scheduler = DownloadQueueSchedulerImpl(workManager)

    scheduler.kick("queue-A").result.get()

    val infos = workManager.getWorkInfosForUniqueWork(DownloadQueueSchedulerImpl.UNIQUE_WORK_NAME).get()
    assertTrue(infos.isNotEmpty())
  }

  @Test
  fun createInputData_containsQueueId() {
    val scheduler = DownloadQueueSchedulerImpl(workManager)

    val inputData = scheduler.createInputData("queue-xyz")

    assertEquals("queue-xyz", inputData.getString(DownloadQueueWorker.KEY_QUEUE_ID))
  }
}

private class NoOpNotificationFactory(
  private val context: Context
) : DownloadNotificationFactory {
  override fun createForegroundInfo(progressText: String): ForegroundInfo {
    val notification = NotificationCompat.Builder(context, "test-channel")
      .setSmallIcon(android.R.drawable.stat_sys_download)
      .setContentTitle("test")
      .setContentText(progressText)
      .build()
    return ForegroundInfo(1, notification)
  }
}
