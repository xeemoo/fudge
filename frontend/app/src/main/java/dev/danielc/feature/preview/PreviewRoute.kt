package dev.danielc.feature.preview

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.usecase.EnqueueDownloadUseCase
import dev.danielc.core.domain.usecase.FetchPreviewImageUseCase
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.ObserveDownloadStateUseCase
import dev.danielc.ui.resolve
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PreviewRoute(
  photoId: String,
  fetchPreviewImageUseCase: FetchPreviewImageUseCase,
  fetchPhotoListUseCase: FetchPhotoListUseCase,
  observeDownloadStateUseCase: ObserveDownloadStateUseCase,
  enqueueDownloadUseCase: EnqueueDownloadUseCase,
  errorMessageMapper: ErrorMessageMapper,
  analyticsTracker: AnalyticsTracker,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val viewModel: PreviewViewModel = viewModel(
    factory = remember(
      photoId,
      fetchPreviewImageUseCase,
      fetchPhotoListUseCase,
      observeDownloadStateUseCase,
      enqueueDownloadUseCase,
      errorMessageMapper,
      analyticsTracker
    ) {
      PreviewViewModelFactory(
        photoId = photoId,
        fetchPreviewImageUseCase = fetchPreviewImageUseCase,
        fetchPhotoListUseCase = fetchPhotoListUseCase,
        observeDownloadStateUseCase = observeDownloadStateUseCase,
        enqueueDownloadUseCase = enqueueDownloadUseCase,
        errorMessageMapper = errorMessageMapper,
        analyticsTracker = analyticsTracker
      )
    }
  )
  val state by viewModel.state.collectAsState()

  LaunchedEffect(viewModel) {
    viewModel.accept(PreviewContract.Intent.OnEnter)
  }
  LaunchedEffect(viewModel, context) {
    viewModel.effect.collectLatest { effect ->
      when (effect) {
        is PreviewContract.Effect.ShowToast -> {
          Toast.makeText(context, effect.message.resolve(context), Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  PreviewScreen(
    state = state,
    onIntent = viewModel::accept,
    onBack = onBack
  )
}

private class PreviewViewModelFactory(
  private val photoId: String,
  private val fetchPreviewImageUseCase: FetchPreviewImageUseCase,
  private val fetchPhotoListUseCase: FetchPhotoListUseCase,
  private val observeDownloadStateUseCase: ObserveDownloadStateUseCase,
  private val enqueueDownloadUseCase: EnqueueDownloadUseCase,
  private val errorMessageMapper: ErrorMessageMapper,
  private val analyticsTracker: AnalyticsTracker
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    check(modelClass.isAssignableFrom(PreviewViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return PreviewViewModel(
      fetchPreviewImageUseCase = fetchPreviewImageUseCase,
      fetchPhotoListUseCase = fetchPhotoListUseCase,
      observeDownloadStateUseCase = observeDownloadStateUseCase,
      enqueueDownloadUseCase = enqueueDownloadUseCase,
      errorMessageMapper = errorMessageMapper,
      analyticsTracker = analyticsTracker,
      photoId = photoId
    ) as T
  }
}
