package dev.danielc.core.work.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import dev.danielc.core.db.AppDatabase
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.FujifilmCameraClient
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.SdkErrorCode
import dev.danielc.core.media.MediaStoreImageSaver
import dev.danielc.core.media.SaveImageRequest
import dev.danielc.core.work.AppWorkerFactory
import dev.danielc.core.work.DownloadQueueSchedulerImpl
import dev.danielc.core.work.DummyWorkerDependency
import dev.danielc.core.work.notification.DownloadNotificationFactory
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadQueueWorkerWorkManagerIntegrationTest {

  private lateinit var context: Context
  private lateinit var database: AppDatabase
  private lateinit var taskDao: DownloadTaskDao
  private lateinit var downloadedDao: DownloadedPhotoDao

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    taskDao = database.downloadTaskDao()
    downloadedDao = database.downloadedPhotoDao()
  }

  @After
  fun tearDown() {
    runCatching {
      WorkManager.getInstance(context).cancelAllWork().result.get()
      WorkManager.getInstance(context).pruneWork().result.get()
    }
    database.close()
  }

  @Test
  fun workManagerQueue_whenSingleTaskSucceeds_transitionsQueuedToDownloadingToSuccess() = runTest {
    val queueId = "queue-success"
    val photoId = "photo-success"
    val saver = IntegrationFakeMediaStoreImageSaver()
    var statusObservedAtWrite: DownloadStatus? = null
    saver.beforeWrite = {
      statusObservedAtWrite = taskDao.observeByPhotoId(photoId).first()?.status
    }
    val scheduler = initWorkManager(
      cameraClient = IntegrationFakeCameraClient(),
      saver = saver
    )

    taskDao.insert(task(taskId = "task-success", queueId = queueId, photoId = photoId, createdAt = 1L))
    assertEquals(DownloadStatus.QUEUED, taskDao.observeByPhotoId(photoId).first()?.status)

    scheduler.kick(queueId).result.get()
    awaitUniqueWorkFinished()

    val finalTask = taskDao.observeByPhotoId(photoId).first()
    assertEquals(DownloadStatus.DOWNLOADING, statusObservedAtWrite)
    assertEquals(DownloadStatus.SUCCESS, finalTask?.status)
    assertEquals(100, finalTask?.progressPercent)
    assertNotNull(finalTask?.localUri)
    assertNotNull(downloadedDao.get(photoId))
    assertTrue(saver.publishedUris.isNotEmpty())
  }

  @Test
  fun workManagerQueue_whenSdkError_marksFailedAndContinuesNextTask() = runTest {
    val queueId = "queue-sdk-fail"
    val scheduler = initWorkManager(
      cameraClient = IntegrationFakeCameraClient(failPhotoIds = setOf("photo-fail")),
      saver = IntegrationFakeMediaStoreImageSaver()
    )

    taskDao.insert(task(taskId = "task-fail", queueId = queueId, photoId = "photo-fail", createdAt = 1L))
    taskDao.insert(task(taskId = "task-ok", queueId = queueId, photoId = "photo-ok", createdAt = 2L))

    scheduler.kick(queueId).result.get()
    awaitUniqueWorkFinished()

    val failed = taskDao.observeByPhotoId("photo-fail").first()
    val success = taskDao.observeByPhotoId("photo-ok").first()
    assertEquals(DownloadStatus.FAILED, failed?.status)
    assertEquals(DownloadErrorCode.SDK_ERROR, failed?.errorCode)
    assertEquals(DownloadStatus.SUCCESS, success?.status)
    assertNotNull(downloadedDao.get("photo-ok"))
  }

  @Test
  fun workManagerQueue_whenStorageFull_marksFailedAndContinuesNextTask() = runTest {
    val queueId = "queue-storage-fail"
    val saver = IntegrationFakeMediaStoreImageSaver().apply {
      failWriteCalls[1] = IOException("No space left on device")
    }
    val scheduler = initWorkManager(
      cameraClient = IntegrationFakeCameraClient(),
      saver = saver
    )

    taskDao.insert(task(taskId = "task-storage-fail", queueId = queueId, photoId = "photo-storage-fail", createdAt = 1L))
    taskDao.insert(task(taskId = "task-after-fail", queueId = queueId, photoId = "photo-after-fail", createdAt = 2L))

    scheduler.kick(queueId).result.get()
    awaitUniqueWorkFinished()

    val failed = taskDao.observeByPhotoId("photo-storage-fail").first()
    val success = taskDao.observeByPhotoId("photo-after-fail").first()
    assertEquals(DownloadStatus.FAILED, failed?.status)
    assertEquals(DownloadErrorCode.STORAGE_FULL, failed?.errorCode)
    assertEquals(DownloadStatus.SUCCESS, success?.status)
    assertNotNull(downloadedDao.get("photo-after-fail"))
    assertEquals(1, saver.deletedUris.size)
  }

  private fun initWorkManager(
    cameraClient: FujifilmCameraClient,
    saver: IntegrationFakeMediaStoreImageSaver
  ): DownloadQueueSchedulerImpl {
    val configuration = Configuration.Builder()
      .setMinimumLoggingLevel(Log.DEBUG)
      .setExecutor(SynchronousExecutor())
      .setWorkerFactory(
        AppWorkerFactory(
          dummyDependency = DummyWorkerDependency(),
          downloadQueueDependency = DownloadQueueWorkerDependency(
            taskDao = taskDao,
            downloadedDao = downloadedDao,
            cameraClient = cameraClient,
            saver = saver,
            clock = { 1000L }
          ),
          downloadNotificationFactory = TestNotificationFactory(context)
        )
      )
      .build()

    WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    return DownloadQueueSchedulerImpl(WorkManager.getInstance(context))
  }

  private fun awaitUniqueWorkFinished() {
    repeat(50) {
      val infos = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(DownloadQueueSchedulerImpl.UNIQUE_WORK_NAME)
        .get()
      if (infos.isNotEmpty() && infos.all { info -> info.state.isFinished }) {
        assertTrue(infos.all { info -> info.state == WorkInfo.State.SUCCEEDED })
        return
      }
      Thread.sleep(20)
    }
    throw AssertionError("Timed out waiting for unique work to finish.")
  }

  private fun task(taskId: String, queueId: String, photoId: String, createdAt: Long): DownloadTaskEntity {
    return DownloadTaskEntity(
      taskId = taskId,
      queueId = queueId,
      photoId = photoId,
      status = DownloadStatus.QUEUED,
      progressPercent = 0,
      errorCode = null,
      localUri = null,
      createdAtEpochMillis = createdAt,
      updatedAtEpochMillis = createdAt
    )
  }
}

private class IntegrationFakeCameraClient(
  private val failPhotoIds: Set<String> = emptySet()
) : FujifilmCameraClient {

  override suspend fun isReachable(): Boolean = true

  override suspend fun fetchRemotePhotos(): List<RemotePhoto> = emptyList()

  override suspend fun fetchThumbnail(photoId: PhotoId): ByteArray = ByteArray(0)

  override suspend fun openPreview(photoId: PhotoId): InputStream = ByteArrayInputStream(ByteArray(0))

  override suspend fun openOriginal(photoId: PhotoId): InputStream {
    if (photoId.value in failPhotoIds) {
      throw AppException(AppError.Sdk(SdkErrorCode.IO, "mock sdk error"))
    }
    return ByteArrayInputStream(byteArrayOf(1, 2, 3))
  }
}

private class IntegrationFakeMediaStoreImageSaver : MediaStoreImageSaver {
  var beforeWrite: suspend () -> Unit = {}
  val failWriteCalls: MutableMap<Int, Throwable> = linkedMapOf()
  val deletedUris = mutableListOf<Uri>()
  val publishedUris = mutableListOf<Uri>()

  private var writeCount = 0
  private var nextId = 1

  override suspend fun createPending(request: SaveImageRequest): Uri {
    return Uri.parse("content://media/external/images/media/${nextId++}")
  }

  override suspend fun write(uri: Uri, source: InputStream, onProgress: (Int) -> Unit) {
    beforeWrite()
    writeCount += 1
    failWriteCalls[writeCount]?.let { throw it }
    source.use { input ->
      while (input.read() != -1) {
        // consume stream
      }
    }
    onProgress(100)
  }

  override suspend fun publish(uri: Uri) {
    publishedUris += uri
  }

  override suspend fun delete(uri: Uri) {
    deletedUris += uri
  }
}

private class TestNotificationFactory(
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
