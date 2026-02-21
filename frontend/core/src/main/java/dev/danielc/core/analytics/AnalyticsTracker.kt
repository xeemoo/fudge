package dev.danielc.core.analytics

interface AnalyticsTracker {
  fun track(event: AnalyticsEvent)
}

object NoOpAnalyticsTracker : AnalyticsTracker {
  override fun track(event: AnalyticsEvent) = Unit
}
