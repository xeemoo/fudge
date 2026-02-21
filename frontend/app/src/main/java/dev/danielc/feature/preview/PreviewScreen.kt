package dev.danielc.feature.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.danielc.R
import dev.danielc.app.language.currentAppLocale
import dev.danielc.ui.asString
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
  state: PreviewContract.State,
  onIntent: (PreviewContract.Intent) -> Unit,
  onBack: () -> Unit
) {
  var showDetailSheet by remember { mutableStateOf(false) }
  val displayFileName = state.fileName?.trim()?.takeIf { it.isNotEmpty() } ?: state.photoId
  val takenAtText = state.takenAtEpochMillis?.let(::formatTakenAt)
    ?: stringResource(id = R.string.preview_detail_unknown)
  val fileSizeText = state.fileSizeBytes?.let(::formatFileSize)
    ?: stringResource(id = R.string.preview_detail_unknown)
  val mediaTypeText = when (resolveMediaType(state.mimeType, state.fileName)) {
    PreviewMediaType.IMAGE -> stringResource(id = R.string.preview_detail_media_type_image)
    PreviewMediaType.VIDEO -> stringResource(id = R.string.preview_detail_media_type_video)
    PreviewMediaType.UNKNOWN -> stringResource(id = R.string.preview_detail_media_type_unknown)
  }

  Scaffold(
    containerColor = androidx.compose.ui.graphics.Color.Transparent,
    topBar = {
      TopAppBar(
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(id = R.string.preview_back)
            )
          }
        },
        title = {
          Text(
            text = stringResource(id = R.string.preview_title),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        },
        actions = {
          IconButton(onClick = { showDetailSheet = true }) {
            Icon(
              imageVector = Icons.Filled.Info,
              contentDescription = stringResource(id = R.string.preview_open_details)
            )
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Text(
          text = displayFileName,
          style = MaterialTheme.typography.titleMedium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
      }

      Card(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        when {
          state.isLoading -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
              ) {
                CircularProgressIndicator()
                Text(
                  text = stringResource(id = R.string.preview_loading),
                  modifier = Modifier.padding(top = 12.dp)
                )
              }
            }
          }

          state.errorMessage != null -> {
            val errorMessage = requireNotNull(state.errorMessage)
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(text = errorMessage.asString())
                TextButton(
                  onClick = { onIntent(PreviewContract.Intent.OnRetry) }
                ) {
                  Text(text = stringResource(id = R.string.preview_retry))
                }
              }
            }
          }

          state.imageBytes != null -> {
            AsyncImage(
              model = state.imageBytes,
              contentDescription = stringResource(
                id = R.string.preview_image_content_description,
                state.photoId
              ),
              contentScale = ContentScale.Fit,
              modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
            )
          }

          else -> {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Text(text = stringResource(id = R.string.preview_load_failed))
                TextButton(
                  onClick = { onIntent(PreviewContract.Intent.OnRetry) }
                ) {
                  Text(text = stringResource(id = R.string.preview_retry))
                }
              }
            }
          }
        }
      }

      val downloadButtonUi = DownloadButtonStateMapper.map(state.downloadButtonState)
      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        )
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Text(
            text = stringResource(id = R.string.preview_taken_at_inline, takenAtText),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Button(
            onClick = { onIntent(PreviewContract.Intent.OnClickDownload) },
            enabled = downloadButtonUi.enabled,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(text = stringResource(id = downloadButtonUi.textResId))
          }
        }
      }
    }
  }

  if (showDetailSheet) {
    ModalBottomSheet(onDismissRequest = { showDetailSheet = false }) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp)
          .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text(
          text = stringResource(id = R.string.preview_detail_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        DetailItem(
          label = stringResource(id = R.string.preview_detail_name_label),
          value = displayFileName
        )
        DetailItem(
          label = stringResource(id = R.string.preview_detail_taken_at_label),
          value = takenAtText
        )
        DetailItem(
          label = stringResource(id = R.string.preview_detail_file_size_label),
          value = fileSizeText
        )
        DetailItem(
          label = stringResource(id = R.string.preview_detail_media_type_label),
          value = mediaTypeText
        )
      }
    }
  }
}

@Composable
private fun DetailItem(
  label: String,
  value: String
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = value,
        style = MaterialTheme.typography.bodyMedium
      )
    }
  }
}

private enum class PreviewMediaType {
  IMAGE,
  VIDEO,
  UNKNOWN
}

private fun resolveMediaType(mimeType: String?, fileName: String?): PreviewMediaType {
  val mime = mimeType?.trim()?.lowercase()
  if (!mime.isNullOrEmpty()) {
    if (mime.startsWith("image/")) return PreviewMediaType.IMAGE
    if (mime.startsWith("video/")) return PreviewMediaType.VIDEO
  }

  val extension = fileName
    ?.substringAfterLast('.', missingDelimiterValue = "")
    ?.trim()
    ?.lowercase()
    .orEmpty()
  if (extension in IMAGE_EXTENSIONS) return PreviewMediaType.IMAGE
  if (extension in VIDEO_EXTENSIONS) return PreviewMediaType.VIDEO
  return PreviewMediaType.UNKNOWN
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

private val IMAGE_EXTENSIONS = setOf(
  "jpg", "jpeg", "png", "webp", "bmp", "gif", "heic", "heif", "tif", "tiff", "raf", "raw"
)
private val VIDEO_EXTENSIONS = setOf(
  "mp4", "mov", "avi", "mkv", "3gp", "m4v", "mts", "webm"
)
