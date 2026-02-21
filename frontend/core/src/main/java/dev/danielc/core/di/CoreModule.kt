package dev.danielc.core.di

import dev.danielc.core.analytics.analyticsModule
import dev.danielc.core.data.dataModule
import dev.danielc.core.db.dbModule
import dev.danielc.core.wifi.wifiModule
import dev.danielc.core.work.workManagerModule
import org.koin.dsl.module

val coreModule = module {
  includes(analyticsModule, workManagerModule, dbModule, dataModule, wifiModule)
}
