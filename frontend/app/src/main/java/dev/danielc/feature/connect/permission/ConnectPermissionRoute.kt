package dev.danielc.feature.connect.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.data.CameraSessionManager
import androidx.core.app.ActivityCompat
import dev.danielc.core.data.HotspotHistoryRepository
import dev.danielc.core.wifi.WifiPermissionChecker
import dev.danielc.core.wifi.WifiConnector
import dev.danielc.core.wifi.WifiConnectionMonitor
import dev.danielc.core.wifi.WifiScanner
import dev.danielc.core.wifi.model.WifiPermissionState
import dev.danielc.feature.connect.ConnectContract
import dev.danielc.feature.connect.ConnectScreen
import dev.danielc.feature.connect.ConnectViewModel
import dev.danielc.ui.resolve
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ConnectPermissionRoute(
  wifiPermissionChecker: WifiPermissionChecker,
  wifiScanner: WifiScanner,
  wifiConnector: WifiConnector,
  wifiConnectionMonitor: WifiConnectionMonitor,
  cameraSessionManager: CameraSessionManager,
  hotspotHistoryRepository: HotspotHistoryRepository,
  analyticsTracker: AnalyticsTracker,
  onConnected: (ssid: String) -> Unit
) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }
  val requiredPermissions = remember(wifiPermissionChecker) { wifiPermissionChecker.requiredPermissions() }
  var hasRequestedPermissions by rememberSaveable {
    mutableStateOf(context.hasRequestedWifiPermissions(requiredPermissions))
  }

  val viewModel: ConnectViewModel = viewModel(
    factory = remember(wifiScanner, wifiConnector, wifiConnectionMonitor, cameraSessionManager, hotspotHistoryRepository, analyticsTracker) {
      ConnectViewModelFactory(
        wifiScanner = wifiScanner,
        wifiConnector = wifiConnector,
        wifiConnectionMonitor = wifiConnectionMonitor,
        cameraSessionManager = cameraSessionManager,
        hotspotHistoryRepository = hotspotHistoryRepository,
        analyticsTracker = analyticsTracker
      )
    }
  )
  val state by viewModel.state.collectAsState()

  val permissionRequestLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) {
    viewModel.accept(
      ConnectContract.Intent.OnPermissionChanged(
        resolvePermissionState(
          currentState = wifiPermissionChecker.currentState(),
          requiredPermissions = requiredPermissions,
          activity = activity,
          hasRequestedPermissions = hasRequestedPermissions
        )
      )
    )
  }

  LaunchedEffect(viewModel, requiredPermissions, hasRequestedPermissions, activity, wifiPermissionChecker) {
    viewModel.accept(
      ConnectContract.Intent.OnEnter(
        permission = resolvePermissionState(
          currentState = wifiPermissionChecker.currentState(),
          requiredPermissions = requiredPermissions,
          activity = activity,
          hasRequestedPermissions = hasRequestedPermissions
        )
      )
    )
  }

  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, wifiPermissionChecker, requiredPermissions, activity, hasRequestedPermissions) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        viewModel.accept(
          ConnectContract.Intent.OnPermissionChanged(
            resolvePermissionState(
              currentState = wifiPermissionChecker.currentState(),
              requiredPermissions = requiredPermissions,
              activity = activity,
              hasRequestedPermissions = hasRequestedPermissions
            )
          )
        )
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  LaunchedEffect(viewModel, context) {
    viewModel.effect.collectLatest { effect ->
      when (effect) {
        is ConnectContract.Effect.NavigateToPhotoList -> onConnected(effect.ssid)
        ConnectContract.Effect.OpenAppSettings -> context.openApplicationSettings()
        is ConnectContract.Effect.Toast -> {
          Toast.makeText(context, effect.message.resolve(context), Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  ConnectScreen(
    state = state,
    onIntent = viewModel::accept,
    onRequestPermission = {
      context.markWifiPermissionsRequested(requiredPermissions)
      hasRequestedPermissions = true
      permissionRequestLauncher.launch(requiredPermissions)
    }
  )
}

private class ConnectViewModelFactory(
  private val wifiScanner: WifiScanner,
  private val wifiConnector: WifiConnector,
  private val wifiConnectionMonitor: WifiConnectionMonitor,
  private val cameraSessionManager: CameraSessionManager,
  private val hotspotHistoryRepository: HotspotHistoryRepository,
  private val analyticsTracker: AnalyticsTracker
) : ViewModelProvider.Factory {

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    check(modelClass.isAssignableFrom(ConnectViewModel::class.java)) {
      "Unknown ViewModel class: ${modelClass.name}"
    }
    return ConnectViewModel(
      wifiScanner = wifiScanner,
      wifiConnector = wifiConnector,
      wifiConnectionMonitor = wifiConnectionMonitor,
      cameraSessionManager = cameraSessionManager,
      hotspotHistoryRepository = hotspotHistoryRepository,
      analyticsTracker = analyticsTracker
    ) as T
  }
}

private fun resolvePermissionState(
  currentState: WifiPermissionState,
  requiredPermissions: Array<String>,
  activity: Activity?,
  hasRequestedPermissions: Boolean
): WifiPermissionState {
  if (currentState == WifiPermissionState.GRANTED) {
    return WifiPermissionState.GRANTED
  }

  if (!hasRequestedPermissions || activity == null) {
    return WifiPermissionState.DENIED_CAN_REQUEST
  }

  val isPermanentlyDenied = requiredPermissions.any { permission ->
    activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
      !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
  }

  return if (isPermanentlyDenied) {
    WifiPermissionState.DENIED_PERMANENT
  } else {
    WifiPermissionState.DENIED_CAN_REQUEST
  }
}

private fun Context.openApplicationSettings() {
  val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    .setData(Uri.fromParts("package", packageName, null))
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  startActivity(intent)
}

private fun Context.hasRequestedWifiPermissions(permissions: Array<String>): Boolean {
  val prefs = applicationContext.getSharedPreferences(
    WIFI_PERMISSION_PREFS_NAME,
    Context.MODE_PRIVATE
  )
  return permissions.any { permission ->
    prefs.getBoolean("$WIFI_PERMISSION_REQUESTED_PREFIX$permission", false)
  }
}

private fun Context.markWifiPermissionsRequested(permissions: Array<String>) {
  val prefs = applicationContext.getSharedPreferences(
    WIFI_PERMISSION_PREFS_NAME,
    Context.MODE_PRIVATE
  )
  prefs.edit().apply {
    permissions.forEach { permission ->
      putBoolean("$WIFI_PERMISSION_REQUESTED_PREFIX$permission", true)
    }
  }.apply()
}

private tailrec fun Context.findActivity(): Activity? {
  return when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
  }
}

private const val WIFI_PERMISSION_PREFS_NAME = "wifi_permission_state"
private const val WIFI_PERMISSION_REQUESTED_PREFIX = "requested_"
