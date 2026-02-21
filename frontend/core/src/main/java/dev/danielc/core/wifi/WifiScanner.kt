package dev.danielc.core.wifi

import dev.danielc.core.wifi.model.ScanResult
import kotlinx.coroutines.flow.Flow

interface WifiScanner {
  fun scanOnce(): suspend () -> ScanResult
  fun observeScan(intervalMs: Long = 3_000): Flow<ScanResult>
}
