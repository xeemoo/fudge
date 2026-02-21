package dev.danielc.feature.photolist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.danielc.R
import dev.danielc.core.data.ThumbnailRepository
import dev.danielc.ui.asString
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoListScreen(
  state: PhotoListContract.State,
  thumbnailRepository: ThumbnailRepository,
  onBack: () -> Unit,
  onIntent: (PhotoListContract.Intent) -> Unit
) {
  val listState = rememberLazyListState()
  val gridState = rememberLazyGridState()
  val latestOnIntent by rememberUpdatedState(onIntent)
  val latestLayoutMode by rememberUpdatedState(state.layoutMode)
  val latestHasMore by rememberUpdatedState(state.hasMore)
  val latestIsLoading by rememberUpdatedState(state.isLoading)
  val latestIsAppending by rememberUpdatedState(state.isAppending)
  val latestItemsLastIndex by rememberUpdatedState(state.items.lastIndex)
  val cameraDisplayName = state.cameraName
    ?.takeIf { it.isNotBlank() }
    ?: stringResource(id = R.string.photo_list_camera_fallback)
  val (layoutToggleIcon, layoutToggleContentDescription) = when (state.layoutMode) {
    PhotoListContract.LayoutMode.LIST -> {
      Icons.Filled.ViewModule to stringResource(id = R.string.photo_list_toggle_to_grid)
    }
    PhotoListContract.LayoutMode.GRID_4 -> {
      Icons.AutoMirrored.Filled.ViewList to stringResource(id = R.string.photo_list_toggle_to_list)
    }
  }

  LaunchedEffect(listState, gridState) {
    snapshotFlow {
      when (latestLayoutMode) {
        PhotoListContract.LayoutMode.LIST -> listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        PhotoListContract.LayoutMode.GRID_4 -> gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
      }
    }.collect { lastVisibleIndex ->
      val triggerIndex = (latestItemsLastIndex - LOAD_MORE_TRIGGER_AHEAD).coerceAtLeast(0)
      if (latestHasMore && !latestIsLoading && !latestIsAppending && lastVisibleIndex >= triggerIndex) {
        latestOnIntent(PhotoListContract.Intent.OnLoadMore)
      }
    }
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
          Column {
            Text(
              text = cameraDisplayName,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              style = MaterialTheme.typography.titleMedium
            )
            Text(
              text = stringResource(id = R.string.photo_list_total_count, state.items.size),
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        actions = {
          IconButton(
            onClick = { onIntent(PhotoListContract.Intent.OnRetry) }
          ) {
            Icon(
              imageVector = Icons.Filled.Refresh,
              contentDescription = stringResource(id = R.string.photo_list_refresh)
            )
          }
          FilledIconButton(
            onClick = { onIntent(PhotoListContract.Intent.OnToggleLayoutMode) }
          ) {
            Icon(
              imageVector = layoutToggleIcon,
              contentDescription = layoutToggleContentDescription
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
    ) {
      state.queueBar?.let { queueBar ->
        QueueBar(
          stats = queueBar,
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
      }
      when {
        state.isLoading -> {
          LoadingPanel(modifier = Modifier.weight(1f))
        }

        state.errorMessage != null -> {
          val errorMessage = requireNotNull(state.errorMessage)
          StatePanel(
            modifier = Modifier.weight(1f),
            message = errorMessage.asString(),
            actionLabel = stringResource(id = R.string.photo_list_retry),
            onAction = { onIntent(PhotoListContract.Intent.OnRetry) }
          )
        }

        state.items.isEmpty() -> {
          StatePanel(
            modifier = Modifier.weight(1f),
            message = stringResource(id = R.string.photo_list_empty),
            actionLabel = stringResource(id = R.string.photo_list_refresh),
            onAction = { onIntent(PhotoListContract.Intent.OnRetry) }
          )
        }

        else -> {
          when (state.layoutMode) {
            PhotoListContract.LayoutMode.LIST -> {
              LazyColumn(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                items(
                  items = state.items,
                  key = { item -> item.photoId },
                  contentType = { "photo-list-item" }
                ) { item ->
                  ListModeItem(
                    item = item,
                    thumbnailRepository = thumbnailRepository,
                    onClick = { latestOnIntent(PhotoListContract.Intent.OnClickPhoto(item.photoId)) }
                  )
                }
                if (state.isAppending) {
                  item(
                    key = "photo-list-appending",
                    contentType = "photo-list-appending"
                  ) {
                    AppendingIndicator()
                  }
                }
              }
            }

            PhotoListContract.LayoutMode.GRID_4 -> {
              LazyVerticalGrid(
                modifier = Modifier
                  .weight(1f)
                  .fillMaxWidth(),
                state = gridState,
                columns = GridCells.Fixed(4),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                items(
                  items = state.items,
                  key = { item -> item.photoId },
                  contentType = { "photo-grid-item" }
                ) { item ->
                  GridModeItem(
                    item = item,
                    thumbnailRepository = thumbnailRepository,
                    onClick = { latestOnIntent(PhotoListContract.Intent.OnClickPhoto(item.photoId)) }
                  )
                }
                if (state.isAppending) {
                  item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "photo-grid-appending",
                    contentType = "photo-grid-appending"
                  ) {
                    AppendingIndicator()
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

private const val LOAD_MORE_TRIGGER_AHEAD = 8

@Composable
private fun ListModeItem(
  item: PhotoListContract.PhotoListItemUi,
  thumbnailRepository: ThumbnailRepository,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Box(modifier = Modifier.size(64.dp)) {
        ThumbnailComposable(
          photoId = item.photoId,
          thumbnailRepository = thumbnailRepository,
          modifier = Modifier.fillMaxSize()
        )
        MediaTypeIcon(
          mediaType = item.mediaType,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(2.dp)
        )
      }
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        Text(
          text = item.fileName.asString(),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold
        )
        Text(
          text = item.takenAt.asString(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = item.fileSize.asString(),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      StatusBadge(item = item)
    }
  }
}

@Composable
private fun GridModeItem(
  item: PhotoListContract.PhotoListItemUi,
  thumbnailRepository: ThumbnailRepository,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      width = 1.dp,
      color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(6.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .aspectRatio(1f)
      ) {
        ThumbnailComposable(
          photoId = item.photoId,
          thumbnailRepository = thumbnailRepository,
          modifier = Modifier.fillMaxSize()
        )
        MediaTypeIcon(
          mediaType = item.mediaType,
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(3.dp)
        )
      }
      Text(
        text = item.fileName.asString(),
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      StatusBadge(
        item = item,
        style = MaterialTheme.typography.labelSmall
      )
    }
  }
}

@Composable
private fun LoadingPanel(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator()
    Text(
      text = stringResource(id = R.string.photo_list_loading),
      modifier = Modifier.padding(top = 12.dp),
      style = MaterialTheme.typography.bodyMedium
    )
  }
}

@Composable
private fun StatePanel(
  modifier: Modifier = Modifier,
  message: String,
  actionLabel: String,
  onAction: () -> Unit
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = message,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onAction) {
          Text(text = actionLabel)
        }
      }
    }
  }
}

@Composable
private fun AppendingIndicator() {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 16.dp),
    horizontalArrangement = Arrangement.Center
  ) {
    CircularProgressIndicator(modifier = Modifier.size(24.dp))
  }
}

@Composable
private fun StatusBadge(
  item: PhotoListContract.PhotoListItemUi,
  style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelMedium
) {
  val label = when {
    item.isDownloaded -> stringResource(id = R.string.photo_list_downloaded)
    item.queueBadge == PhotoListContract.QueueBadgeUi.QUEUED -> stringResource(id = R.string.photo_list_queue_badge_queued)
    item.queueBadge == PhotoListContract.QueueBadgeUi.DOWNLOADING -> stringResource(id = R.string.photo_list_queue_badge_downloading)
    else -> null
  } ?: return

  val (containerColor, contentColor) = when {
    item.isDownloaded -> {
      MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }
    else -> {
      MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    }
  }

  Surface(
    shape = CircleShape,
    color = containerColor
  ) {
    Text(
      text = label,
      color = contentColor,
      style = style,
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
  }
}

@Composable
private fun MediaTypeIcon(
  mediaType: PhotoListContract.MediaTypeUi,
  modifier: Modifier = Modifier
) {
  val (icon, contentDescription) = when (mediaType) {
    PhotoListContract.MediaTypeUi.IMAGE -> {
      Icons.Filled.Image to stringResource(id = R.string.photo_list_media_type_icon_image)
    }
    PhotoListContract.MediaTypeUi.VIDEO -> {
      Icons.Filled.Videocam to stringResource(id = R.string.photo_list_media_type_icon_video)
    }
    PhotoListContract.MediaTypeUi.UNKNOWN -> {
      Icons.AutoMirrored.Filled.HelpOutline to stringResource(id = R.string.photo_list_media_type_icon_unknown)
    }
  }
  Surface(
    modifier = modifier,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    tonalElevation = 2.dp
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier
        .padding(3.dp)
        .size(14.dp),
      tint = MaterialTheme.colorScheme.primary
    )
  }
}
