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
  id("io.github.aryapreetam.parikshan.gradle-plugin")
}

val parikshanTestFilter = providers.gradleProperty("parikshan.testFilter").orNull

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
    minSdk = 26
    targetSdk = 35

    applicationId = "sample.app"
    versionCode = 1
    versionName = "1.0.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    if (!parikshanTestFilter.isNullOrBlank()) {
      testInstrumentationRunnerArguments["class"] = parikshanTestFilter
    }

  }

  packaging {
    resources {
      excludes += "META-INF/INDEX.LIST"
      pickFirsts += "META-INF/io.netty.versions.properties"
    }
  }
}

dependencies {
  androidTestImplementation(project(":parikshan-client"))
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

// Parikshan Configuration
configure<io.github.aryapreetam.parikshan.gradle.ParikshanExtension> {
    desktopTestTaskName.set("jvmTest")
    // Use development webpack for faster test feedback loop
    wasmDistributionTaskName.set("wasmJsBrowserDevelopmentWebpack") 
}

tasks.withType<Test>().configureEach {
  if (name.endsWith("UnitTest")) {
    exclude("**/*UITest*")
  }
}
