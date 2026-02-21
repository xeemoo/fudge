package dev.danielc.app.language

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationResourceParityTest {

  @Test
  fun app_enAndZhStringKeys_areConsistent() {
    val enKeys = loadKeys("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml")
    val zhKeys = loadKeys("src/main/res/values-zh-rCN/strings.xml", "app/src/main/res/values-zh-rCN/strings.xml")

    assertEquals(enKeys, zhKeys)
  }

  @Test
  fun core_enAndZhStringKeys_areConsistent() {
    val enKeys = loadKeys("../core/src/main/res/values/strings.xml", "core/src/main/res/values/strings.xml")
    val zhKeys = loadKeys(
      "../core/src/main/res/values-zh-rCN/strings.xml",
      "core/src/main/res/values-zh-rCN/strings.xml"
    )

    assertEquals(enKeys, zhKeys)
  }

  private fun loadKeys(primaryPath: String, fallbackPath: String): Set<String> {
    val file = locateFile(primaryPath, fallbackPath)
    val regex = Regex("""<string\s+name=\"([^\"]+)\"""")
    return regex.findAll(file.readText())
      .map { match -> match.groupValues[1] }
      .toSet()
  }

  private fun locateFile(primaryPath: String, fallbackPath: String): File {
    val primary = File(primaryPath)
    if (primary.exists()) return primary

    val fallback = File(fallbackPath)
    assertTrue("Resource file not found: $primaryPath or $fallbackPath", fallback.exists())
    return fallback
  }
}
