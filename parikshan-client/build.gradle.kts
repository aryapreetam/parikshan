@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
@file:Suppress("DEPRECATION")

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.serialization)
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
}

kotlin {
  jvmToolchain(17)
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
  }
}
