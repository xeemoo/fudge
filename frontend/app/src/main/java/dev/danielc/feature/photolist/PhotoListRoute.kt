package dev.danielc.feature.photolist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.ThumbnailRepository
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.IsDownloadedUseCase
import dev.danielc.core.domain.usecase.ObserveQueuePhotoStatusUseCase
import dev.danielc.core.domain.usecase.ObserveQueueStatsUseCase
import dev.danielc.core.wifi.WifiConnectionMonitor
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PhotoListRoute(
  fetchPhotoListUseCase: FetchPhotoListUseCase,
  cameraSessionManager: CameraSessionManager,
  wifiConnectionMonitor: WifiConnectionMonitor,
  isDownloadedUseCase: IsDownloadedUseCase,
  observeQueueStatsUseCase: ObserveQueueStatsUseCase,
  observeQueuePhotoStatusUseCase: ObserveQueuePhotoStatusUseCase,
  errorMessageMapper: ErrorMessageMapper,
  analyticsTracker: AnalyticsTracker,
  thumbnailRepository: ThumbnailRepository,
  onBack: () -> Unit,
  onNavigateToPreview: (photoId: String) -> Unit
) {
  val viewModel: PhotoListViewModel = viewModel(
    factory = remember(
      fetchPhotoListUseCase,
      cameraSessionManager,
      wifiConnectionMonitor,
      isDownloadedUseCase,
      observeQueueStatsUseCase,
      observeQueuePhotoStatusUseCase,
      errorMessageMapper,
      analyticsTracker
    ) {
      PhotoListViewModelFactory(
        fetchPhotoListUseCase = fetchPhotoListUseCase,
        cameraSessionManager = cameraSessionManager,
        wifiConnectionMonitor = wifiConnectionMonitor,
        isDownloadedUseCase = isDownloadedUseCase,
        observeQueueStatsUseCase = observeQueueStatsUseCase,
        observeQueuePhotoStatusUseCase = observeQueuePhotoStatusUseCase,
        errorMessageMapper = errorMessageMapper,
        analyticsTracker = analyticsTracker
      )
    }
  )
  val state by viewModel.state.collectAsState()

  LaunchedEffect(viewModel) {
    viewModel.accept(PhotoListContract.Intent.OnEnter)
  }

  LaunchedEffect(viewModel, onNavigateToPreview) {
    viewModel.effect.collectLatest { effect ->
      when (effect) {
        is PhotoListContract.Effect.NavigateToPreview -> {
          onNavigateToPreview(effect.photoId)
        }
      }
    }
  }

  PhotoListScreen(
    state = state,
    thumbnailRepository = thumbnailRepository,
    onBack = onBack,
    onIntent = viewModel::accept
  )
}

private class PhotoListViewModelFactory(
  private val fetchPhotoListUseCase: FetchPhotoListUseCase,
  private val cameraSessionManager: CameraSessionManager,
  private val wifiConnectionMonitor: WifiConnectionMonitor,
  private val isDownloadedUseCase: IsDownloadedUseCase,
  private val observeQueueStatsUseCase: ObserveQueueStatsUseCase,
  private val observeQueuePhotoStatusUseCase: ObserveQueuePhotoStatusUseCase,
  private val errorMessageMapper: ErrorMessageMapper,
  private val analyticsTracker: AnalyticsTracker
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    check(modelClass.isAssignableFrom(PhotoListViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return PhotoListViewModel(
      fetchPhotoListUseCase = fetchPhotoListUseCase,
      cameraSessionManager = cameraSessionManager,
      wifiConnectionMonitor = wifiConnectionMonitor,
      isDownloadedUseCase = isDownloadedUseCase,
      observeQueueStatsUseCase = observeQueueStatsUseCase,
      observeQueuePhotoStatusUseCase = observeQueuePhotoStatusUseCase,
      errorMessageMapper = errorMessageMapper,
      analyticsTracker = analyticsTracker
    ) as T
  }
}
