plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

val cmpProfile = providers.gradleProperty("cmpProfile").orNull ?: "1.10"
val androidCompileSdk = when (cmpProfile) {
  "1.10" -> 35
  "1.11" -> 36
  "1.12" -> 37
  else -> 35
}

android {
  namespace = "sample.app"
  compileSdk = androidCompileSdk

  defaultConfig {
    applicationId = "sample.app"
    minSdk = 26
    targetSdk = androidCompileSdk
    versionCode = 1
    versionName = "1.0.0"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":samples:multiplatform-showcase:composeApp"))
  implementation(libs.androidx.activityCompose)
}
