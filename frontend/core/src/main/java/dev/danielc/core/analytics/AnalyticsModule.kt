package dev.danielc.core.analytics

import org.koin.dsl.module

val analyticsModule = module {
  single<AnalyticsTracker> { LogcatAnalyticsTracker() }
}
