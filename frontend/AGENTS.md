# Frontend Project Guide

## Project Overview
The `frontend` directory is an Android multi-module project (project name: `FujifilmCam`). Its goal is to connect to Fujifilm cameras over Wi-Fi and support photo list browsing, preview, and download.

## Module Structure
- `app`: Application entry point and UI layer (Jetpack Compose, Navigation, feature screens, and ViewModels).
- `core`: Core business layer (domain use cases, repositories, Wi-Fi capabilities, Room database, WorkManager download queue, and MVI base classes).
- `fujifilm-sdk`: Wrapper for the legacy Fujifilm SDK and native bridge (includes `externalNativeBuild` CMake configuration).

## Tech Stack and Key Dependencies
- Kotlin + Java 17 (AGP `8.5.2`, Kotlin `1.9.24`).
- Jetpack Compose + Material3 + Navigation Compose + Coil.
- Koin (dependency injection).
- Room + KSP (local persistence and schema export).
- WorkManager (download task scheduling/execution).
- Coroutines + Flow.
- Testing: JUnit4, Robolectric, AndroidX Test, Compose UI Test.

## Core Business Flows
1. Connect screen: permission check -> hotspot scan -> connect to camera Wi-Fi -> session readiness verification.
2. List screen: fetch remote photos -> show thumbnails -> display download states and queue stats.
3. Preview screen: load preview by `photoId` -> trigger download -> observe download state.

## Key Entry Files
- `app/src/main/java/com/fujifilmcam/app/FujifilmCamApplication.kt`
- `app/src/main/java/com/fujifilmcam/app/AppNavGraph.kt`
- `core/src/main/java/com/fujifilmcam/core/di/CoreModule.kt`
- `core/src/main/java/com/fujifilmcam/core/data/DataModule.kt`
- `core/src/main/java/com/fujifilmcam/core/mvi/MviViewModel.kt`

## Common Commands (run in `frontend`)
- Build Debug: `./gradlew :app:assembleDebug`
- Compile Kotlin: `./gradlew --console=plain :app:compileDebugKotlin`
- Release minification check: `./gradlew --console=plain :app:minifyReleaseWithR8`
- Run all unit tests: `./gradlew test`
- Core unit tests: `./gradlew :core:testDebugUnitTest`
- App unit tests: `./gradlew :app:testDebugUnitTest`

## Runtime Fake Switches
- The project supports runtime fake mode via Gradle properties + `BuildConfig` fields (not build-type only).
- Properties in `gradle.properties`:
  - `fujifilm.useFakeCamera=false`
  - `fujifilm.useFakeWifi=false`
- Override per run:
  - `-Pfujifilm.useFakeCamera=true`
  - `-Pfujifilm.useFakeWifi=true`
- Switching behavior:
  - `core/data/DataModule.kt`: toggles `FujifilmCameraClient` between real SDK and `FakeFujifilmCameraClient`.
  - `core/wifi/WifiModule.kt`: toggles Wi-Fi stack between Android implementations and fake implementations.
- Typical fake-mode verification commands:
  - `./gradlew --console=plain :core:testDebugUnitTest -Pfujifilm.useFakeCamera=true -Pfujifilm.useFakeWifi=true`
  - `./gradlew --console=plain :app:compileDebugAndroidTestKotlin -Pfujifilm.useFakeCamera=true -Pfujifilm.useFakeWifi=true`

## Development Conventions (Recommended)
- Put new business logic in `core` first (domain/data/usecase); keep `app` focused on UI orchestration and state presentation.
- Follow the existing Feature structure: `Contract + ViewModel (MVI) + Route/Screen`.
- Prefer adding new dependencies through Koin modules, and avoid constructing complex objects directly in Compose code.
- When changing Room schema, also verify exported files in `core/schemas` and related tests.
