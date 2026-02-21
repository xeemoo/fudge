package dev.danielc.core.analytics

sealed class AnalyticsEvent(
  val name: String,
  open val params: Map<String, Any> = emptyMap()
) {
  data object AppOpen : AnalyticsEvent("app_open")

  data object WifiScanStart : AnalyticsEvent("wifi_scan_start")

  data class WifiScanResultCount(val resultCount: Int) : AnalyticsEvent(
    name = "wifi_scan_result_count",
    params = mapOf(PARAM_RESULT_COUNT to resultCount)
  )

  data class WifiConnectClick(val source: WifiConnectSource) : AnalyticsEvent(
    name = "wifi_connect_click",
    params = mapOf(PARAM_SOURCE to source.value)
  )

  data object WifiConnectSuccess : AnalyticsEvent("wifi_connect_success")

  data class WifiConnectFail(val reason: WifiConnectFailReason) : AnalyticsEvent(
    name = "wifi_connect_fail",
    params = mapOf(PARAM_REASON to reason.value)
  )

  data object PhotoListRequest : AnalyticsEvent("photo_list_request")
  data object PhotoListSuccess : AnalyticsEvent("photo_list_success")
  data object PhotoListFail : AnalyticsEvent("photo_list_fail")

  data object PhotoItemClick : AnalyticsEvent("photo_item_click")

  data object DownloadClick : AnalyticsEvent("download_click")
  data object DownloadEnqueueSuccess : AnalyticsEvent("download_enqueue_success")

  data class DownloadEnqueueFail(val reason: DownloadEnqueueFailReason) : AnalyticsEvent(
    name = "download_enqueue_fail",
    params = mapOf(PARAM_REASON to reason.value)
  )

  data object DownloadStart : AnalyticsEvent("download_start")
  data object DownloadSuccess : AnalyticsEvent("download_success")

  data class DownloadFail(val reason: DownloadFailReason) : AnalyticsEvent(
    name = "download_fail",
    params = mapOf(PARAM_REASON to reason.value)
  )

  data class QueueLengthChange(val queueLength: Int) : AnalyticsEvent(
    name = "queue_length_change",
    params = mapOf(PARAM_QUEUE_LENGTH to queueLength)
  )

  companion object {
    const val PARAM_RESULT_COUNT = "result_count"
    const val PARAM_SOURCE = "source"
    const val PARAM_REASON = "reason"
    const val PARAM_QUEUE_LENGTH = "queue_length"
  }
}

enum class WifiConnectSource(val value: String) {
  AVAILABLE("available"),
  HISTORY("history")
}

enum class WifiConnectFailReason(val value: String) {
  TIMEOUT("timeout"),
  AUTH("auth"),
  SYSTEM_RESTRICT("system_restrict"),
  UNKNOWN("unknown")
}

enum class DownloadEnqueueFailReason(val value: String) {
  ALREADY_DOWNLOADED("already_downloaded"),
  ALREADY_IN_QUEUE("already_in_queue"),
  UNKNOWN("unknown")
}

enum class DownloadFailReason(val value: String) {
  DISCONNECT("disconnect"),
  STORAGE_FULL("storage_full"),
  SDK_ERROR("sdk_error"),
  UNKNOWN("unknown")
}
