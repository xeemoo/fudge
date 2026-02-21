package dev.danielc.app

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.danielc.R
import dev.danielc.app.language.AppLanguageManager
import dev.danielc.core.analytics.AnalyticsTracker
import dev.danielc.core.data.CameraSessionManager
import dev.danielc.core.data.HotspotHistoryRepository
import dev.danielc.core.data.ThumbnailRepository
import dev.danielc.core.domain.usecase.EnqueueDownloadUseCase
import dev.danielc.core.domain.ErrorMessageMapper
import dev.danielc.core.domain.usecase.FetchPhotoListUseCase
import dev.danielc.core.domain.usecase.FetchPreviewImageUseCase
import dev.danielc.core.domain.usecase.IsDownloadedUseCase
import dev.danielc.core.domain.usecase.ObserveDownloadStateUseCase
import dev.danielc.core.domain.usecase.ObserveQueuePhotoStatusUseCase
import dev.danielc.core.domain.usecase.ObserveQueueStatsUseCase
import dev.danielc.core.wifi.WifiPermissionChecker
import dev.danielc.core.wifi.WifiConnector
import dev.danielc.core.wifi.WifiConnectionMonitor
import dev.danielc.core.wifi.WifiScanner
import dev.danielc.feature.connect.permission.ConnectPermissionRoute
import dev.danielc.feature.photolist.PhotoListRoute
import dev.danielc.feature.preview.PreviewRoute
import dev.danielc.feature.settings.SettingsScreen
import dev.danielc.feature.settings.about.AboutScreen
import dev.danielc.feature.settings.help.HelpScreen
import dev.danielc.feature.settings.language.LanguageScreen
import dev.danielc.ui.theme.AppBackground
import org.koin.core.context.GlobalContext

object AppRoutes {
  const val MAIN = "main"
  const val CONNECT = "connect"
  const val SETTINGS = "settings"
  const val LANGUAGE = "language"
  const val HELP = "help"
  const val ABOUT = "about"
  const val PHOTO_LIST = "photo_list"
  const val PREVIEW_BASE = "preview"
  const val PREVIEW_ARG_PHOTO_ID = "photoId"
  const val PREVIEW = "$PREVIEW_BASE/{$PREVIEW_ARG_PHOTO_ID}"
}

@Composable
fun AppNavGraph(navController: NavHostController = rememberNavController()) {
  NotificationPermissionGate()

  val wifiPermissionChecker = remember {
    GlobalContext.get().get<WifiPermissionChecker>()
  }
  val wifiScanner = remember {
    GlobalContext.get().get<WifiScanner>()
  }
  val wifiConnector = remember {
    GlobalContext.get().get<WifiConnector>()
  }
  val wifiConnectionMonitor = remember {
    GlobalContext.get().get<WifiConnectionMonitor>()
  }
  val hotspotHistoryRepository = remember {
    GlobalContext.get().get<HotspotHistoryRepository>()
  }
  val cameraSessionManager = remember {
    GlobalContext.get().get<CameraSessionManager>()
  }
  val fetchPhotoListUseCase = remember {
    GlobalContext.get().get<FetchPhotoListUseCase>()
  }
  val errorMessageMapper = remember {
    GlobalContext.get().get<ErrorMessageMapper>()
  }
  val isDownloadedUseCase = remember {
    GlobalContext.get().get<IsDownloadedUseCase>()
  }
  val thumbnailRepository = remember {
    GlobalContext.get().get<ThumbnailRepository>()
  }
  val fetchPreviewImageUseCase = remember {
    GlobalContext.get().get<FetchPreviewImageUseCase>()
  }
  val observeDownloadStateUseCase = remember {
    GlobalContext.get().get<ObserveDownloadStateUseCase>()
  }
  val enqueueDownloadUseCase = remember {
    GlobalContext.get().get<EnqueueDownloadUseCase>()
  }
  val observeQueueStatsUseCase = remember {
    GlobalContext.get().get<ObserveQueueStatsUseCase>()
  }
  val observeQueuePhotoStatusUseCase = remember {
    GlobalContext.get().get<ObserveQueuePhotoStatusUseCase>()
  }
  val analyticsTracker = remember {
    GlobalContext.get().get<AnalyticsTracker>()
  }
  val appLanguageManager = remember {
    GlobalContext.get().get<AppLanguageManager>()
  }

  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination
  val showBottomBar = currentDestination
    ?.hierarchy
    ?.any { it.route == AppRoutes.MAIN } == true

  AppBackground {
    Scaffold(
      containerColor = Color.Transparent,
      bottomBar = {
        if (showBottomBar) {
          MainBottomBar(
            currentDestination = currentDestination,
            onNavigate = { route ->
              navController.navigateToMainTab(route)
            }
          )
        }
      }
    ) { innerPadding ->
      NavHost(
        navController = navController,
        startDestination = AppRoutes.MAIN,
        modifier = Modifier.padding(innerPadding)
      ) {
        navigation(
          startDestination = AppRoutes.CONNECT,
          route = AppRoutes.MAIN
        ) {
          composable(AppRoutes.CONNECT) {
            ConnectPermissionRoute(
              wifiPermissionChecker = wifiPermissionChecker,
              wifiScanner = wifiScanner,
              wifiConnector = wifiConnector,
              wifiConnectionMonitor = wifiConnectionMonitor,
              cameraSessionManager = cameraSessionManager,
              hotspotHistoryRepository = hotspotHistoryRepository,
              analyticsTracker = analyticsTracker,
              onConnected = {
                navController.navigate(AppRoutes.PHOTO_LIST)
              }
            )
          }
          composable(AppRoutes.SETTINGS) {
            SettingsScreen(
              onNavigateToLanguage = {
                navController.navigate(AppRoutes.LANGUAGE)
              },
              onNavigateToHelp = {
                navController.navigate(AppRoutes.HELP)
              },
              onNavigateToAbout = {
                navController.navigate(AppRoutes.ABOUT)
              }
            )
          }
        }
        composable(AppRoutes.LANGUAGE) {
          val currentLanguage by appLanguageManager.currentLanguage.collectAsState()
          LanguageScreen(
            currentLanguage = currentLanguage,
            onBack = {
              navController.popBackStack()
            },
            onSelectLanguage = { language ->
              appLanguageManager.setLanguage(language)
              navController.popBackStack()
            }
          )
        }
        composable(AppRoutes.HELP) {
          HelpScreen(
            onBack = {
              navController.popBackStack()
            }
          )
        }
        composable(AppRoutes.ABOUT) {
          AboutScreen(
            onBack = {
              navController.popBackStack()
            }
          )
        }
        composable(AppRoutes.PHOTO_LIST) {
          val sessionState by rememberCameraSessionState(cameraSessionManager)
          Box(modifier = Modifier.fillMaxSize()) {
            PhotoListRoute(
              fetchPhotoListUseCase = fetchPhotoListUseCase,
              cameraSessionManager = cameraSessionManager,
              wifiConnectionMonitor = wifiConnectionMonitor,
              isDownloadedUseCase = isDownloadedUseCase,
              observeQueueStatsUseCase = observeQueueStatsUseCase,
              observeQueuePhotoStatusUseCase = observeQueuePhotoStatusUseCase,
              errorMessageMapper = errorMessageMapper,
              analyticsTracker = analyticsTracker,
              thumbnailRepository = thumbnailRepository,
              onBack = {
                navController.popBackStack()
              },
              onNavigateToPreview = { photoId ->
                navController.navigateToPreview(photoId)
              }
            )
            SessionGate(
              sessionState = sessionState,
              onNavigateToConnect = {
                navController.navigateToConnect()
              },
              modifier = Modifier.align(Alignment.TopCenter)
            )
          }
        }
        composable(
          route = AppRoutes.PREVIEW,
          arguments = listOf(
            navArgument(AppRoutes.PREVIEW_ARG_PHOTO_ID) {
              type = NavType.StringType
              nullable = false
            }
          )
        ) { backStackEntry ->
          val photoId = Uri.decode(
            backStackEntry.arguments
              ?.getString(AppRoutes.PREVIEW_ARG_PHOTO_ID)
              .orEmpty()
          )
          val sessionState by rememberCameraSessionState(cameraSessionManager)
          Box(modifier = Modifier.fillMaxSize()) {
            PreviewRoute(
              photoId = photoId,
              fetchPreviewImageUseCase = fetchPreviewImageUseCase,
              fetchPhotoListUseCase = fetchPhotoListUseCase,
              observeDownloadStateUseCase = observeDownloadStateUseCase,
              enqueueDownloadUseCase = enqueueDownloadUseCase,
              errorMessageMapper = errorMessageMapper,
              analyticsTracker = analyticsTracker,
              onBack = {
                navController.popBackStack()
              }
            )
            SessionGate(
              sessionState = sessionState,
              onNavigateToConnect = {
                navController.navigateToConnect()
              },
              modifier = Modifier.align(Alignment.TopCenter)
            )
          }
        }
      }
    }
  }
}

@Composable
private fun MainBottomBar(
  currentDestination: NavDestination?,
  onNavigate: (String) -> Unit
) {
  val tabs = listOf(
    MainTab(
      route = AppRoutes.CONNECT,
      labelResId = R.string.main_tab_connect,
      icon = Icons.Filled.Wifi
    ),
    MainTab(
      route = AppRoutes.SETTINGS,
      labelResId = R.string.main_tab_settings,
      icon = Icons.Filled.Settings
    )
  )

  Box(
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
  ) {
    NavigationBar(
      modifier = Modifier.clip(RoundedCornerShape(22.dp)),
      containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
      tonalElevation = 10.dp
    ) {
      tabs.forEach { tab ->
        val selected = currentDestination
          ?.hierarchy
          ?.any { it.route == tab.route } == true
        NavigationBarItem(
          selected = selected,
          onClick = {
            onNavigate(tab.route)
          },
          colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onPrimary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
          ),
          icon = {
            Icon(
              imageVector = tab.icon,
              contentDescription = stringResource(id = tab.labelResId)
            )
          },
          label = {
            Text(text = stringResource(id = tab.labelResId))
          }
        )
      }
    }
  }
}

private data class MainTab(
  val route: String,
  val labelResId: Int,
  val icon: androidx.compose.ui.graphics.vector.ImageVector
)

private fun NavHostController.navigateToMainTab(route: String) {
  navigate(route) {
    launchSingleTop = true
    restoreState = true
    popUpTo(AppRoutes.MAIN) {
      inclusive = false
      saveState = true
    }
  }
}

private fun NavHostController.navigateToConnect() {
  navigateToMainTab(AppRoutes.CONNECT)
}

private fun NavHostController.navigateToPreview(photoId: String) {
  val encoded = Uri.encode(photoId)
  navigate("${AppRoutes.PREVIEW_BASE}/$encoded")
}
