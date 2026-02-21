package dev.danielc.feature.connect.permission

sealed interface WifiPermissionEffect {
  data object OpenSystemSettingsHint : WifiPermissionEffect
}
