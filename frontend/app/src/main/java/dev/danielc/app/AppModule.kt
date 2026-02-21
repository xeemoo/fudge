package dev.danielc.app

import dev.danielc.app.language.AppLanguageManager
import dev.danielc.app.language.AppLocaleDelegate
import dev.danielc.app.language.AndroidXAppLocaleDelegate
import org.koin.dsl.module

val appModule = module {
  single<AppLocaleDelegate> { AndroidXAppLocaleDelegate }
  single { AppLanguageManager(get()) }
}
