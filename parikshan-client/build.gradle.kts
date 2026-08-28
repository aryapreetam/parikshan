@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.serialization)
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
  id("parikshan.publishing")
  alias(libs.plugins.dokka)
  alias(libs.plugins.binary.compatibility.validator)
}

dokka {
  moduleName.set("parikshan-client")
  dokkaSourceSets.configureEach {
    if (name == "jsMain") {
      suppress.set(true)
    }
    includes.from("src/commonMain/kotlin/Module.md")
    includes.from("src/commonMain/kotlin/io/github/aryapreetam/parikshan/package.md")
    perPackageOption {
      matchingRegex.set("io\\.github\\.aryapreetam\\.parikshan\\.(client|server).*")
      suppress.set(true)
    }
  }
}

kotlin {
  sourceSets.all {
    languageSettings.optIn("io.github.aryapreetam.parikshan.InternalParikshanApi")
  }
  jvmToolchain(17)
  androidLibrary {
    namespace = "io.github.aryapreetam.parikshan.client"
    compileSdk = 35
    minSdk = 24
    withHostTest {}
  }
  jvm {
    testRuns.named("test") {
      executionTask.configure {
        useJUnitPlatform()
      }
    }
  }
  js {
    browser()
  }
  wasmJs {
    browser()
  }
  iosX64()
  iosArm64()
  iosSimulatorArm64()

  sourceSets {
    val jvmAndAndroidMain by creating {
      dependsOn(commonMain.get())
    }
    jvmMain.get().dependsOn(jvmAndAndroidMain)
    androidMain.get().dependsOn(jvmAndAndroidMain)

    val iosMain by creating {
      dependsOn(commonMain.get())
    }
    listOf(iosX64Main, iosArm64Main, iosSimulatorArm64Main).forEach {
      it.get().dependsOn(iosMain)
    }

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
      api("org.jetbrains.kotlin:kotlin-test-junit5")
      implementation(libs.ktor.client.cio)
      implementation(libs.playwright.java)
      implementation("org.junit.jupiter:junit-jupiter-api:5.10.2")
      implementation(libs.jcodec.javase)
      implementation("org.mp4parser:isoparser:1.9.56")
      implementation("org.mp4parser:muxer:1.9.56")
    }

    androidMain.dependencies {
      implementation(libs.androidx.compose.ui.test.junit4.android)
      implementation(libs.androidx.espresso.core)
      implementation(libs.androidx.uiautomator)
      implementation(libs.androidx.test.runner)
    }

    iosMain.dependencies {
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(compose.uiTest)
    }

    wasmJsMain.dependencies {
      @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
      implementation(compose.uiTest)
    }
  }
}

mavenPublishing {
  pom {
    name.set("Parikshan Client")
    description.set("Client library for Parikshan Compose Multiplatform E2E")
  }
}
