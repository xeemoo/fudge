package dev.danielc.core.wifi

import android.content.Context
import dev.danielc.core.BuildConfig
import dev.danielc.core.wifi.fake.FakeWifiConnectionMonitor
import dev.danielc.core.wifi.fake.FakeWifiConnector
import dev.danielc.core.wifi.fake.FakeWifiPermissionChecker
import dev.danielc.core.wifi.fake.FakeWifiRuntimeState
import dev.danielc.core.wifi.fake.FakeWifiScanner
import org.koin.dsl.module

val wifiModule = module {
  if (BuildConfig.USE_FAKE_WIFI) {
    single { FakeWifiRuntimeState() }
    single<WifiPermissionChecker> { FakeWifiPermissionChecker() }
    single<WifiScanner> { FakeWifiScanner(get(), get()) }
    single<WifiConnector> { FakeWifiConnector(get()) }
    single<WifiConnectionMonitor> { FakeWifiConnectionMonitor(get()) }
  } else {
    single<WifiPermissionChecker> { AndroidWifiPermissionChecker(get<Context>()) }
    single<WifiScanner> { WifiScannerImpl(get<Context>(), get()) }
    single<WifiConnector> { WifiConnectorImpl(get<Context>()) }
    single<WifiConnectionMonitor> { WifiConnectionMonitorImpl(get<Context>()) }
  }
}
