package dev.danielc.app

import androidx.annotation.StringRes
import dev.danielc.BuildConfig
import dev.danielc.R
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class E2EFakeFlowTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun fakeFlow_connectListPreviewDownload() {
    assumeTrue(
      "Requires fujifilm.useFakeCamera=true and fujifilm.useFakeWifi=true",
      BuildConfig.USE_FAKE_CAMERA && BuildConfig.USE_FAKE_WIFI
    )

    composeRule.waitUntil(timeoutMillis = 5_000) {
      composeRule
        .onAllNodesWithText(string(R.string.connect_hotspot_recommended, "FUJIFILM-XT5"))
        .fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText(string(R.string.connect_hotspot_recommended, "FUJIFILM-XT5")).performClick()

    composeRule.waitUntil(timeoutMillis = 8_000) {
      composeRule.onAllNodesWithText("DSCF0001.png").fetchSemanticsNodes().isNotEmpty()
    }
    composeRule.onNodeWithText("DSCF0001.png").performClick()

    composeRule.onNodeWithText(string(R.string.preview_title)).assertIsDisplayed()
    composeRule.onNodeWithText(string(R.string.preview_download_button_download)).performClick()

    composeRule.waitUntil(timeoutMillis = 10_000) {
      composeRule.onAllNodesWithText(string(R.string.preview_download_button_queued))
        .fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithText(string(R.string.preview_download_button_downloading))
          .fetchSemanticsNodes().isNotEmpty() ||
        composeRule.onAllNodesWithText(string(R.string.preview_download_button_success))
          .fetchSemanticsNodes().isNotEmpty()
    }
  }

  private fun string(@StringRes id: Int, vararg args: Any): String {
    return composeRule.activity.getString(id, *args)
  }
}
