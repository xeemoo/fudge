package dev.danielc.sdk.legacy

import dev.danielc.common.Camlib
import dev.danielc.common.WiFiComm
import dev.danielc.fujiapp.Backend
import dev.danielc.fujiapp.MySettings
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

class LegacyNativeFujifilmSdk : FujifilmLegacySdk {

  private val handleCache = ConcurrentHashMap<String, Int>()
  private val galleryConfigLock = Any()
  @Volatile
  private var galleryConfigured = false
  @Volatile
  private var photoHandlesSnapshot: IntArray? = null

  override suspend fun isReachable(): Boolean {
    return runCatching {
      Backend.ensureNativeReady()
      val firstPingRc = Backend.cPtpFujiPing()
      if (firstPingRc == Camlib.PTP_OK) {
        logDebug("isReachable firstPing rc=$firstPingRc reachable=true")
        return@runCatching true
      }

      val killSwitch = Backend.cGetKillSwitch()
      val networkHandle = WiFiComm.getNetworkHandle()
      val currentCameraIp = MySettings.getIPAddress()
      logDebug(
        "isReachable firstPing rc=$firstPingRc reachable=false " +
          "killSwitch=$killSwitch networkHandle=$networkHandle cameraIp=$currentCameraIp"
      )

      if (networkHandle < 0) {
        return@runCatching false
      }

      if (killSwitch) {
        if (!tryConnectWifiWithFallback()) {
          return@runCatching false
        }
      }

      invalidateGalleryConfig()
      val setupRc = Backend.cFujiSetup()
      logDebug("isReachable cFujiSetup rc=$setupRc")
      if (setupRc != Camlib.PTP_OK) {
        invalidateGalleryConfig()
        return@runCatching false
      }

      val secondPingRc = Backend.cPtpFujiPing()
      val reachable = secondPingRc == Camlib.PTP_OK
      logDebug("isReachable secondPing rc=$secondPingRc reachable=$reachable")
      reachable
    }.onFailure { throwable ->
      logWarn("isReachable failed message=${throwable.message}", throwable)
      if (throwable.isMissingNativeLibrary()) {
        throw IllegalStateException("Legacy native library 'fudge' is not available", throwable)
      }
    }.getOrDefault(false)
  }

  override suspend fun fetchPhotoList(): List<LegacyPhotoDto> {
    Backend.ensureNativeReady()
    ensureGalleryConfigured()
    val handles = getPhotoHandlesSnapshot(forceRefresh = true)
    return mapPhotoPage(handles = handles, offset = 0, limit = handles.size)
  }

  override suspend fun fetchPhotoListPage(offset: Int, limit: Int): List<LegacyPhotoDto> {
    if (offset < 0 || limit <= 0) {
      return emptyList()
    }
    Backend.ensureNativeReady()
    ensureGalleryConfigured()
    val handles = getPhotoHandlesSnapshot(forceRefresh = offset == 0)
    return mapPhotoPage(handles = handles, offset = offset, limit = limit)
  }

  private fun mapPhotoPage(
    handles: IntArray,
    offset: Int,
    limit: Int
  ): List<LegacyPhotoDto> {
    if (offset >= handles.size || limit <= 0) {
      return emptyList()
    }
    val endExclusive = (offset + limit).coerceAtMost(handles.size)
    val result = ArrayList<LegacyPhotoDto>(endExclusive - offset)
    for (index in offset until endExclusive) {
      val handle = handles[index]
      val info = Camlib.cGetObjectInfo(handle)
      val photoKey = handle.toString()
      handleCache[photoKey] = handle

      result += LegacyPhotoDto(
        photoKey = photoKey,
        fileName = info?.optStringOrNull("filename"),
        takenAtEpochMillis = info?.extractTakenAtEpochMillis(),
        fileSizeBytes = info?.optLongOrNull("compressedSize"),
        mimeType = info?.extractMimeType()
      )
    }
    return result
  }

  private fun getPhotoHandlesSnapshot(forceRefresh: Boolean): IntArray {
    if (forceRefresh) {
      clearPhotoHandlesSnapshot()
    }
    photoHandlesSnapshot?.let { return it }

    synchronized(galleryConfigLock) {
      photoHandlesSnapshot?.let { return it }
      val handles = Backend.cFujiGetObjectHandles()
        ?: throw FujifilmLegacySdkException("Failed to fetch object handles from legacy backend")
      logDebug("fetchPhotoList handles=${handles.size}")
      runCatching {
        Camlib.cPtpObjectServiceStart(handles)
        logDebug("fetchPhotoList cPtpObjectServiceStart ok count=${handles.size}")
      }.onFailure { throwable ->
        logWarn("fetchPhotoList cPtpObjectServiceStart failed message=${throwable.message}", throwable)
      }
      handleCache.clear()
      photoHandlesSnapshot = handles
      return handles
    }
  }

  override suspend fun fetchThumbnail(photoKey: String): ByteArray {
    Backend.ensureNativeReady()
    ensureGalleryConfigured()
    val handle = resolveHandle(photoKey)

    val first = Backend.cFujiGetThumb(handle)
    if (first != null) {
      logDebug("fetchThumbnail handle=$handle size=${first.size} photoKey=$photoKey")
      return first
    }

    // Match legacy flow: if gallery state becomes stale, re-configure once and retry.
    invalidateGalleryConfig()
    ensureGalleryConfigured()
    val second = Backend.cFujiGetThumb(handle)
    if (second != null) {
      logDebug("fetchThumbnail retry handle=$handle size=${second.size} photoKey=$photoKey")
      return second
    }
    throw FujifilmLegacySdkException("Failed to fetch thumbnail for photoKey=$photoKey handle=$handle")
  }

  override suspend fun openPreviewStream(photoKey: String): InputStream {
    val thumbnail = fetchThumbnail(photoKey)
    return ByteArrayInputStream(thumbnail)
  }

  override suspend fun openOriginalStream(photoKey: String): InputStream {
    Backend.ensureNativeReady()
    val handle = resolveHandle(photoKey)

    val infoJson = Backend.cFujiBeginDownloadGetObjectInfo(handle)
      ?: throw FujifilmLegacySdkException("Failed to query object info before download for photoKey=$photoKey")

    val metadata = JSONObject(infoJson)
    val size = metadata.optLongOrNull("compressedSize")?.toInt()
      ?: throw FujifilmLegacySdkException("Invalid compressedSize in object info for photoKey=$photoKey")

    if (size <= 0) {
      throw FujifilmLegacySdkException("compressedSize must be > 0 for photoKey=$photoKey")
    }

    val buffer = ByteArray(size)
    val rc = Backend.cFujiGetFile(handle, buffer, size)
    if (rc != Camlib.PTP_OK) {
      throw FujifilmLegacySdkException("Failed to open original stream for photoKey=$photoKey, rc=$rc")
    }

    return ByteArrayInputStream(buffer)
  }

  private fun resolveHandle(photoKey: String): Int {
    handleCache[photoKey]?.let { return it }

    return photoKey.toIntOrNull()
      ?: throw FujifilmLegacySdkException("photoKey=$photoKey is not a valid legacy handle")
  }

  private fun ensureGalleryConfigured() {
    if (galleryConfigured) {
      return
    }
    synchronized(galleryConfigLock) {
      if (galleryConfigured) {
        return
      }
      val rc = Backend.cFujiConfigImageGallery()
      logDebug("ensureGalleryConfigured cFujiConfigImageGallery rc=$rc")
      if (rc != Camlib.PTP_OK) {
        throw FujifilmLegacySdkException("Failed to configure image gallery rc=$rc")
      }
      galleryConfigured = true
    }
  }

  private fun invalidateGalleryConfig() {
    synchronized(galleryConfigLock) {
      galleryConfigured = false
      clearPhotoHandlesSnapshot()
      handleCache.clear()
    }
  }

  private fun clearPhotoHandlesSnapshot() {
    photoHandlesSnapshot = null
  }

  private fun JSONObject.optStringOrNull(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
  }

  private fun JSONObject.optLongOrNull(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    return runCatching { optLong(name) }.getOrNull()
  }

  private fun JSONObject.extractTakenAtEpochMillis(): Long? {
    val directEpoch = listOf(
      "takenAtEpochMillis",
      "capturedAtEpochMillis",
      "captureEpochMillis",
      "timestamp"
    ).firstNotNullOfOrNull { key -> optLongOrNull(key) }
    if (directEpoch != null && directEpoch > 0) return directEpoch

    return null
  }

  private fun JSONObject.extractMimeType(): String? {
    val format = optLongOrNull("format_int")?.toInt() ?: return null
    return when (format) {
      Camlib.PTP_OF_JPEG -> "image/jpeg"
      Camlib.PTP_OF_RAW -> "image/x-fuji-raw"
      Camlib.PTP_OF_MOV -> "video/quicktime"
      else -> null
    }
  }

  private fun tryConnectWifiWithFallback(): Boolean {
    val preferredIp = MySettings.getIPAddress()
    val dynamicCandidates = MySettings.getIpCandidates().toList()
    val candidateIps = linkedSetOf(
      preferredIp,
      *dynamicCandidates.toTypedArray(),
      "192.168.0.1",
      "192.168.1.1",
      "192.168.2.1"
    ).filter(::isUsableCameraIp)

    logDebug("isReachable tryConnect candidates=${candidateIps.joinToString()}")

    repeat(2) { round ->
      candidateIps.forEach { ip ->
        MySettings.setIPAddress(ip)
        val handleBeforeTry = WiFiComm.getNetworkHandle()
        val connectRc = Backend.cTryConnectWiFi(5)
        logDebug(
          "isReachable cTryConnectWiFi rc=$connectRc ip=$ip round=$round " +
            "networkHandleBeforeTry=$handleBeforeTry"
        )
        if (connectRc == Camlib.PTP_OK) {
          return true
        }
        runCatching { Thread.sleep(250) }
      }
    }
    return false
  }
}

private const val LEGACY_SDK_TAG = "LegacyNativeFujifilmSdk"

private fun logDebug(message: String) {
  runCatching { Log.d(LEGACY_SDK_TAG, message) }
}

private fun logWarn(message: String, throwable: Throwable?) {
  runCatching {
    if (throwable == null) {
      Log.w(LEGACY_SDK_TAG, message)
    } else {
      Log.w(LEGACY_SDK_TAG, message, throwable)
    }
  }
}

private fun Throwable.isMissingNativeLibrary(): Boolean {
  if (this is UnsatisfiedLinkError) {
    return true
  }
  if (message?.contains("Legacy native library 'fudge' is not available") == true) {
    return true
  }
  if (message?.contains("libfudge.so") == true) {
    return true
  }
  return cause?.isMissingNativeLibrary() == true
}

private fun isUsableCameraIp(ip: String?): Boolean {
  if (ip.isNullOrBlank()) return false
  if (ip == "0.0.0.0") return false
  if (ip == "255.255.255.255") return false
  return ip.count { it == '.' } == 3
}
