plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ksp)
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
  namespace = "dev.danielc.core"
  compileSdk = 36

  defaultConfig {
    minSdk = 26
    consumerProguardFiles("consumer-rules.pro")
    buildConfigField("boolean", "USE_FAKE_CAMERA", useFakeCamera.toString())
    buildConfigField("boolean", "USE_FAKE_WIFI", useFakeWifi.toString())
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
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
    buildConfig = true
  }
}

ksp {
  arg("room.schemaLocation", "$projectDir/schemas")
  arg("room.incremental", "true")
  arg("room.expandProjection", "true")
}

dependencies {
  implementation(projects.fujifilmSdk)

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.coroutines.core)

  implementation(libs.koin.core)
  implementation(libs.androidx.work.runtime.ktx)

  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  testImplementation(libs.junit4)
  testImplementation(libs.coroutines.test)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.test.core)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.robolectric)
}
