@file:OptIn(org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeCacheApi::class)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
  alias(libs.plugins.jetbrains.kotlin.multiplatform)
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.jetbrains.compose.compiler)
  alias(libs.plugins.android.kotlin.multiplatform.library)
  id("io.github.aryapreetam.parikshan") version "0.0.5"
}

kotlin {
  android {
    namespace = "com.example.app.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
    withJava()
    androidResources {
      enable = true
    }
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
    }
  }

  listOf(
    iosArm64(),
    iosSimulatorArm64(),
  ).forEach { iosTarget ->
    iosTarget.binaries.framework {
      baseName = "Shared"
      isStatic = true
      disableNativeCache(
        version = org.jetbrains.kotlin.gradle.plugin.mpp.DisableCacheInKotlinVersion.`2_4_0`,
        reason = "Resolve compilation issues with duplicate symbols in composite builds"
      )
    }
  }

  jvm()

  @OptIn(ExperimentalWasmDsl::class)
  wasmJs {
    browser()
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(compose.foundation)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.composables.icons.lucide)
      implementation(libs.composables.uri.painter)
      implementation(libs.composables.ui)
      implementation(libs.composables.unstyled)
    }
  }
}

dependencies {
    androidRuntimeClasspath(libs.compose.ui.tooling)
}

