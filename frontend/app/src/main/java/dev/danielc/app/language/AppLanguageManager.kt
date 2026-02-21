package dev.danielc.app.language

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLanguageManager(
  private val localeDelegate: AppLocaleDelegate
) {
  private val mutableCurrentLanguage = MutableStateFlow(
    AppLanguage.fromLocaleList(localeDelegate.getApplicationLocales())
  )

  val currentLanguage: StateFlow<AppLanguage> = mutableCurrentLanguage.asStateFlow()

  fun setLanguage(language: AppLanguage) {
    localeDelegate.setApplicationLocales(language.toLocaleListCompat())
    refresh()
  }

  fun refresh() {
    mutableCurrentLanguage.value = AppLanguage.fromLocaleList(localeDelegate.getApplicationLocales())
  }
}
