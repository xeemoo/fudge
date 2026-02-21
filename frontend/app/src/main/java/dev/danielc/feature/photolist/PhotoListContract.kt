package dev.danielc.feature.photolist

import dev.danielc.ui.UiText

object PhotoListContract {

  enum class LayoutMode {
    LIST,
    GRID_4
  }

  enum class MediaTypeUi {
    IMAGE,
    VIDEO,
    UNKNOWN
  }

  data class QueueBarUi(
    val done: Int,
    val total: Int,
    val running: Boolean
  )

  enum class QueueBadgeUi {
    QUEUED,
    DOWNLOADING
  }

  data class PhotoListItemUi(
    val photoId: String,
    val fileName: UiText,
    val takenAt: UiText,
    val fileSize: UiText,
    val mediaType: MediaTypeUi,
    val isDownloaded: Boolean,
    val queueBadge: QueueBadgeUi? = null
  )

  data class State(
    val cameraName: String? = null,
    val layoutMode: LayoutMode = LayoutMode.LIST,
    val isLoading: Boolean = false,
    val isAppending: Boolean = false,
    val hasMore: Boolean = false,
    val items: List<PhotoListItemUi> = emptyList(),
    val errorMessage: UiText? = null,
    val queueBar: QueueBarUi? = null
  )

  sealed interface Intent {
    data object OnEnter : Intent
    data object OnRetry : Intent
    data object OnLoadMore : Intent
    data object OnToggleLayoutMode : Intent
    data class OnClickPhoto(val photoId: String) : Intent
  }

  sealed interface Effect {
    data class NavigateToPreview(val photoId: String) : Effect
  }
}
