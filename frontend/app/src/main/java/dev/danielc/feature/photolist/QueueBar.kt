package dev.danielc.feature.photolist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.danielc.R

@Composable
fun QueueBar(
  stats: PhotoListContract.QueueBarUi,
  modifier: Modifier = Modifier
) {
  val text = if (stats.running) {
    stringResource(
      id = R.string.photo_list_queue_bar_downloading,
      stats.done,
      stats.total
    )
  } else {
    stringResource(
      id = R.string.photo_list_queue_bar_completed,
      stats.done,
      stats.total
    )
  }

  val progress = if (stats.total <= 0) {
    0f
  } else {
    (stats.done.toFloat() / stats.total.toFloat()).coerceIn(0f, 1f)
  }

  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
      )
      LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth()
      )
    }
  }
}
