package dev.danielc.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dev.danielc.app.language.AppLanguageManager
import dev.danielc.core.analytics.AnalyticsEvent
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.work.DummyWorker
import dev.danielc.ui.theme.FujifilmCamTheme
import org.koin.core.context.GlobalContext

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    GlobalContext.get().get<AppLanguageManager>().refresh()
    super.onCreate(savedInstanceState)

    GlobalContext.get().get<AnalyticsTracker>().track(AnalyticsEvent.AppOpen)
    enqueueDummyWorker()

    setContent {
      FujifilmCamTheme {
        AppNavGraph()
      }
    }
  }

  override fun onResume() {
    super.onResume()
    GlobalContext.get().get<AppLanguageManager>().refresh()
  }

  private fun enqueueDummyWorker() {
    val request = OneTimeWorkRequestBuilder<DummyWorker>().build()
    WorkManager.getInstance(applicationContext).enqueueUniqueWork(
      DummyWorker.UNIQUE_WORK_NAME,
      ExistingWorkPolicy.KEEP,
      request
    )
  }
}
