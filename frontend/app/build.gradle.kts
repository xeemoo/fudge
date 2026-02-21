plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
}

val useFakeCamera = providers
  .gradleProperty("fujifilm.useFakeCamera")
  .orElse("false")
  .map(String::toBoolean)
  .get()
val useFakeWifi = providers
  .gradleProperty("fujifilm.useFakeWifi")
  .orElse("false")
  .map(String::toBoolean)
  .get()

android {
  namespace = "dev.danielc"
  compileSdk = 36

  defaultConfig {
    applicationId = "dev.danielc"
    minSdk = 26
    targetSdk = 34
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    vectorDrawables {
      useSupportLibrary = true
    }
    buildConfigField("boolean", "USE_FAKE_CAMERA", useFakeCamera.toString())
    buildConfigField("boolean", "USE_FAKE_WIFI", useFakeWifi.toString())
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
      )
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlinOptions {
    jvmTarget = "17"
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  composeOptions {
    kotlinCompilerExtensionVersion = "1.5.14"
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)

  implementation(projects.core)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.navigation.compose)

  implementation(composeBom)
  androidTestImplementation(composeBom)

  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.coil.compose)

  implementation(libs.koin.android)
  implementation(libs.androidx.work.runtime.ktx)

  testImplementation(libs.junit4)
  testImplementation(libs.coroutines.test)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.espresso.core)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)

  debugImplementation(libs.androidx.compose.ui.tooling)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
}

tasks.register("verifyNoHardcodedUiStrings") {
  group = "verification"
  description = "Fails build when Compose UI text/contentDescription is hardcoded in app/src/main."

  doLast {
    val sourceRoots = listOf(
      file("src/main/java"),
      file("src/main/kotlin")
    ).filter { it.exists() }

    val hardcodedPatterns = listOf(
      Regex("\\bText\\(\\s*text\\s*=\\s*\"(?:[^\"\\\\]|\\\\.)+\""),
      Regex("\\bText\\(\\s*\"(?:[^\"\\\\]|\\\\.)+\""),
      Regex("\\bcontentDescription\\s*=\\s*\"(?:[^\"\\\\]|\\\\.)+\"")
    )

    val violations = mutableListOf<String>()
    sourceRoots.forEach { root ->
      root.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .forEach { file ->
          file.readLines().forEachIndexed { index, line ->
            if (hardcodedPatterns.any { pattern -> pattern.containsMatchIn(line) }) {
              violations += "${file.path}:${index + 1}: $line"
            }
          }
        }
    }

    if (violations.isNotEmpty()) {
      throw GradleException(
        buildString {
          appendLine("Hardcoded UI strings detected. Use string resources instead:")
          violations.forEach { appendLine(it) }
        }
      )
    }
  }
}

tasks.named("check") {
  dependsOn("verifyNoHardcodedUiStrings")
}
