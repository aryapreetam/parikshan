@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.serialization)
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.maven.publish)
}

kotlin {
  jvmToolchain(17)
  androidLibrary {
    namespace = "io.github.aryapreetam.parikshan.client"
    compileSdk = 35
    minSdk = 24
    withHostTest {}
  }
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
      implementation(libs.androidx.compose.ui.test.junit4.android)
      implementation(libs.androidx.espresso.core)
      implementation(libs.androidx.uiautomator)
      implementation(libs.androidx.test.runner)
    }
  }
}

mavenPublishing {
  coordinates(project.group.toString(), project.name, project.version.toString())
}
