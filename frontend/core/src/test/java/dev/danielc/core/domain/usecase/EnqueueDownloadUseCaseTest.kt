package dev.danielc.core.domain.usecase

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.data.QueueIdProvider
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.media.MediaUriVerifier
import dev.danielc.core.work.DownloadQueueScheduler
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnqueueDownloadUseCaseTest {

  @Test
  fun invoke_whenAlreadyDownloaded_returnsAlreadyDownloadedAndSkipsEnqueue() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val downloadedDao = FakeDownloadedPhotoDao().apply {
      downloaded["photo-downloaded"] = DownloadedPhotoEntity(
        photoId = "photo-downloaded",
        localUri = "content://photo-downloaded",
        displayName = "photo-downloaded.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 1L
      )
    }
    val scheduler = FakeDownloadQueueScheduler()
    val useCase = EnqueueDownloadUseCase(
      taskDao = taskDao,
      downloadedDao = downloadedDao,
      mediaUriVerifier = FakeMediaUriVerifier(existingUris = setOf("content://photo-downloaded")),
      queueIdProvider = FakeQueueIdProvider("queue-main"),
      scheduler = scheduler,
      clock = { 123L },
      taskIdFactory = { "task-1" }
    )

    val result = useCase(photo("photo-downloaded"))

    assertEquals(EnqueueResult.AlreadyDownloaded, result)
    assertTrue(taskDao.inserted.isEmpty())
    assertTrue(scheduler.kickedQueueIds.isEmpty())
  }

  @Test
  fun invoke_whenAlreadyInQueue_returnsAlreadyInQueueAndSkipsEnqueue() = runTest {
    val taskDao = FakeDownloadTaskDao().apply {
      existingActive = DownloadTaskEntity(
        taskId = "task-existing",
        queueId = "queue-main",
        photoId = "photo-queued",
        status = DownloadStatus.QUEUED,
        progressPercent = 0,
        errorCode = null,
        localUri = null,
        createdAtEpochMillis = 10L,
        updatedAtEpochMillis = 10L
      )
    }
    val scheduler = FakeDownloadQueueScheduler()
    val useCase = EnqueueDownloadUseCase(
      taskDao = taskDao,
      downloadedDao = FakeDownloadedPhotoDao(),
      mediaUriVerifier = FakeMediaUriVerifier(existingUris = emptySet()),
      queueIdProvider = FakeQueueIdProvider("queue-main"),
      scheduler = scheduler,
      clock = { 123L },
      taskIdFactory = { "task-1" }
    )

    val result = useCase(photo("photo-queued"))

    assertEquals(EnqueueResult.AlreadyInQueue, result)
    assertTrue(taskDao.inserted.isEmpty())
    assertTrue(scheduler.kickedQueueIds.isEmpty())
  }

  @Test
  fun invoke_whenNewPhoto_insertsQueuedTaskAndKicksWorker() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val scheduler = FakeDownloadQueueScheduler()
    val analytics = RecordingAnalyticsTracker()
    val useCase = EnqueueDownloadUseCase(
      taskDao = taskDao,
      downloadedDao = FakeDownloadedPhotoDao(),
      mediaUriVerifier = FakeMediaUriVerifier(existingUris = emptySet()),
      queueIdProvider = FakeQueueIdProvider("queue-main"),
      scheduler = scheduler,
      analyticsTracker = analytics,
      clock = { 999L },
      taskIdFactory = { "task-new" }
    )

    val result = useCase(photo("photo-new"))

    assertEquals(EnqueueResult.Enqueued, result)
    assertEquals(1, taskDao.inserted.size)
    assertEquals(
      DownloadTaskEntity(
        taskId = "task-new",
        queueId = "queue-main",
        photoId = "photo-new",
        status = DownloadStatus.QUEUED,
        progressPercent = 0,
        errorCode = null,
        localUri = null,
        createdAtEpochMillis = 999L,
        updatedAtEpochMillis = 999L
      ),
      taskDao.inserted.single()
    )
    assertEquals(listOf("queue-main"), scheduler.kickedQueueIds)
    assertTrue(analytics.events.contains(AnalyticsEvent.DownloadEnqueueSuccess))
    assertTrue(analytics.events.contains(AnalyticsEvent.QueueLengthChange(1)))
  }

  @Test
  fun invoke_whenInsertThrows_returnsFailed() = runTest {
    val taskDao = FakeDownloadTaskDao().apply {
      insertError = IllegalStateException("db insert failed")
    }
    val scheduler = FakeDownloadQueueScheduler()
    val useCase = EnqueueDownloadUseCase(
      taskDao = taskDao,
      downloadedDao = FakeDownloadedPhotoDao(),
      mediaUriVerifier = FakeMediaUriVerifier(existingUris = emptySet()),
      queueIdProvider = FakeQueueIdProvider("queue-main"),
      scheduler = scheduler
    )

    val result = useCase(photo("photo-failed"))

    assertTrue(result is EnqueueResult.Failed)
    assertTrue((result as EnqueueResult.Failed).message.contains("db insert failed"))
    assertTrue(scheduler.kickedQueueIds.isEmpty())
  }

  @Test
  fun invoke_whenIndexExistsButFileMissing_stillEnqueues() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val downloadedDao = FakeDownloadedPhotoDao().apply {
      downloaded["photo-stale"] = DownloadedPhotoEntity(
        photoId = "photo-stale",
        localUri = "content://missing/photo-stale",
        displayName = "photo-stale.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 1L
      )
    }
    val scheduler = FakeDownloadQueueScheduler()
    val useCase = EnqueueDownloadUseCase(
      taskDao = taskDao,
      downloadedDao = downloadedDao,
      mediaUriVerifier = FakeMediaUriVerifier(existingUris = emptySet()),
      queueIdProvider = FakeQueueIdProvider("queue-main"),
      scheduler = scheduler,
      clock = { 777L },
      taskIdFactory = { "task-stale" }
    )

    val result = useCase(photo("photo-stale"))

    assertEquals(EnqueueResult.Enqueued, result)
    assertEquals(1, taskDao.inserted.size)
    assertEquals(listOf("queue-main"), scheduler.kickedQueueIds)
  }

  @Test
  fun invoke_whenConcurrentRequests_onlyOneTaskIsInserted() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val scheduler = FakeDownloadQueueScheduler()
    val useCase = EnqueueDownloadUseCase(
      taskDao = taskDao,
      downloadedDao = FakeDownloadedPhotoDao(),
      mediaUriVerifier = FakeMediaUriVerifier(existingUris = emptySet()),
      queueIdProvider = FakeQueueIdProvider("queue-main"),
      scheduler = scheduler,
      clock = { 555L },
      taskIdFactory = { "task-concurrent-${taskDao.inserted.size}" }
    )

    val results = awaitAll(
      async { useCase(photo("photo-concurrent")) },
      async { useCase(photo("photo-concurrent")) }
    )

    assertEquals(1, results.count { it == EnqueueResult.Enqueued })
    assertEquals(1, results.count { it == EnqueueResult.AlreadyInQueue })
    assertEquals(1, taskDao.inserted.size)
    assertEquals(listOf("queue-main"), scheduler.kickedQueueIds)
  }

  private fun photo(photoId: String): RemotePhoto {
    return RemotePhoto(
      photoId = PhotoId(photoId),
      fileName = "$photoId.jpg",
      takenAtEpochMillis = null,
      fileSizeBytes = null,
      mimeType = "image/jpeg"
    )
  }

  private class FakeQueueIdProvider(
    private val queueId: String
  ) : QueueIdProvider {
    override suspend fun getOrCreateQueueId(): String = queueId
  }

  private class FakeDownloadQueueScheduler : DownloadQueueScheduler {
    val kickedQueueIds = mutableListOf<String>()

    override fun kick(queueId: String): Operation {
      kickedQueueIds += queueId
      return successfulOperation()
    }
  }

  private class FakeDownloadTaskDao : DownloadTaskDao {
    val inserted = mutableListOf<DownloadTaskEntity>()
    var existingActive: DownloadTaskEntity? = null
    var insertError: Throwable? = null

    override fun observeByPhotoId(photoId: String): Flow<DownloadTaskEntity?> = flowOf(null)

    override fun observeActive(): Flow<List<DownloadTaskEntity>> = flowOf(emptyList())

    override fun observeByQueueId(queueId: String): Flow<List<DownloadTaskEntity>> {
      return flowOf(inserted.filter { it.queueId == queueId }.sortedBy { it.createdAtEpochMillis })
    }

    override suspend fun insert(entity: DownloadTaskEntity) {
      insertError?.let { throw it }
      inserted += entity
    }

    override suspend fun updateStatus(
      taskId: String,
      status: DownloadStatus,
      progressPercent: Int,
      errorCode: DownloadErrorCode?,
      localUri: String?,
      updatedAtEpochMillis: Long
    ) = Unit

    override suspend fun findExistingActiveTask(photoId: String): DownloadTaskEntity? {
      return existingActive?.takeIf { it.photoId == photoId }
        ?: inserted
          .filter { it.photoId == photoId && it.status in setOf(DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING) }
          .maxByOrNull { it.createdAtEpochMillis }
    }

    override suspend fun nextQueuedTask(queueId: String): DownloadTaskEntity? = null
  }

  private class RecordingAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
      events += event
    }
  }

  private class FakeDownloadedPhotoDao : DownloadedPhotoDao {
    val downloaded = linkedMapOf<String, DownloadedPhotoEntity>()

    override fun observe(photoId: String): Flow<DownloadedPhotoEntity?> = flowOf(null)

    override suspend fun get(photoId: String): DownloadedPhotoEntity? = downloaded[photoId]

    override suspend fun upsert(entity: DownloadedPhotoEntity) {
      downloaded[entity.photoId] = entity
    }
  }

  private class FakeMediaUriVerifier(
    private val existingUris: Set<String>
  ) : MediaUriVerifier {
    override suspend fun exists(uriString: String): Boolean = uriString in existingUris
  }
}

private fun successfulOperation(): Operation {
  val stateData = MutableLiveData<Operation.State>(Operation.SUCCESS)
  val resultFuture = SettableFuture.create<Operation.State.SUCCESS>().apply {
    set(Operation.SUCCESS)
  }
  return object : Operation {
    override fun getState(): LiveData<Operation.State> = stateData
    override fun getResult(): ListenableFuture<Operation.State.SUCCESS> = resultFuture
  }
}
