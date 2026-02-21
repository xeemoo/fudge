package dev.danielc.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.danielc.R
import org.junit.Rule
import org.junit.Test

class AppLaunchSmokeTest {

  @get:Rule
  val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun launch_showsConnectScreenSubtitle() {
    composeRule.onNodeWithText(composeRule.activity.getString(R.string.connect_subtitle)).assertIsDisplayed()
  }
}
