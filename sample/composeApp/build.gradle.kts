@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose)
  alias(libs.plugins.android.application)
}

kotlin {
  jvmToolchain(17)

  androidTarget {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    instrumentedTestVariant.sourceSetTree.set(KotlinSourceSetTree.test)
  }
  jvm()
  wasmJs {
    browser()
    binaries.executable()
  }
  listOf(
    iosX64(),
    iosArm64(),
    iosSimulatorArm64()
  ).forEach {
    it.binaries.framework {
      baseName = "ComposeApp"
      isStatic = true
    }
  }

  sourceSets {
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.ui)
      implementation(compose.foundation)
      implementation(project(":lib"))
    }

    commonTest.dependencies {
      implementation(libs.kotlin.test)
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(compose.uiTest)
    }

    androidMain.dependencies {
      implementation(libs.androidx.activityCompose)
    }

    jvmMain.dependencies {
      implementation(compose.desktop.currentOs)
    }

  }
}

android {
  namespace = "sample.app"
  compileSdk = 35

  defaultConfig {
    minSdk = 21
    targetSdk = 35

    applicationId = "sample.app"
    versionCode = 1
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

  }
}

dependencies {
  androidTestImplementation("androidx.compose.ui:ui-test-junit4-android:1.9.0")
  debugImplementation("androidx.compose.ui:ui-test-manifest:1.9.0")
}

compose.desktop {
  application {
    mainClass = "MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "sample"
      packageVersion = "1.0.0"
    }
  }
}

tasks.withType<Test>().configureEach {
  // works for both testDebugUnitTest & testReleaseUnitTest
  if (name.endsWith("UnitTest")) {
    exclude("**/*UITest*")
  }
}
