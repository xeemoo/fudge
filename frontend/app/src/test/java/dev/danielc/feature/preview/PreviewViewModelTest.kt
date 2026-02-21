package dev.danielc.feature.preview

import dev.danielc.R
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.QueueIdProvider
import dev.danielc.core.data.SessionState
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.dao.DownloadedPhotoDao
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.PhotoRepository
import dev.danielc.core.domain.PreviewRepository
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.usecase.DownloadButtonState
import dev.danielc.core.domain.usecase.EnqueueDownloadUseCase
import dev.danielc.core.domain.usecase.EnqueueResult
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.FetchPreviewImageUseCase
import dev.danielc.core.domain.usecase.IsDownloadedUseCase
import dev.danielc.core.domain.usecase.ObserveDownloadStateUseCase
import dev.danielc.core.media.MediaUriVerifier
import dev.danielc.core.work.DownloadQueueScheduler
import dev.danielc.feature.connect.MainDispatcherRule
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.work.Operation
import com.google.common.util.concurrent.ListenableFuture
import dev.danielc.ui.UiText
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()
  private val testErrorMessageMapper: ErrorMessageMapper = TestErrorMessageMapper()

  @Test
  fun onEnter_whenFetchSuccess_updatesReadyState() = runTest {
    val expected = byteArrayOf(9, 8, 7)
    val gate = CompletableDeferred<Unit>()
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.success(ByteArrayInputStream(expected))),
          gate = gate
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = createObserveDownloadStateUseCase(),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      photoId = "photo-1"
    )

    viewModel.accept(PreviewContract.Intent.OnEnter)
    assertTrue(viewModel.state.value.isLoading)
    gate.complete(Unit)
    advanceUntilIdle()

    assertTrue(!viewModel.state.value.isLoading)
    assertEquals(null, viewModel.state.value.errorMessage)
    assertEquals(expected.toList(), viewModel.state.value.imageBytes?.toList())
    assertEquals(DownloadButtonState.NOT_DOWNLOADED, viewModel.state.value.downloadButtonState)
  }

  @Test
  fun onEnter_whenFetchFails_updatesErrorState() = runTest {
    val gate = CompletableDeferred<Unit>()
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.failure(IllegalStateException("preview timeout"))),
          gate = gate
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = createObserveDownloadStateUseCase(),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      photoId = "photo-2"
    )

    viewModel.accept(PreviewContract.Intent.OnEnter)
    assertTrue(viewModel.state.value.isLoading)
    gate.complete(Unit)
    advanceUntilIdle()

    assertTrue(!viewModel.state.value.isLoading)
    assertEquals(UiText.Dynamic("preview timeout"), viewModel.state.value.errorMessage)
    assertEquals(null, viewModel.state.value.imageBytes)
  }

  @Test
  fun onRetry_afterFailure_recoversToReadyState() = runTest {
    val expected = byteArrayOf(1, 3, 5)
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(
            Result.failure(IllegalStateException("temporary sdk error")),
            Result.success(ByteArrayInputStream(expected))
          )
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = createObserveDownloadStateUseCase(),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      photoId = "photo-3"
    )

    viewModel.accept(PreviewContract.Intent.OnEnter)
    advanceUntilIdle()
    assertEquals(UiText.Dynamic("temporary sdk error"), viewModel.state.value.errorMessage)

    viewModel.accept(PreviewContract.Intent.OnRetry)
    advanceUntilIdle()

    assertEquals(null, viewModel.state.value.errorMessage)
    assertEquals(expected.toList(), viewModel.state.value.imageBytes?.toList())
  }

  @Test
  fun init_whenPhotoMetaFound_updatesPhotoDetailState() = runTest {
    val targetPhoto = RemotePhoto(
      photoId = PhotoId("photo-meta"),
      fileName = "DSCF0123.JPG",
      takenAtEpochMillis = 1700000000000L,
      fileSizeBytes = 5_242_880L,
      mimeType = "image/jpeg"
    )
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.success(ByteArrayInputStream(byteArrayOf(1))))
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = createObserveDownloadStateUseCase(),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = FakePhotoRepository(
          photos = listOf(
            targetPhoto,
            targetPhoto.copy(photoId = PhotoId("other-photo"), fileName = "OTHER.JPG")
          )
        ),
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      photoId = "photo-meta"
    )

    advanceUntilIdle()

    assertEquals("DSCF0123.JPG", viewModel.state.value.fileName)
    assertEquals(1700000000000L, viewModel.state.value.takenAtEpochMillis)
    assertEquals(5_242_880L, viewModel.state.value.fileSizeBytes)
    assertEquals("image/jpeg", viewModel.state.value.mimeType)
  }

  @Test
  fun downloadState_updatesFromDatabaseFlow() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val downloadedDao = FakeDownloadedPhotoDao()
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.success(ByteArrayInputStream(byteArrayOf(1))))
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = ObserveDownloadStateUseCase(
        taskDao = taskDao,
        isDownloadedUseCase = IsDownloadedUseCase(downloadedDao, AlwaysExistsVerifier())
      ),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      photoId = "photo-4"
    )

    taskDao.emit(status = DownloadStatus.QUEUED, photoId = "photo-4")
    advanceUntilIdle()
    assertEquals(DownloadButtonState.QUEUED, viewModel.state.value.downloadButtonState)

    taskDao.emit(status = DownloadStatus.DOWNLOADING, photoId = "photo-4")
    advanceUntilIdle()
    assertEquals(DownloadButtonState.DOWNLOADING, viewModel.state.value.downloadButtonState)

    downloadedDao.emit(
      DownloadedPhotoEntity(
        photoId = "photo-4",
        localUri = "content://photo-4",
        displayName = "photo-4.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 123L
      )
    )
    advanceUntilIdle()
    assertEquals(DownloadButtonState.SUCCESS, viewModel.state.value.downloadButtonState)
  }

  @Test
  fun onClickDownload_whenEnqueued_emitsQueueToast() = runTest {
    val analytics = RecordingAnalyticsTracker()
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.success(ByteArrayInputStream(byteArrayOf(1))))
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = createObserveDownloadStateUseCase(),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      analyticsTracker = analytics,
      photoId = "photo-5"
    )
    val effect = async { viewModel.effect.first() }
    runCurrent()

    viewModel.accept(PreviewContract.Intent.OnClickDownload)
    advanceUntilIdle()

    assertEquals(
      PreviewContract.Effect.ShowToast(UiText.Res(R.string.preview_toast_enqueued)),
      effect.await()
    )
    assertTrue(analytics.events.contains(AnalyticsEvent.DownloadClick))
  }

  @Test
  fun onClickDownload_usesResolvedMetaForEnqueuePayload() = runTest {
    var capturedPhoto: RemotePhoto? = null
    val targetPhoto = RemotePhoto(
      photoId = PhotoId("photo-meta-enqueue"),
      fileName = "DSCF9999.JPG",
      takenAtEpochMillis = 1711111111000L,
      fileSizeBytes = 9_437_184L,
      mimeType = "image/jpeg"
    )
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.success(ByteArrayInputStream(byteArrayOf(1))))
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = createObserveDownloadStateUseCase(),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = FakePhotoRepository(photos = listOf(targetPhoto)),
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      enqueuePhotoAction = { photo ->
        capturedPhoto = photo
        EnqueueResult.Enqueued
      },
      photoId = "photo-meta-enqueue"
    )

    advanceUntilIdle()
    viewModel.accept(PreviewContract.Intent.OnClickDownload)
    advanceUntilIdle()

    assertEquals(targetPhoto, capturedPhoto)
  }

  @Test
  fun onClickDownload_whenAlreadyDownloaded_emitsDownloadedToast() = runTest {
    val downloadedDao = FakeDownloadedPhotoDao().apply {
      downloadedById["photo-6"] = DownloadedPhotoEntity(
        photoId = "photo-6",
        localUri = "content://photo-6",
        displayName = "photo-6.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 1L
      )
    }
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.success(ByteArrayInputStream(byteArrayOf(1))))
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = createObserveDownloadStateUseCase(),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(downloadedDao = downloadedDao),
      errorMessageMapper = testErrorMessageMapper,
      photoId = "photo-6"
    )
    val effect = async { viewModel.effect.first() }
    runCurrent()

    viewModel.accept(PreviewContract.Intent.OnClickDownload)
    advanceUntilIdle()

    assertEquals(
      PreviewContract.Effect.ShowToast(UiText.Res(R.string.preview_toast_already_downloaded)),
      effect.await()
    )
  }

  @Test
  fun downloadFailed_withStorageFull_emitsRecoverableToast() = runTest {
    val taskDao = FakeDownloadTaskDao()
    val viewModel = PreviewViewModel(
      fetchPreviewImageUseCase = FetchPreviewImageUseCase(
        repo = FakePreviewRepository(
          results = mutableListOf(Result.success(ByteArrayInputStream(byteArrayOf(1))))
        ),
        session = FakeCameraSessionManager(SessionState.Ready),
        ioDispatcher = StandardTestDispatcher(testScheduler)
      ),
      observeDownloadStateUseCase = ObserveDownloadStateUseCase(
        taskDao = taskDao,
        isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier())
      ),
      enqueueDownloadUseCase = createEnqueueDownloadUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      photoId = "photo-storage"
    )
    val effect = async { viewModel.effect.first() }
    runCurrent()

    taskDao.emit(
      status = DownloadStatus.FAILED,
      photoId = "photo-storage",
      errorCode = DownloadErrorCode.STORAGE_FULL
    )
    advanceUntilIdle()

    assertEquals(
      PreviewContract.Effect.ShowToast(
        UiText.Dynamic(testErrorMessageMapper.toDownloadFailMessage(DownloadErrorCode.STORAGE_FULL))
      ),
      effect.await()
    )
    assertEquals(DownloadButtonState.FAILED, viewModel.state.value.downloadButtonState)
  }

  private fun createObserveDownloadStateUseCase(): ObserveDownloadStateUseCase {
    return ObserveDownloadStateUseCase(
      taskDao = FakeDownloadTaskDao(),
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier())
    )
  }

  private fun createEnqueueDownloadUseCase(
    taskDao: FakeDownloadTaskDao = FakeDownloadTaskDao(),
    downloadedDao: FakeDownloadedPhotoDao = FakeDownloadedPhotoDao(),
    mediaUriVerifier: MediaUriVerifier = AlwaysExistsVerifier()
  ): EnqueueDownloadUseCase {
    return EnqueueDownloadUseCase(
      taskDao = taskDao,
      downloadedDao = downloadedDao,
      mediaUriVerifier = mediaUriVerifier,
      queueIdProvider = object : QueueIdProvider {
        override suspend fun getOrCreateQueueId(): String = "queue-A"
      },
      scheduler = object : DownloadQueueScheduler {
        override fun kick(queueId: String): Operation = successfulOperation()
      },
      clock = { 1L },
      taskIdFactory = { "task-new" }
    )
  }

  private class FakeCameraSessionManager(
    private val assertReadyResult: SessionState
  ) : CameraSessionManager {
    override val sessionState: Flow<SessionState> = emptyFlow()
    override suspend fun assertReady(): SessionState = assertReadyResult
  }

  private class FakePreviewRepository(
    private val results: MutableList<Result<InputStream>>,
    private val gate: CompletableDeferred<Unit>? = null
  ) : PreviewRepository {
    override suspend fun openPreview(photoId: PhotoId): InputStream {
      gate?.await()
      val result = if (results.isNotEmpty()) {
        results.removeAt(0)
      } else {
        Result.failure(IllegalStateException("no preview stream"))
      }
      return result.getOrThrow()
    }
  }

  private class FakePhotoRepository(
    private val photos: List<RemotePhoto>
  ) : PhotoRepository {
    override suspend fun fetchRemotePhotos(): List<RemotePhoto> = photos
  }

  private class FakeDownloadTaskDao : DownloadTaskDao {
    private val taskFlow = MutableStateFlow<DownloadTaskEntity?>(null)
    var existingActiveTask: DownloadTaskEntity? = null
    var insertError: Throwable? = null

    fun emit(status: DownloadStatus, photoId: String, errorCode: DownloadErrorCode? = null) {
      taskFlow.value = DownloadTaskEntity(
        taskId = "task-$photoId-$status",
        queueId = "queue-A",
        photoId = photoId,
        status = status,
        progressPercent = if (status == DownloadStatus.DOWNLOADING) 50 else 0,
        errorCode = if (status == DownloadStatus.FAILED) (errorCode ?: DownloadErrorCode.UNKNOWN) else null,
        localUri = null,
        createdAtEpochMillis = 1L,
        updatedAtEpochMillis = 1L
      )
    }

    override fun observeByPhotoId(photoId: String): Flow<DownloadTaskEntity?> = taskFlow
    override fun observeActive(): Flow<List<DownloadTaskEntity>> = flowOf(emptyList())
    override fun observeByQueueId(queueId: String): Flow<List<DownloadTaskEntity>> = flowOf(emptyList())
    override suspend fun insert(entity: DownloadTaskEntity) {
      insertError?.let { throw it }
      taskFlow.value = entity
    }
    override suspend fun updateStatus(
      taskId: String,
      status: DownloadStatus,
      progressPercent: Int,
      errorCode: DownloadErrorCode?,
      localUri: String?,
      updatedAtEpochMillis: Long
    ) {}
    override suspend fun findExistingActiveTask(photoId: String): DownloadTaskEntity? {
      return existingActiveTask?.takeIf { it.photoId == photoId }
    }
    override suspend fun nextQueuedTask(queueId: String): DownloadTaskEntity? = null
  }

  private class FakeDownloadedPhotoDao : DownloadedPhotoDao {
    private val downloadedFlow = MutableStateFlow<DownloadedPhotoEntity?>(null)
    val downloadedById = linkedMapOf<String, DownloadedPhotoEntity>()

    fun emit(entity: DownloadedPhotoEntity?) {
      downloadedFlow.value = entity
      if (entity != null) {
        downloadedById[entity.photoId] = entity
      }
    }

    override fun observe(photoId: String): Flow<DownloadedPhotoEntity?> = downloadedFlow
    override suspend fun get(photoId: String): DownloadedPhotoEntity? {
      return downloadedById[photoId] ?: downloadedFlow.value?.takeIf { it.photoId == photoId }
    }
    override suspend fun upsert(entity: DownloadedPhotoEntity) {
      downloadedById[entity.photoId] = entity
      downloadedFlow.value = entity
    }
  }

  private class AlwaysExistsVerifier : MediaUriVerifier {
    override suspend fun exists(uriString: String): Boolean = true
  }

  private class TestErrorMessageMapper : ErrorMessageMapper {
    override fun toUserMessage(error: AppError): String = "mapped-user-error"
    override fun toDownloadFailMessage(code: DownloadErrorCode): String {
      return when (code) {
        DownloadErrorCode.STORAGE_FULL -> "mapped-storage-full"
        else -> "mapped-download-error"
      }
    }
  }

  private class RecordingAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
      events += event
    }
  }
}

private fun successfulOperation(): Operation {
  val stateData = MutableLiveData<Operation.State>(Operation.SUCCESS)
  return object : Operation {
    override fun getState(): LiveData<Operation.State> = stateData
    override fun getResult(): ListenableFuture<Operation.State.SUCCESS> {
      return ImmediateSuccessFuture(Operation.SUCCESS)
    }
  }
}

private class ImmediateSuccessFuture(
  private val value: Operation.State.SUCCESS
) : ListenableFuture<Operation.State.SUCCESS> {
  override fun addListener(listener: Runnable, executor: Executor) {
    executor.execute(listener)
  }

  override fun cancel(mayInterruptIfRunning: Boolean): Boolean = false

  override fun isCancelled(): Boolean = false

  override fun isDone(): Boolean = true

  override fun get(): Operation.State.SUCCESS = value

  override fun get(timeout: Long, unit: TimeUnit): Operation.State.SUCCESS = value
}
