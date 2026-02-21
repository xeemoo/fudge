package dev.danielc.app.language

import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

fun currentAppLocale(): Locale {
  return AppCompatDelegate.getApplicationLocales()[0] ?: Locale.getDefault()
}
