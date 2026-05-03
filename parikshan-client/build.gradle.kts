@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
@file:Suppress("DEPRECATION")

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.android.library.legacy)
  alias(libs.plugins.serialization)
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.maven.publish)
}

kotlin {
  jvmToolchain(17)
  androidTarget()
  jvm()
  wasmJs {
    browser()
  }
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    commonMain.dependencies {
      api(project(":parikshan-core"))
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.ui)
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.ktor.client.core)
      implementation(libs.ktor.client.websockets)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
    }

    jvmMain.dependencies {
      implementation(libs.ktor.client.cio)
      implementation(libs.playwright.java)
    }

    androidMain.dependencies {
      implementation("androidx.compose.ui:ui-test-junit4-android:1.9.0")
      implementation("androidx.test.espresso:espresso-core:3.6.1")
      implementation("androidx.test.uiautomator:uiautomator:2.3.0")
      implementation("androidx.test:runner:1.6.2")
    }
  }
}

android {
  namespace = "io.github.aryapreetam.parikshan.client"
  compileSdk = 35

  defaultConfig {
    minSdk = 24
  }
}

mavenPublishing {
  coordinates(project.group.toString(), project.name, project.version.toString())
}
