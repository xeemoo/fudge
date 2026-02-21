package dev.danielc.feature.photolist

import dev.danielc.R
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.QueueIdProvider
import dev.danielc.core.data.SessionState
import dev.danielc.core.data.SessionNotReadyCode
import dev.danielc.core.db.dao.DownloadTaskDao
import dev.danielc.core.db.model.DownloadErrorCode
import dev.danielc.core.db.model.DownloadStatus
import dev.danielc.core.db.entity.DownloadTaskEntity
import dev.danielc.core.db.entity.DownloadedPhotoEntity
import dev.danielc.core.domain.AppError
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.PhotoRepository
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.WifiErrorCode
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.IsDownloadedUseCase
import dev.danielc.core.domain.usecase.ObserveQueuePhotoStatusUseCase
import dev.danielc.core.domain.usecase.ObserveQueueStatsUseCase
import dev.danielc.core.media.MediaUriVerifier
import dev.danielc.core.wifi.WifiConnectionMonitor
import dev.danielc.feature.connect.MainDispatcherRule
import dev.danielc.ui.UiText
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PhotoListViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()
  private val testErrorMessageMapper: ErrorMessageMapper = TestErrorMessageMapper()

  @Test
  fun onEnter_whenFetchSuccess_updatesNormalStateWithDownloadedBadge() = runTest {
    val analytics = RecordingAnalyticsTracker()
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-1"),
              fileName = "DSCF0001.JPG",
              takenAtEpochMillis = 1000L,
              fileSizeBytes = 2048L,
              mimeType = "image/jpeg"
            ),
            RemotePhoto(
              photoId = PhotoId("photo-2"),
              fileName = "DSCF0002.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = 4096L,
              mimeType = "image/jpeg"
            )
          )
        )
      )
    )
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = sessionManager
    )
    val downloadedDao = FakeDownloadedPhotoDao(downloadedIds = setOf("photo-1"))
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = useCase,
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(downloadedDao, AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      analyticsTracker = analytics
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(1, repository.fetchCount)
    assertTrue(!viewModel.state.value.isLoading)
    assertEquals(null, viewModel.state.value.errorMessage)
    assertEquals(2, viewModel.state.value.items.size)
    assertEquals(UiText.Res(R.string.photo_list_item_name, "DSCF0001.JPG"), viewModel.state.value.items[0].fileName)
    assertEquals(
      UiText.Res(R.string.photo_list_item_taken_at, formatTakenAtForTest(1000L)),
      viewModel.state.value.items[0].takenAt
    )
    assertEquals(UiText.Res(R.string.photo_list_item_size, "2.0 KB"), viewModel.state.value.items[0].fileSize)
    assertEquals(PhotoListContract.MediaTypeUi.IMAGE, viewModel.state.value.items[0].mediaType)
    assertEquals(true, viewModel.state.value.items[0].isDownloaded)
    assertEquals(UiText.Res(R.string.photo_list_item_name, "DSCF0002.JPG"), viewModel.state.value.items[1].fileName)
    assertEquals(UiText.Res(R.string.photo_list_item_taken_at_unknown), viewModel.state.value.items[1].takenAt)
    assertEquals(UiText.Res(R.string.photo_list_item_size, "4.0 KB"), viewModel.state.value.items[1].fileSize)
    assertEquals(false, viewModel.state.value.items[1].isDownloaded)
    assertTrue(analytics.events.contains(AnalyticsEvent.PhotoListRequest))
    assertTrue(analytics.events.contains(AnalyticsEvent.PhotoListSuccess))
  }

  @Test
  fun onEnter_whenAlreadyHasItems_doesNotRefetch() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-1"),
              fileName = "DSCF0001.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            )
          )
        ),
        Result.failure(IllegalStateException("should not be called"))
      )
    )
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = sessionManager
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = useCase,
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()
    assertEquals(1, repository.fetchCount)
    assertEquals(1, viewModel.state.value.items.size)

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(1, repository.fetchCount)
    assertEquals(null, viewModel.state.value.errorMessage)
    assertEquals(1, viewModel.state.value.items.size)
  }

  @Test
  fun onToggleLayoutMode_switchesUiModeWithoutRefetch() = runTest {
    val repository = FakePhotoRepository()
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = repository,
        session = sessionManager
      ),
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    assertEquals(PhotoListContract.LayoutMode.LIST, viewModel.state.value.layoutMode)
    assertEquals(0, repository.fetchCount)

    viewModel.accept(PhotoListContract.Intent.OnToggleLayoutMode)
    advanceUntilIdle()

    assertEquals(PhotoListContract.LayoutMode.GRID_4, viewModel.state.value.layoutMode)
    assertEquals(0, repository.fetchCount)

    viewModel.accept(PhotoListContract.Intent.OnToggleLayoutMode)
    advanceUntilIdle()

    assertEquals(PhotoListContract.LayoutMode.LIST, viewModel.state.value.layoutMode)
    assertEquals(0, repository.fetchCount)
  }

  @Test
  fun onEnter_afterSessionReconnect_refetches() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-1"),
              fileName = "DSCF0001.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            )
          )
        ),
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-1"),
              fileName = "DSCF0001.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            ),
            RemotePhoto(
              photoId = PhotoId("photo-2"),
              fileName = "DSCF0002.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            )
          )
        )
      )
    )
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = sessionManager
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = useCase,
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()
    assertEquals(1, repository.fetchCount)
    assertEquals(1, viewModel.state.value.items.size)

    sessionManager.emit(SessionState.NotReady(code = SessionNotReadyCode.WIFI_DISCONNECTED))
    advanceUntilIdle()
    sessionManager.emit(SessionState.Ready)
    advanceUntilIdle()

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(2, repository.fetchCount)
    assertEquals(2, viewModel.state.value.items.size)
  }

  @Test
  fun onEnter_whenSessionNotReady_showsErrorAndDoesNotCallRepository() = runTest {
    val repository = FakePhotoRepository()
    val sessionManager = FakeCameraSessionManager(
      SessionState.NotReady(code = SessionNotReadyCode.WIFI_DISCONNECTED)
    )
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = sessionManager
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = useCase,
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(0, repository.fetchCount)
    assertEquals(
      UiText.Dynamic(
        testErrorMessageMapper.toUserMessage(
          AppError.Wifi(WifiErrorCode.DISCONNECTED)
        )
      ),
      viewModel.state.value.errorMessage
    )
    assertTrue(viewModel.state.value.items.isEmpty())
  }

  @Test
  fun onRetry_afterFailure_recoversToSuccessState() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.failure(IllegalStateException("sdk timeout")),
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-1"),
              fileName = "DSCF0001.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = null
            )
          )
        )
      )
    )
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = sessionManager
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = useCase,
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()
    assertEquals(UiText.Dynamic("sdk timeout"), viewModel.state.value.errorMessage)

    viewModel.accept(PhotoListContract.Intent.OnRetry)
    advanceUntilIdle()

    assertEquals(2, repository.fetchCount)
    assertEquals(null, viewModel.state.value.errorMessage)
    assertEquals(1, viewModel.state.value.items.size)
  }

  @Test
  fun onClickPhoto_emitsNavigateToPreviewEffect() = runTest {
    val analytics = RecordingAnalyticsTracker()
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-9"),
              fileName = "DSCF0009.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = null
            )
          )
        )
      )
    )
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = sessionManager
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = useCase,
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      analyticsTracker = analytics
    )
    val effects = mutableListOf<PhotoListContract.Effect>()
    val effectJob = launch(start = CoroutineStart.UNDISPATCHED) {
      viewModel.effect.collect { effect ->
        effects += effect
      }
    }

    viewModel.accept(PhotoListContract.Intent.OnClickPhoto("photo-9"))
    advanceUntilIdle()
    effectJob.cancel()

    assertEquals(
      listOf(PhotoListContract.Effect.NavigateToPreview("photo-9")),
      effects
    )
    assertTrue(analytics.events.contains(AnalyticsEvent.PhotoItemClick))
  }

  @Test
  fun onEnter_whenIndexedButFileMissing_doesNotShowDownloadedBadge() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-x"),
              fileName = "DSCF0099.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            )
          )
        )
      )
    )
    val sessionManager = FakeCameraSessionManager(SessionState.Ready)
    val useCase = FetchPhotoListUseCase(
      repo = repository,
      session = sessionManager
    )
    val downloadedDao = FakeDownloadedPhotoDao(downloadedIds = setOf("photo-x"))
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = useCase,
      cameraSessionManager = sessionManager,
      isDownloadedUseCase = IsDownloadedUseCase(downloadedDao, NeverExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(false, viewModel.state.value.items.single().isDownloaded)
  }

  @Test
  fun queueStatsAndBadge_areUpdatedFromQueueFlows() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-a"),
              fileName = "A.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            ),
            RemotePhoto(
              photoId = PhotoId("photo-b"),
              fileName = "B.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            )
          )
        )
      )
    )
    val taskDao = FakeDownloadTaskDao(
      tasks = listOf(
        DownloadTaskEntity(
          taskId = "t1",
          queueId = "queue-A",
          photoId = "photo-a",
          status = DownloadStatus.DOWNLOADING,
          progressPercent = 50,
          errorCode = null,
          localUri = null,
          createdAtEpochMillis = 1L,
          updatedAtEpochMillis = 1L
        ),
        DownloadTaskEntity(
          taskId = "t2",
          queueId = "queue-A",
          photoId = "photo-b",
          status = DownloadStatus.QUEUED,
          progressPercent = 0,
          errorCode = null,
          localUri = null,
          createdAtEpochMillis = 2L,
          updatedAtEpochMillis = 2L
        ),
        DownloadTaskEntity(
          taskId = "t3",
          queueId = "queue-A",
          photoId = "photo-c",
          status = DownloadStatus.SUCCESS,
          progressPercent = 100,
          errorCode = null,
          localUri = "content://ok",
          createdAtEpochMillis = 3L,
          updatedAtEpochMillis = 3L
        )
      )
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = repository,
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), NeverExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(taskDao),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(taskDao),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(PhotoListContract.QueueBarUi(done = 1, total = 3, running = true), viewModel.state.value.queueBar)
    assertEquals(PhotoListContract.QueueBadgeUi.DOWNLOADING, viewModel.state.value.items[0].queueBadge)
    assertEquals(PhotoListContract.QueueBadgeUi.QUEUED, viewModel.state.value.items[1].queueBadge)
  }

  @Test
  fun downloadedState_updatesAfterLoad_withoutManualRefresh() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("photo-live"),
              fileName = "LIVE.JPG",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            )
          )
        )
      )
    )
    val downloadedDao = FakeDownloadedPhotoDao()
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = repository,
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      isDownloadedUseCase = IsDownloadedUseCase(downloadedDao, AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()
    assertEquals(false, viewModel.state.value.items.single().isDownloaded)

    downloadedDao.upsert(
      DownloadedPhotoEntity(
        photoId = "photo-live",
        localUri = "content://local/photo-live",
        displayName = "photo-live.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 10L
      )
    )
    advanceUntilIdle()

    assertEquals(true, viewModel.state.value.items.single().isDownloaded)
  }

  @Test
  fun onLoadMore_appendsNextPageAndStopsAtLastPage() = runTest {
    val pagedPhotos = (1..35).map { index ->
      RemotePhoto(
        photoId = PhotoId("photo-$index"),
        fileName = "DSCF${index.toString().padStart(4, '0')}.JPG",
        takenAtEpochMillis = null,
        fileSizeBytes = null,
        mimeType = "image/jpeg"
      )
    }
    val repository = FakePhotoRepository(pagedDataset = pagedPhotos)
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = repository,
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), NeverExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(1, repository.fetchPageCount)
    assertEquals(20, viewModel.state.value.items.size)
    assertTrue(viewModel.state.value.hasMore)

    viewModel.accept(PhotoListContract.Intent.OnLoadMore)
    advanceUntilIdle()

    assertEquals(2, repository.fetchPageCount)
    assertEquals(35, viewModel.state.value.items.size)
    assertTrue(!viewModel.state.value.hasMore)
    assertEquals("photo-35", viewModel.state.value.items.last().photoId)
  }

  @Test
  fun cameraName_mapsSsid_nullAndBlankFallbackToNull() = runTest {
    val ssidFlow = MutableStateFlow<String?>(null)
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = FakePhotoRepository(),
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), AlwaysExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper,
      wifiConnectionMonitor = FakeWifiConnectionMonitor(ssidFlow)
    )

    advanceUntilIdle()
    assertEquals(null, viewModel.state.value.cameraName)

    ssidFlow.value = "  "
    advanceUntilIdle()
    assertEquals(null, viewModel.state.value.cameraName)

    ssidFlow.value = " FUJIFILM-XT5 "
    advanceUntilIdle()
    assertEquals("FUJIFILM-XT5", viewModel.state.value.cameraName)
  }

  @Test
  fun onEnter_mediaType_prefersMimeThenFallsBackToFileExtension() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("p-image"),
              fileName = "unknown.bin",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = "image/jpeg"
            ),
            RemotePhoto(
              photoId = PhotoId("p-video"),
              fileName = "movie.MP4",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = null
            ),
            RemotePhoto(
              photoId = PhotoId("p-unknown"),
              fileName = "file.abc",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = null
            )
          )
        )
      )
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = repository,
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), NeverExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(
      PhotoListContract.MediaTypeUi.IMAGE,
      viewModel.state.value.items[0].mediaType
    )
    assertEquals(
      PhotoListContract.MediaTypeUi.VIDEO,
      viewModel.state.value.items[1].mediaType
    )
    assertEquals(
      PhotoListContract.MediaTypeUi.UNKNOWN,
      viewModel.state.value.items[2].mediaType
    )
  }

  @Test
  fun onEnter_whenFieldsMissing_usesPlaceholderTexts() = runTest {
    val repository = FakePhotoRepository(
      results = mutableListOf(
        Result.success(
          listOf(
            RemotePhoto(
              photoId = PhotoId("p-missing"),
              fileName = "",
              takenAtEpochMillis = null,
              fileSizeBytes = null,
              mimeType = null
            )
          )
        )
      )
    )
    val viewModel = PhotoListViewModel(
      fetchPhotoListUseCase = FetchPhotoListUseCase(
        repo = repository,
        session = FakeCameraSessionManager(SessionState.Ready)
      ),
      cameraSessionManager = FakeCameraSessionManager(SessionState.Ready),
      isDownloadedUseCase = IsDownloadedUseCase(FakeDownloadedPhotoDao(), NeverExistsVerifier()),
      observeQueueStatsUseCase = createObserveQueueStatsUseCase(),
      observeQueuePhotoStatusUseCase = createObserveQueuePhotoStatusUseCase(),
      errorMessageMapper = testErrorMessageMapper
    )

    viewModel.accept(PhotoListContract.Intent.OnEnter)
    advanceUntilIdle()

    assertEquals(UiText.Res(R.string.photo_list_item_name_unknown), viewModel.state.value.items[0].fileName)
    assertEquals(UiText.Res(R.string.photo_list_item_taken_at_unknown), viewModel.state.value.items[0].takenAt)
    assertEquals(UiText.Res(R.string.photo_list_item_size_unknown), viewModel.state.value.items[0].fileSize)
    assertEquals(PhotoListContract.MediaTypeUi.UNKNOWN, viewModel.state.value.items[0].mediaType)
  }

  private fun createObserveQueueStatsUseCase(
    taskDao: FakeDownloadTaskDao = FakeDownloadTaskDao(emptyList())
  ): ObserveQueueStatsUseCase {
    return ObserveQueueStatsUseCase(
      taskDao = taskDao,
      queueIdProvider = FakeQueueIdProvider("queue-A")
    )
  }

  private fun createObserveQueuePhotoStatusUseCase(
    taskDao: FakeDownloadTaskDao = FakeDownloadTaskDao(emptyList())
  ): ObserveQueuePhotoStatusUseCase {
    return ObserveQueuePhotoStatusUseCase(
      taskDao = taskDao,
      queueIdProvider = FakeQueueIdProvider("queue-A")
    )
  }

  private class FakeCameraSessionManager(
    initialState: SessionState
  ) : CameraSessionManager {
    private val flow = MutableStateFlow(initialState)
    private var assertReadyResult: SessionState = initialState

    override val sessionState: Flow<SessionState> = flow

    override suspend fun assertReady(): SessionState = assertReadyResult

    fun emit(state: SessionState) {
      flow.value = state
      assertReadyResult = state
    }
  }

  private class FakePhotoRepository(
    private val results: MutableList<Result<List<RemotePhoto>>> = mutableListOf(Result.success(emptyList())),
    private val pagedDataset: List<RemotePhoto>? = null
  ) : PhotoRepository {
    var fetchCount: Int = 0
    var fetchPageCount: Int = 0

    override suspend fun fetchRemotePhotos(): List<RemotePhoto> {
      fetchCount += 1
      val result = if (results.isNotEmpty()) {
        results.removeAt(0)
      } else {
        Result.success(emptyList())
      }
      return result.getOrThrow()
    }

    override suspend fun fetchRemotePhotosPage(offset: Int, limit: Int): List<RemotePhoto> {
      val dataset = pagedDataset ?: return super.fetchRemotePhotosPage(offset, limit)
      fetchPageCount += 1
      if (offset < 0 || limit <= 0 || offset >= dataset.size) {
        return emptyList()
      }
      val endExclusive = (offset + limit).coerceAtMost(dataset.size)
      return dataset.subList(offset, endExclusive)
    }
  }

  private class FakeDownloadedPhotoDao(
    downloadedIds: Set<String> = emptySet()
  ) : dev.danielc.core.db.dao.DownloadedPhotoDao {
    private val entities = downloadedIds.associateWith { photoId ->
      DownloadedPhotoEntity(
        photoId = photoId,
        localUri = "content://local/$photoId",
        displayName = "$photoId.jpg",
        relativePath = "Pictures/FujifilmCam",
        mimeType = "image/jpeg",
        downloadedAtEpochMillis = 1L
      )
    }.toMutableMap()

    override fun observe(photoId: String): Flow<DownloadedPhotoEntity?> {
      return flows.getOrPut(photoId) { MutableStateFlow(entities[photoId]) }
    }

    override suspend fun get(photoId: String): DownloadedPhotoEntity? {
      return entities[photoId]
    }

    override suspend fun upsert(entity: DownloadedPhotoEntity) {
      entities[entity.photoId] = entity
      flows.getOrPut(entity.photoId) { MutableStateFlow(entity) }.value = entity
    }

    private val flows = linkedMapOf<String, MutableStateFlow<DownloadedPhotoEntity?>>()
  }

  private class AlwaysExistsVerifier : MediaUriVerifier {
    override suspend fun exists(uriString: String): Boolean = true
  }

  private class NeverExistsVerifier : MediaUriVerifier {
    override suspend fun exists(uriString: String): Boolean = false
  }

  private class FakeQueueIdProvider(
    private val queueId: String
  ) : QueueIdProvider {
    override suspend fun getOrCreateQueueId(): String = queueId
  }

  private class FakeDownloadTaskDao(
    tasks: List<DownloadTaskEntity>
  ) : DownloadTaskDao {
    private val taskFlow = MutableStateFlow(tasks)

    override fun observeByPhotoId(photoId: String): Flow<DownloadTaskEntity?> {
      return flowOf(taskFlow.value.filter { it.photoId == photoId }.maxByOrNull { it.createdAtEpochMillis })
    }

    override fun observeActive(): Flow<List<DownloadTaskEntity>> {
      return flowOf(taskFlow.value.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING })
    }

    override fun observeByQueueId(queueId: String): Flow<List<DownloadTaskEntity>> {
      return flowOf(taskFlow.value.filter { it.queueId == queueId }.sortedBy { it.createdAtEpochMillis })
    }

    override suspend fun insert(entity: DownloadTaskEntity) = Unit

    override suspend fun updateStatus(
      taskId: String,
      status: DownloadStatus,
      progressPercent: Int,
      errorCode: DownloadErrorCode?,
      localUri: String?,
      updatedAtEpochMillis: Long
    ) = Unit

    override suspend fun findExistingActiveTask(photoId: String): DownloadTaskEntity? = null

    override suspend fun nextQueuedTask(queueId: String): DownloadTaskEntity? = null
  }

  private class FakeWifiConnectionMonitor(
    private val ssidFlow: MutableStateFlow<String?>
  ) : WifiConnectionMonitor {
    override val currentSsid: Flow<String?> = ssidFlow
    override val isWifiConnected: Flow<Boolean> = ssidFlow.map { !it.isNullOrBlank() }
  }

  private class TestErrorMessageMapper : ErrorMessageMapper {
    override fun toUserMessage(error: AppError): String = "mapped-user-error"
    override fun toDownloadFailMessage(code: DownloadErrorCode): String = "mapped-download-error"
  }

  private class RecordingAnalyticsTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
      events += event
    }
  }
}

private fun formatTakenAtForTest(epochMillis: Long): String {
  val zonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  return TEST_TAKEN_AT_FORMATTER.format(zonedDateTime)
}

private val TEST_TAKEN_AT_FORMATTER: DateTimeFormatter =
  DateTimeFormatter.ofPattern("yyyyMMdd HH:mm:ss", Locale.US)
