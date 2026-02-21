package dev.danielc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
  primary = FujiRed,
  onPrimary = FujiWhite,
  primaryContainer = Color(0xFFFFDADF),
  onPrimaryContainer = Color(0xFF3A0010),
  secondary = FujiCyan,
  tertiary = FujiMint,
  background = FujiCloud,
  onBackground = FujiInk,
  surface = FujiWhite,
  onSurface = FujiInk,
  surfaceVariant = FujiMist,
  onSurfaceVariant = FujiSlate,
  outline = Color(0xFFB4BDCC)
)

private val DarkColors = darkColorScheme(
  primary = Color(0xFFFFB2BE),
  onPrimary = FujiWhite,
  primaryContainer = FujiRedDeep,
  onPrimaryContainer = Color(0xFFFFD9DE),
  secondary = Color(0xFF9BCBFF),
  tertiary = Color(0xFF89F0E1),
  background = Color(0xFF0B0D12),
  onBackground = Color(0xFFE4E7EE),
  surface = Color(0xFF12161E),
  onSurface = Color(0xFFE4E7EE),
  surfaceVariant = Color(0xFF222833),
  onSurfaceVariant = Color(0xFFC0C8D7),
  outline = Color(0xFF576072)
)

private val AppShapes = Shapes(
  extraSmall = RoundedCornerShape(10.dp),
  small = RoundedCornerShape(14.dp),
  medium = RoundedCornerShape(20.dp),
  large = RoundedCornerShape(26.dp),
  extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun FujifilmCamTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColors else LightColors,
    typography = Typography,
    shapes = AppShapes,
    content = content
  )
}
