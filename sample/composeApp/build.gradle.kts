@file:OptIn(ExperimentalWasmDsl::class)
@file:Suppress("DEPRECATION")

import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSetTree

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.compose)
  alias(libs.plugins.android.application)
  id("io.github.aryapreetam.parikshan")
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
      implementation(compose.material3)
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
    minSdk = 26
    targetSdk = 35

    applicationId = "sample.app"
    versionCode = 1
    versionName = "1.0.0"
  }

  packaging {
    resources {
      excludes += "META-INF/INDEX.LIST"
      pickFirsts += "META-INF/io.netty.versions.properties"
    }
  }
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
  if (name.endsWith("UnitTest")) {
    exclude("**/*UITest*")
  }
}

// iOS E2E orchestration is handled by the Parikshan Gradle plugin (e2eIosTest task).
// The plugin builds via xcodebuild, installs on the simulator, launches the app,
// waits for the in-app HTTP server, then runs JVM tests with parikshan.target=ios.
