package dev.danielc.feature.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.danielc.BuildConfig
import dev.danielc.R

@Composable
fun SettingsScreen(
  onNavigateToLanguage: () -> Unit,
  onNavigateToHelp: () -> Unit,
  onNavigateToAbout: () -> Unit
) {
  val context = LocalContext.current

  Column(
    modifier = Modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 16.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
      )
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
          androidx.compose.foundation.Image(
            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
            contentDescription = stringResource(id = R.string.settings_app_icon),
            contentScale = ContentScale.Crop,
            modifier = Modifier
              .padding(6.dp)
              .size(48.dp)
          )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = stringResource(id = R.string.app_name),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
          )
          Text(
            text = stringResource(id = R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
    }

    Text(
      text = stringResource(id = R.string.settings_section_general),
      style = MaterialTheme.typography.labelLarge,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 4.dp)
    )

    Card(
      modifier = Modifier.fillMaxWidth(),
      colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface
      )
    ) {
      SettingItem(
        icon = Icons.Filled.Language,
        text = stringResource(id = R.string.settings_item_language),
        subtitle = stringResource(id = R.string.settings_item_language_desc),
        onClick = onNavigateToLanguage
      )
      HorizontalDivider()
      SettingItem(
        icon = Icons.AutoMirrored.Filled.HelpOutline,
        text = stringResource(id = R.string.settings_item_help),
        subtitle = stringResource(id = R.string.settings_item_help_desc),
        onClick = onNavigateToHelp
      )
      HorizontalDivider()
      SettingItem(
        icon = Icons.Filled.Feedback,
        text = stringResource(id = R.string.settings_item_feedback),
        subtitle = stringResource(id = R.string.settings_item_feedback_desc),
        onClick = {
          val intent = Intent(Intent.ACTION_VIEW, FEEDBACK_URL.toUri())
          context.startActivity(intent)
        }
      )
      HorizontalDivider()
      SettingItem(
        icon = Icons.Filled.Info,
        text = stringResource(id = R.string.settings_item_about),
        subtitle = stringResource(id = R.string.settings_item_about_desc),
        onClick = onNavigateToAbout
      )
      HorizontalDivider()
      SettingItem(
        icon = Icons.Filled.Description,
        text = stringResource(id = R.string.settings_item_export_logs),
        subtitle = stringResource(id = R.string.settings_item_export_logs_desc),
        onClick = {}
      )
    }
  }
}

private const val FEEDBACK_URL = "https://discord.gg/DfRptGxGFS"

@Composable
private fun SettingItem(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  text: String,
  subtitle: String,
  onClick: () -> Unit
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 14.dp)
  ) {
    Surface(
      shape = CircleShape,
      color = MaterialTheme.colorScheme.surfaceVariant
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.padding(8.dp)
      )
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Icon(
      imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
