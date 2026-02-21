package dev.danielc.app.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

interface AppLocaleDelegate {
  fun getApplicationLocales(): LocaleListCompat

  fun setApplicationLocales(locales: LocaleListCompat)
}

object AndroidXAppLocaleDelegate : AppLocaleDelegate {
  override fun getApplicationLocales(): LocaleListCompat = AppCompatDelegate.getApplicationLocales()

  override fun setApplicationLocales(locales: LocaleListCompat) {
    AppCompatDelegate.setApplicationLocales(locales)
  }
}
