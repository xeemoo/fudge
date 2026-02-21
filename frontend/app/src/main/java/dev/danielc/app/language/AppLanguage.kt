package dev.danielc.app.language

import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(private val languageTag: String?) {
  SYSTEM(languageTag = null),
  ENGLISH(languageTag = "en"),
  CHINESE_SIMPLIFIED(languageTag = "zh-CN");

  fun toLocaleListCompat(): LocaleListCompat {
    return if (languageTag == null) {
      LocaleListCompat.getEmptyLocaleList()
    } else {
      LocaleListCompat.forLanguageTags(languageTag)
    }
  }

  companion object {
    fun fromLocaleList(locales: LocaleListCompat): AppLanguage {
      val locale = locales[0] ?: return SYSTEM
      if (locale.language.equals("en", ignoreCase = true)) return ENGLISH
      if (locale.language.equals("zh", ignoreCase = true)) return CHINESE_SIMPLIFIED
      return fromLanguageTag(locale.toLanguageTag())
    }

    fun fromLanguageTag(tag: String?): AppLanguage {
      val normalized = tag
        ?.trim()
        ?.lowercase(Locale.ROOT)
        .orEmpty()
      if (normalized.isEmpty()) return SYSTEM

      return when {
        normalized == "en" || normalized.startsWith("en-") -> ENGLISH
        normalized == "zh" || normalized.startsWith("zh-cn") || normalized.startsWith("zh-hans") -> {
          CHINESE_SIMPLIFIED
        }
        else -> SYSTEM
      }
    }
  }
}
