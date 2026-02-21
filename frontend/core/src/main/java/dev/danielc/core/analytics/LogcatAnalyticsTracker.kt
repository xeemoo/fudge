package dev.danielc.core.analytics

import android.util.Log

class LogcatAnalyticsTracker : AnalyticsTracker {

  override fun track(event: AnalyticsEvent) {
    val params = event.params.entries.joinToString(separator = " ") { (key, value) ->
      "$key=$value"
    }
    if (params.isBlank()) {
      Log.i(TAG, event.name)
      return
    }
    Log.i(TAG, "${event.name} $params")
  }

  companion object {
    private const val TAG = "FujifilmAnalytics"
  }
}
