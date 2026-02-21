package dev.danielc.feature.settings.language

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.danielc.R
import dev.danielc.app.language.AppLanguage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(
  currentLanguage: AppLanguage,
  onBack: () -> Unit,
  onSelectLanguage: (AppLanguage) -> Unit
) {
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
          Text(text = stringResource(id = R.string.settings_language_title))
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text(
        text = stringResource(id = R.string.settings_language_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
      )

      Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
          containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
          width = 1.dp,
          color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
      ) {
        LanguageOptionRow(
          title = stringResource(id = R.string.settings_language_option_system),
          subtitle = stringResource(id = R.string.settings_language_option_system_desc),
          selected = currentLanguage == AppLanguage.SYSTEM,
          onClick = { onSelectLanguage(AppLanguage.SYSTEM) }
        )
        HorizontalDivider()
        LanguageOptionRow(
          title = stringResource(id = R.string.settings_language_option_english),
          subtitle = stringResource(id = R.string.settings_language_option_english_desc),
          selected = currentLanguage == AppLanguage.ENGLISH,
          onClick = { onSelectLanguage(AppLanguage.ENGLISH) }
        )
        HorizontalDivider()
        LanguageOptionRow(
          title = stringResource(id = R.string.settings_language_option_simplified_chinese),
          subtitle = stringResource(id = R.string.settings_language_option_simplified_chinese_desc),
          selected = currentLanguage == AppLanguage.CHINESE_SIMPLIFIED,
          onClick = { onSelectLanguage(AppLanguage.CHINESE_SIMPLIFIED) }
        )
      }
    }
  }
}

@Composable
private fun LanguageOptionRow(
  title: String,
  subtitle: String,
  selected: Boolean,
  onClick: () -> Unit
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 14.dp, vertical = 14.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = title,
        fontWeight = FontWeight.Medium
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    RadioButton(
      selected = selected,
      onClick = onClick
    )
  }
}
