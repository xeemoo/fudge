package dev.danielc.feature.photolist

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.danielc.core.data.ThumbnailRepository
import dev.danielc.core.data.ThumbnailState
import dev.danielc.core.domain.PhotoId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ThumbnailComposable(
  photoId: String,
  thumbnailRepository: ThumbnailRepository,
  modifier: Modifier = Modifier
) {
  val state by remember(photoId, thumbnailRepository) {
    thumbnailRepository.observeThumbnail(PhotoId(photoId))
  }.collectAsState(initial = ThumbnailState.Loading)

  when (val current = state) {
    ThumbnailState.Loading -> {
      ThumbnailPlaceholder(
        icon = Icons.Outlined.Image,
        modifier = modifier
      )
    }

    is ThumbnailState.Error -> {
      ThumbnailPlaceholder(
        icon = Icons.Outlined.BrokenImage,
        modifier = modifier
      )
    }

    is ThumbnailState.Ready -> {
      var bitmap by remember(current.bytes) { mutableStateOf<Bitmap?>(null) }

      LaunchedEffect(current.bytes) {
        bitmap = decodeLegacyThumbnail(current.bytes)
      }

      if (bitmap == null) {
        ThumbnailPlaceholder(
          icon = Icons.Outlined.BrokenImage,
          modifier = modifier
        )
      } else {
        Image(
          bitmap = bitmap!!.asImageBitmap(),
          contentDescription = null,
          modifier = modifier.clip(RoundedCornerShape(10.dp)),
          contentScale = ContentScale.Crop
        )
      }
    }
  }
}

@Composable
private fun ThumbnailPlaceholder(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(10.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant
    )
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .size(56.dp),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(8.dp)
      )
    }
  }
}

private suspend fun decodeLegacyThumbnail(bytes: ByteArray): Bitmap? = withContext(Dispatchers.Default) {
  runCatching {
    val opt = BitmapFactory.Options().apply {
      inScaled = true
      inDensity = 320
      inTargetDensity = 160
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opt)
  }.getOrNull()
}
