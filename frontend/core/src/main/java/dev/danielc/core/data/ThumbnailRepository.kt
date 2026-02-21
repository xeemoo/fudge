package dev.danielc.core.data

import dev.danielc.core.domain.PhotoId
import kotlinx.coroutines.flow.Flow

sealed interface ThumbnailState {
  data object Loading : ThumbnailState
  data class Ready(val bytes: ByteArray) : ThumbnailState
  data class Error(val message: String) : ThumbnailState
}

interface ThumbnailRepository {
  fun observeThumbnail(photoId: PhotoId): Flow<ThumbnailState>
}
