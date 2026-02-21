package dev.danielc.feature.photolist

import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.SessionState
import dev.danielc.R
import dev.danielc.app.language.currentAppLocale
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.analytics.NoOpAnalyticsTracker
import dev.danielc.core.domain.AppException
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.PhotoId
import dev.danielc.core.domain.RemotePhoto
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.IsDownloadedUseCase
import dev.danielc.core.domain.usecase.ObserveQueuePhotoStatusUseCase
import dev.danielc.core.domain.usecase.ObserveQueueStatsUseCase
import dev.danielc.core.domain.usecase.QueuePhotoStatus
import dev.danielc.core.domain.usecase.QueueStats
import dev.danielc.core.mvi.MviViewModel
import dev.danielc.core.wifi.WifiConnectionMonitor
import dev.danielc.ui.UiText
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PhotoListViewModel(
  private val fetchPhotoListUseCase: FetchPhotoListUseCase,
  private val cameraSessionManager: CameraSessionManager,
  private val isDownloadedUseCase: IsDownloadedUseCase,
  private val observeQueueStatsUseCase: ObserveQueueStatsUseCase,
  private val observeQueuePhotoStatusUseCase: ObserveQueuePhotoStatusUseCase,
  private val errorMessageMapper: ErrorMessageMapper,
  private val wifiConnectionMonitor: WifiConnectionMonitor = NoOpWifiConnectionMonitor,
  private val analyticsTracker: AnalyticsTracker = NoOpAnalyticsTracker
) : MviViewModel<PhotoListContract.Intent, PhotoListContract.State, PhotoListContract.Effect>(
  initialState = PhotoListContract.State()
) {
  private var latestQueueBadgeByPhotoId: Map<String, PhotoListContract.QueueBadgeUi> = emptyMap()
  private var observeDownloadedJob: Job? = null
  private var shouldRefreshOnNextEnter: Boolean = false
  private var nextOffset: Int = 0

  init {
    viewModelScope.launch {
      wifiConnectionMonitor.currentSsid
        .map { ssid -> ssid?.trim()?.takeIf { it.isNotEmpty() } }
        .distinctUntilChanged()
        .collectLatest { cameraName ->
          setState {
            copy(cameraName = cameraName)
          }
        }
    }
    viewModelScope.launch {
      cameraSessionManager.sessionState.collectLatest { sessionState ->
        if (sessionState is SessionState.NotReady) {
          shouldRefreshOnNextEnter = true
        }
      }
    }
    viewModelScope.launch {
      observeQueueStatsUseCase.observe().collectLatest { stats ->
        setState {
          copy(queueBar = stats.toQueueBarUi())
        }
      }
    }
    viewModelScope.launch {
      observeQueuePhotoStatusUseCase.observe().collectLatest { statusByPhotoId ->
        latestQueueBadgeByPhotoId = statusByPhotoId.mapValues { (_, status) ->
          status.toQueueBadgeUi()
        }
        setState {
          copy(
            items = items.map { item ->
              item.copy(queueBadge = latestQueueBadgeByPhotoId[item.photoId])
            }
          )
        }
      }
    }
  }

  override suspend fun reduce(intent: PhotoListContract.Intent) {
    when (intent) {
      PhotoListContract.Intent.OnEnter -> {
        val hasUsableItems = state.value.items.isNotEmpty() && state.value.errorMessage == null
        if (state.value.isLoading || state.value.isAppending || (hasUsableItems && !shouldRefreshOnNextEnter)) {
          return
        }
        loadFirstPage()
      }
      PhotoListContract.Intent.OnRetry -> loadFirstPage()
      PhotoListContract.Intent.OnLoadMore -> loadNextPage()
      PhotoListContract.Intent.OnToggleLayoutMode -> {
        setState {
          copy(
            layoutMode = when (layoutMode) {
              PhotoListContract.LayoutMode.LIST -> PhotoListContract.LayoutMode.GRID_4
              PhotoListContract.LayoutMode.GRID_4 -> PhotoListContract.LayoutMode.LIST
            }
          )
        }
      }
      is PhotoListContract.Intent.OnClickPhoto -> {
        analyticsTracker.track(AnalyticsEvent.PhotoItemClick)
        postEffect {
          PhotoListContract.Effect.NavigateToPreview(intent.photoId)
        }
      }
    }
  }

  private suspend fun loadFirstPage() {
    nextOffset = 0
    loadPage(reset = true)
  }

  private suspend fun loadNextPage() {
    val currentState = state.value
    if (currentState.isLoading || currentState.isAppending || currentState.errorMessage != null || !currentState.hasMore) {
      return
    }
    loadPage(reset = false)
  }

  private suspend fun loadPage(reset: Boolean) {
    if (reset) {
      analyticsTracker.track(AnalyticsEvent.PhotoListRequest)
    }
    val currentOffset = if (reset) 0 else nextOffset
    setState {
      copy(
        isLoading = reset,
        isAppending = !reset,
        errorMessage = null
      )
    }

    val result = fetchPhotoListUseCase(
      offset = currentOffset,
      limit = PHOTO_LIST_PAGE_SIZE
    )
    if (result.isSuccess) {
      if (reset) {
        analyticsTracker.track(AnalyticsEvent.PhotoListSuccess)
      }
      val photos = result.getOrNull().orEmpty()
      val pageItems = photos.map { photo ->
        photo.toUiItem(latestQueueBadgeByPhotoId[photo.photoId.value])
      }
      nextOffset = currentOffset + photos.size
      setState {
        val mergedItems = if (reset) {
          pageItems
        } else {
          (items + pageItems).distinctBy { item -> item.photoId }
        }
        copy(
          isLoading = false,
          isAppending = false,
          hasMore = photos.size == PHOTO_LIST_PAGE_SIZE,
          items = mergedItems,
          errorMessage = null,
        )
      }
      shouldRefreshOnNextEnter = false
      observeDownloadedState(state.value.items.map { it.photoId })
      return
    }

    val throwable = result.exceptionOrNull()
    if (reset) {
      analyticsTracker.track(AnalyticsEvent.PhotoListFail)
    }
    if (reset) {
      observeDownloadedState(emptyList())
      setState {
        copy(
          isLoading = false,
          isAppending = false,
          hasMore = false,
          items = emptyList(),
          errorMessage = throwable?.toDisplayMessage(errorMessageMapper) ?: UiText.Res(R.string.photo_list_error_default)
        )
      }
      return
    }
    setState {
      copy(
        isLoading = false,
        isAppending = false,
        hasMore = false
      )
    }
  }

  private fun RemotePhoto.toUiItem(queueBadge: PhotoListContract.QueueBadgeUi?): PhotoListContract.PhotoListItemUi {
    val normalizedFileName = fileName?.trim()?.takeIf { it.isNotEmpty() }
    val fileNameUi = normalizedFileName?.let { name ->
      UiText.Res(R.string.photo_list_item_name, name)
    } ?: UiText.Res(R.string.photo_list_item_name_unknown)
    val takenAtUi = takenAtEpochMillis?.let { epoch ->
      UiText.Res(R.string.photo_list_item_taken_at, formatTakenAt(epoch))
    } ?: UiText.Res(R.string.photo_list_item_taken_at_unknown)
    val fileSizeUi = fileSizeBytes?.let { bytes ->
      formatFileSize(bytes)
    }?.let { size ->
      UiText.Res(R.string.photo_list_item_size, size)
    } ?: UiText.Res(R.string.photo_list_item_size_unknown)
    val mediaTypeValue = resolveMediaTypeUiText(
      mimeType = mimeType,
      fileName = normalizedFileName
    )
    return PhotoListContract.PhotoListItemUi(
      photoId = photoId.value,
      fileName = fileNameUi,
      takenAt = takenAtUi,
      fileSize = fileSizeUi,
      mediaType = mediaTypeValue,
      isDownloaded = false,
      queueBadge = queueBadge
    )
  }

  private fun observeDownloadedState(photoIds: List<String>) {
    observeDownloadedJob?.cancel()
    if (photoIds.isEmpty()) {
      return
    }
    observeDownloadedJob = viewModelScope.launch {
      combine(
        photoIds.map { id ->
          isDownloadedUseCase.observe(PhotoId(id)).map { downloaded -> id to downloaded }
        }
      ) { results ->
        results.associate { it.first to it.second }
      }.collectLatest { downloadedByPhotoId ->
        setState {
          copy(
            items = items.map { item ->
              item.copy(isDownloaded = downloadedByPhotoId[item.photoId] == true)
            }
          )
        }
      }
    }
  }
}

private const val PHOTO_LIST_PAGE_SIZE = 20

private fun QueueStats.toQueueBarUi(): PhotoListContract.QueueBarUi? {
  if (total <= 0) return null
  return PhotoListContract.QueueBarUi(
    done = done,
    total = total,
    running = running
  )
}

private fun QueuePhotoStatus.toQueueBadgeUi(): PhotoListContract.QueueBadgeUi {
  return when (this) {
    QueuePhotoStatus.QUEUED -> PhotoListContract.QueueBadgeUi.QUEUED
    QueuePhotoStatus.DOWNLOADING -> PhotoListContract.QueueBadgeUi.DOWNLOADING
  }
}

private fun Throwable.toDisplayMessage(errorMessageMapper: ErrorMessageMapper): UiText {
  if (this is AppException) {
    return UiText.Dynamic(errorMessageMapper.toUserMessage(error))
  }
  return if (message.isNullOrBlank()) {
    UiText.Res(R.string.photo_list_error_default)
  } else {
    UiText.Dynamic(message.orEmpty())
  }
}

private object NoOpWifiConnectionMonitor : WifiConnectionMonitor {
  override val currentSsid = flowOf<String?>(null)
  override val isWifiConnected = flowOf(false)
}

private fun formatFileSize(bytes: Long): String? {
  val locale = currentAppLocale()
  if (bytes < 0L) return null
  if (bytes < 1024L) return "$bytes B"
  val kb = bytes / 1024.0
  if (kb < 1024.0) return String.format(locale, "%.1f KB", kb)
  val mb = kb / 1024.0
  if (mb < 1024.0) return String.format(locale, "%.1f MB", mb)
  val gb = mb / 1024.0
  return String.format(locale, "%.1f GB", gb)
}

private fun formatTakenAt(epochMillis: Long): String {
  val zonedDateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
  return DateTimeFormatter
    .ofPattern("yyyyMMdd HH:mm:ss", currentAppLocale())
    .format(zonedDateTime)
}

private fun resolveMediaTypeUiText(mimeType: String?, fileName: String?): PhotoListContract.MediaTypeUi {
  val mime = mimeType?.trim()?.lowercase()
  if (!mime.isNullOrEmpty()) {
    if (mime.startsWith("image/")) return PhotoListContract.MediaTypeUi.IMAGE
    if (mime.startsWith("video/")) return PhotoListContract.MediaTypeUi.VIDEO
  }

  val extension = fileName
    ?.substringAfterLast('.', missingDelimiterValue = "")
    ?.trim()
    ?.lowercase()
    .orEmpty()
  if (extension in IMAGE_EXTENSIONS) return PhotoListContract.MediaTypeUi.IMAGE
  if (extension in VIDEO_EXTENSIONS) return PhotoListContract.MediaTypeUi.VIDEO
  return PhotoListContract.MediaTypeUi.UNKNOWN
}

private val IMAGE_EXTENSIONS = setOf(
  "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "tif", "tiff", "raf", "raw"
)
private val VIDEO_EXTENSIONS = setOf(
  "mp4", "mov", "avi", "mkv", "3gp", "m4v", "mts", "webm"
)
