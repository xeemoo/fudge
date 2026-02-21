package dev.danielc.core.work.worker

import android.net.Uri
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.analytics.DownloadFailReason
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.entity.DownloadedPhotoEntity
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
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadQueueWorkerDependencyTest {

  @Test
  fun processQueue_whenTaskSucceeds_marksSuccessAndUpsertsDownloaded() = runTest {
    val taskDao = FakeDownloadTaskDao(
      tasks = mutableListOf(
        task(taskId = "task-1", photoId = "photo-success", queueId = "queue-A")
      )
    )
    val downloadedDao = FakeDownloadedPhotoDao()
    val saver = FakeMediaStoreImageSaver()
    val analytics = RecordingAnalyticsTracker()
    val dependency = DownloadQueueWorkerDependency(
      taskDao = taskDao,
      downloadedDao = downloadedDao,
      cameraClient = FakeCameraClient(),
      saver = saver,
      analyticsTracker = analytics,
      clock = { 100L }
    )

    dependency.processQueue("queue-A")

    val finalTask = taskDao.tasks.first { it.taskId == "task-1" }
    assertEquals(DownloadStatus.SUCCESS, finalTask.status)
    assertEquals(100, finalTask.progressPercent)
    assertNotNull(finalTask.localUri)
    assertEquals("queue-A", dependency.lastQueueId)
    assertEquals(
      listOf(DownloadStatus.DOWNLOADING, DownloadStatus.SUCCESS),
      taskDao.statusHistoryByTaskId["task-1"]
    )
    assertTrue(downloadedDao.byPhotoId.containsKey("photo-success"))
    assertTrue(analytics.events.contains(AnalyticsEvent.DownloadStart))
    assertTrue(analytics.events.contains(AnalyticsEvent.DownloadSuccess))
    assertTrue(analytics.events.contains(AnalyticsEvent.QueueLengthChange(0)))
  }

  @Test
  fun processQueue_whenOneTaskFails_continuesNextTask() = runTest {
    val taskDao = FakeDownloadTaskDao(
      tasks = mutableListOf(
        task(taskId = "task-fail", photoId = "photo-fail", queueId = "queue-A", createdAt = 1L),
        task(taskId = "task-ok", photoId = "photo-ok", queueId = "queue-A", createdAt = 2L)
      )
    )
    val downloadedDao = FakeDownloadedPhotoDao()
    val saver = FakeMediaStoreImageSaver()
    val cameraClient = FakeCameraClient(
      failPhotoIds = setOf("photo-fail")
    )
    val analytics = RecordingAnalyticsTracker()
    val dependency = DownloadQueueWorkerDependency(
      taskDao = taskDao,
      downloadedDao = downloadedDao,
      cameraClient = cameraClient,
      saver = saver,
      analyticsTracker = analytics,
      clock = { 200L }
    )

    dependency.processQueue("queue-A")

    val failedTask = taskDao.tasks.first { it.taskId == "task-fail" }
    assertEquals(DownloadStatus.FAILED, failedTask.status)
    assertEquals(DownloadErrorCode.SDK_ERROR, failedTask.errorCode)
    assertEquals(
      listOf(DownloadStatus.DOWNLOADING, DownloadStatus.FAILED),
      taskDao.statusHistoryByTaskId["task-fail"]
    )
    val successTask = taskDao.tasks.first { it.taskId == "task-ok" }
    assertEquals(DownloadStatus.SUCCESS, successTask.status)
    assertEquals(
      listOf(DownloadStatus.DOWNLOADING, DownloadStatus.SUCCESS),
      taskDao.statusHistoryByTaskId["task-ok"]
    )
    assertTrue(downloadedDao.byPhotoId.containsKey("photo-ok"))
    assertTrue(analytics.events.contains(AnalyticsEvent.DownloadFail(DownloadFailReason.SDK_ERROR)))
  }

  @Test
  fun processQueue_whenStorageFull_marksFailedAndContinuesNextTask() = runTest {
    val taskDao = FakeDownloadTaskDao(
      tasks = mutableListOf(
        task(taskId = "task-storage-fail", photoId = "photo-storage-fail", queueId = "queue-A", createdAt = 1L),
        task(taskId = "task-after-storage-fail", photoId = "photo-after-storage-fail", queueId = "queue-A", createdAt = 2L)
      )
    )
    val downloadedDao = FakeDownloadedPhotoDao()
    val saver = FakeMediaStoreImageSaver(
      writeFailuresByCall = mapOf(1 to IOException("No space left on device"))
    )
    val analytics = RecordingAnalyticsTracker()
    val dependency = DownloadQueueWorkerDependency(
      taskDao = taskDao,
      downloadedDao = downloadedDao,
      cameraClient = FakeCameraClient(),
      saver = saver,
      analyticsTracker = analytics,
      clock = { 250L }
    )

    dependency.processQueue("queue-A")

    val failedTask = taskDao.tasks.first { it.taskId == "task-storage-fail" }
    assertEquals(DownloadStatus.FAILED, failedTask.status)
    assertEquals(DownloadErrorCode.STORAGE_FULL, failedTask.errorCode)
    assertEquals(
      listOf(DownloadStatus.DOWNLOADING, DownloadStatus.FAILED),
      taskDao.statusHistoryByTaskId["task-storage-fail"]
    )
    val successTask = taskDao.tasks.first { it.taskId == "task-after-storage-fail" }
    assertEquals(DownloadStatus.SUCCESS, successTask.status)
    assertTrue(downloadedDao.byPhotoId.containsKey("photo-after-storage-fail"))
    assertEquals(1, saver.deletedUris.size)
    assertTrue(analytics.events.contains(AnalyticsEvent.DownloadFail(DownloadFailReason.STORAGE_FULL)))
  }

  @Test
  fun processQueue_whenTaskAppendedDuringRun_processesAppendedTaskInSameLoop() = runTest {
    val taskDao = FakeDownloadTaskDao(
      tasks = mutableListOf(
        task(taskId = "task-1", photoId = "photo-1", queueId = "queue-A", createdAt = 1L)
      )
    )
    val downloadedDao = FakeDownloadedPhotoDao()
    val saver = FakeMediaStoreImageSaver(
      onWrite = {
        if (taskDao.tasks.none { task -> task.taskId == "task-2" }) {
          taskDao.insert(task(taskId = "task-2", photoId = "photo-2", queueId = "queue-A", createdAt = 2L))
        }
      }
    )
    val dependency = DownloadQueueWorkerDependency(
      taskDao = taskDao,
      downloadedDao = downloadedDao,
      cameraClient = FakeCameraClient(),
      saver = saver,
      clock = { 300L }
    )

    dependency.processQueue("queue-A")

    assertEquals(DownloadStatus.SUCCESS, taskDao.tasks.first { it.taskId == "task-1" }.status)
    assertEquals(DownloadStatus.SUCCESS, taskDao.tasks.first { it.taskId == "task-2" }.status)
    assertTrue(downloadedDao.byPhotoId.containsKey("photo-1"))
    assertTrue(downloadedDao.byPhotoId.containsKey("photo-2"))
  }

  private fun task(taskId: String, photoId: String, queueId: String, createdAt: Long = 10L): DownloadTaskEntity {
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

private class FakeDownloadTaskDao(
  val tasks: MutableList<DownloadTaskEntity>
) : DownloadTaskDao {
  val statusHistoryByTaskId = linkedMapOf<String, MutableList<DownloadStatus>>()

  override fun observeByPhotoId(photoId: String): Flow<DownloadTaskEntity?> = flowOf(
    tasks.filter { it.photoId == photoId }.maxByOrNull { it.createdAtEpochMillis }
  )

  override fun observeActive(): Flow<List<DownloadTaskEntity>> = flowOf(
    tasks.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING }
  )

  override fun observeByQueueId(queueId: String): Flow<List<DownloadTaskEntity>> = flowOf(
    tasks.filter { it.queueId == queueId }.sortedBy { it.createdAtEpochMillis }
  )

  override suspend fun insert(entity: DownloadTaskEntity) {
    tasks += entity
  }

  override suspend fun updateStatus(
    taskId: String,
    status: DownloadStatus,
    progressPercent: Int,
    errorCode: DownloadErrorCode?,
    localUri: String?,
    updatedAtEpochMillis: Long
  ) {
    val index = tasks.indexOfFirst { it.taskId == taskId }
    if (index < 0) return
    statusHistoryByTaskId.getOrPut(taskId) { mutableListOf() }.add(status)
    tasks[index] = tasks[index].copy(
      status = status,
      progressPercent = progressPercent,
      errorCode = errorCode,
      localUri = localUri,
      updatedAtEpochMillis = updatedAtEpochMillis
    )
  }

  override suspend fun findExistingActiveTask(photoId: String): DownloadTaskEntity? {
    return tasks
      .filter { it.photoId == photoId && (it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING) }
      .maxByOrNull { it.createdAtEpochMillis }
  }

  override suspend fun nextQueuedTask(queueId: String): DownloadTaskEntity? {
    return tasks
      .filter { it.queueId == queueId && it.status == DownloadStatus.QUEUED }
      .minByOrNull { it.createdAtEpochMillis }
  }
}

private class FakeDownloadedPhotoDao : DownloadedPhotoDao {
  val byPhotoId = linkedMapOf<String, DownloadedPhotoEntity>()

  override fun observe(photoId: String): Flow<DownloadedPhotoEntity?> = flowOf(byPhotoId[photoId])

  override suspend fun get(photoId: String): DownloadedPhotoEntity? = byPhotoId[photoId]

  override suspend fun upsert(entity: DownloadedPhotoEntity) {
    byPhotoId[entity.photoId] = entity
  }
}

private class FakeCameraClient(
  private val failPhotoIds: Set<String> = emptySet()
) : FujifilmCameraClient {
  override suspend fun isReachable(): Boolean = true
  override suspend fun fetchRemotePhotos(): List<RemotePhoto> = emptyList()
  override suspend fun fetchThumbnail(photoId: PhotoId): ByteArray = ByteArray(0)
  override suspend fun openPreview(photoId: PhotoId): InputStream = ByteArrayInputStream(ByteArray(0))

  override suspend fun openOriginal(photoId: PhotoId): InputStream {
    if (photoId.value in failPhotoIds) {
      throw AppException(AppError.Sdk(SdkErrorCode.IO, "mock sdk failure"))
    }
    return ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))
  }
}

private class FakeMediaStoreImageSaver : MediaStoreImageSaver {
  constructor(
    onWrite: suspend () -> Unit = {},
    writeFailuresByCall: Map<Int, Throwable> = emptyMap()
  ) {
    this.onWrite = onWrite
    this.writeFailuresByCall = writeFailuresByCall
  }

  private var nextId = 1
  private val onWrite: suspend () -> Unit
  private val writeFailuresByCall: Map<Int, Throwable>
  private var writeCallCount = 0
  val deletedUris = mutableListOf<Uri>()

  override suspend fun createPending(request: SaveImageRequest): Uri {
    val id = nextId++
    return Uri.parse("content://media/external/images/media/$id")
  }

  override suspend fun write(uri: Uri, source: InputStream, onProgress: (Int) -> Unit) {
    onWrite()
    writeCallCount += 1
    writeFailuresByCall[writeCallCount]?.let { throw it }
    source.use { stream ->
      while (stream.read() != -1) {
        // no-op
      }
    }
    onProgress(100)
  }

  override suspend fun publish(uri: Uri) = Unit

  override suspend fun delete(uri: Uri) {
    deletedUris += uri
  }
}

private class RecordingAnalyticsTracker : AnalyticsTracker {
  val events = mutableListOf<AnalyticsEvent>()

  override fun track(event: AnalyticsEvent) {
    events += event
  }
}
