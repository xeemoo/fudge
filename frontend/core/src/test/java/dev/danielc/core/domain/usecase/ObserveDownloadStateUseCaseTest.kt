package dev.danielc.core.domain.usecase

import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.media.MediaUriVerifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveDownloadStateUseCaseTest {

  @Test
  fun observe_whenDownloadedExists_hasHighestPriority() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val downloadedDao = FakeDownloadedPhotoDao()
    val useCase = ObserveDownloadStateUseCase(
      taskDao = taskDao,
      isDownloadedUseCase = IsDownloadedUseCase(downloadedDao, FakeMediaUriVerifier())
    )
    val photoId = PhotoId("photo-priority")

    taskDao.emit(
      DownloadTaskEntity(
        taskId = "task-1",
        queueId = "queue-A",
        photoId = photoId.value,
        status = DownloadStatus.DOWNLOADING,
        progressPercent = 40,
        errorCode = null,
        localUri = null,
        createdAtEpochMillis = 10L,
        updatedAtEpochMillis = 10L
      )
    )
    downloadedDao.emit(
      DownloadedPhotoEntity(
        photoId = photoId.value,
        localUri = "content://photo-priority",
        displayName = "photo-priority.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 11L
      )
    )

    val state = useCase.observe(photoId).first()

    assertEquals(DownloadButtonState.SUCCESS, state)
  }

  @Test
  fun observe_mapsTaskStatusToButtonState() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val downloadedDao = FakeDownloadedPhotoDao()
    val useCase = ObserveDownloadStateUseCase(
      taskDao = taskDao,
      isDownloadedUseCase = IsDownloadedUseCase(downloadedDao, FakeMediaUriVerifier())
    )
    val photoId = PhotoId("photo-map")

    taskDao.emit(task(photoId, DownloadStatus.QUEUED))
    assertEquals(DownloadButtonState.QUEUED, useCase.observe(photoId).first())

    taskDao.emit(task(photoId, DownloadStatus.DOWNLOADING))
    assertEquals(DownloadButtonState.DOWNLOADING, useCase.observe(photoId).first())

    taskDao.emit(task(photoId, DownloadStatus.FAILED))
    assertEquals(DownloadButtonState.FAILED, useCase.observe(photoId).first())

    taskDao.emit(task(photoId, DownloadStatus.SUCCESS))
    assertEquals(DownloadButtonState.NOT_DOWNLOADED, useCase.observe(photoId).first())
  }

  @Test
  fun observe_emitsRealtimeChangesWithoutManualRefresh() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val downloadedDao = FakeDownloadedPhotoDao()
    val useCase = ObserveDownloadStateUseCase(
      taskDao = taskDao,
      isDownloadedUseCase = IsDownloadedUseCase(downloadedDao, FakeMediaUriVerifier())
    )
    val photoId = PhotoId("photo-realtime")
    val states = mutableListOf<DownloadButtonState>()

    val job = launch {
      useCase.observe(photoId).take(4).toList(states)
    }
    advanceUntilIdle()

    taskDao.emit(task(photoId, DownloadStatus.QUEUED))
    advanceUntilIdle()

    taskDao.emit(task(photoId, DownloadStatus.DOWNLOADING))
    advanceUntilIdle()

    downloadedDao.emit(
      DownloadedPhotoEntity(
        photoId = photoId.value,
        localUri = "content://photo-realtime",
        displayName = "photo-realtime.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 100L
      )
    )
    advanceUntilIdle()
    job.join()

    assertEquals(
      listOf(
        DownloadButtonState.NOT_DOWNLOADED,
        DownloadButtonState.QUEUED,
        DownloadButtonState.DOWNLOADING,
        DownloadButtonState.SUCCESS
      ),
      states
    )
  }

  @Test
  fun observe_whenIndexedButFileMissing_fallsBackToTaskState() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val downloadedDao = FakeDownloadedPhotoDao()
    val useCase = ObserveDownloadStateUseCase(
      taskDao = taskDao,
      isDownloadedUseCase = IsDownloadedUseCase(
        downloadedDao = downloadedDao,
        verifier = object : MediaUriVerifier {
          override suspend fun exists(uriString: String): Boolean = false
        }
      )
    )
    val photoId = PhotoId("photo-missing")

    taskDao.emit(task(photoId, DownloadStatus.QUEUED))
    downloadedDao.emit(
      DownloadedPhotoEntity(
        photoId = photoId.value,
        localUri = "content://missing",
        displayName = "photo-missing.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 100L
      )
    )

    assertEquals(DownloadButtonState.QUEUED, useCase.observe(photoId).first())
  }

  private fun task(photoId: PhotoId, status: DownloadStatus): DownloadTaskEntity {
    return DownloadTaskEntity(
      taskId = "task-${photoId.value}-$status",
      queueId = "queue-A",
      photoId = photoId.value,
      status = status,
      progressPercent = when (status) {
        DownloadStatus.QUEUED -> 0
        DownloadStatus.DOWNLOADING -> 50
        DownloadStatus.SUCCESS -> 100
        DownloadStatus.FAILED -> 0
      },
      errorCode = if (status == DownloadStatus.FAILED) DownloadErrorCode.UNKNOWN else null,
      localUri = if (status == DownloadStatus.SUCCESS) "content://${photoId.value}" else null,
      createdAtEpochMillis = 1L,
      updatedAtEpochMillis = 1L
    )
  }

  private class FakeDownloadTaskDao : DownloadTaskDao {
    private val flow = MutableStateFlow<DownloadTaskEntity?>(null)

    fun emit(task: DownloadTaskEntity?) {
      flow.value = task
    }

    override fun observeByPhotoId(photoId: String): Flow<DownloadTaskEntity?> = flow

    override fun observeActive(): Flow<List<DownloadTaskEntity>> = flowOf(emptyList())

    override fun observeByQueueId(queueId: String): Flow<List<DownloadTaskEntity>> = flowOf(emptyList())

    override suspend fun insert(entity: DownloadTaskEntity) {
      flow.value = entity
    }

    override suspend fun updateStatus(
      taskId: String,
      status: DownloadStatus,
      progressPercent: Int,
      errorCode: DownloadErrorCode?,
      localUri: String?,
      updatedAtEpochMillis: Long
    ) {
      val current = flow.value ?: return
      flow.value = current.copy(
        status = status,
        progressPercent = progressPercent,
        errorCode = errorCode,
        localUri = localUri,
        updatedAtEpochMillis = updatedAtEpochMillis
      )
    }

    override suspend fun findExistingActiveTask(photoId: String): DownloadTaskEntity? = null

    override suspend fun nextQueuedTask(queueId: String): DownloadTaskEntity? = null
  }

  private class FakeDownloadedPhotoDao : DownloadedPhotoDao {
    private val flow = MutableStateFlow<DownloadedPhotoEntity?>(null)

    fun emit(entity: DownloadedPhotoEntity?) {
      flow.value = entity
    }

    override fun observe(photoId: String): Flow<DownloadedPhotoEntity?> = flow

    override suspend fun get(photoId: String): DownloadedPhotoEntity? = flow.value

    override suspend fun upsert(entity: DownloadedPhotoEntity) {
      flow.value = entity
    }
  }

  private class FakeMediaUriVerifier : MediaUriVerifier {
    override suspend fun exists(uriString: String): Boolean = true
  }
}
