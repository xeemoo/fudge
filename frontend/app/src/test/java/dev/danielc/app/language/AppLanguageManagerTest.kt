package dev.danielc.app.language

import androidx.core.os.LocaleListCompat
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageManagerTest {

  @Test
  fun init_readsCurrentLocaleFromDelegate() {
    val delegate = FakeAppLocaleDelegate(LocaleListCompat.forLanguageTags("zh-CN"))

    val manager = AppLanguageManager(delegate)

    assertEquals(AppLanguage.CHINESE_SIMPLIFIED, manager.currentLanguage.value)
  }

  @Test
  fun setLanguage_updatesDelegateAndState() {
    val delegate = FakeAppLocaleDelegate(LocaleListCompat.getEmptyLocaleList())
    val manager = AppLanguageManager(delegate)

    manager.setLanguage(AppLanguage.ENGLISH)

    assertEquals(AppLanguage.ENGLISH, manager.currentLanguage.value)
    assertEquals("en", delegate.getApplicationLocales()[0]?.language)
  }

  @Test
  fun refresh_syncsWhenLocaleChangedExternally() {
    val delegate = FakeAppLocaleDelegate(LocaleListCompat.getEmptyLocaleList())
    val manager = AppLanguageManager(delegate)

    delegate.setApplicationLocales(LocaleListCompat.forLanguageTags("zh-Hans-CN"))
    manager.refresh()

    assertEquals(AppLanguage.CHINESE_SIMPLIFIED, manager.currentLanguage.value)
  }

  @Test
  fun setLanguage_persistsAcrossManagerRecreation() {
    val delegate = FakeAppLocaleDelegate(LocaleListCompat.getEmptyLocaleList())
    val manager = AppLanguageManager(delegate)

    manager.setLanguage(AppLanguage.CHINESE_SIMPLIFIED)
    val recreatedManager = AppLanguageManager(delegate)

    assertEquals(AppLanguage.CHINESE_SIMPLIFIED, recreatedManager.currentLanguage.value)
  }

  @Test
  fun fromLanguageTag_unknownFallsBackToSystem() {
    assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("fr-FR"))
  }
}

private class FakeAppLocaleDelegate(
  private var locales: LocaleListCompat
) : AppLocaleDelegate {
  override fun getApplicationLocales(): LocaleListCompat = locales

  override fun setApplicationLocales(locales: LocaleListCompat) {
    this.locales = locales
  }
}
