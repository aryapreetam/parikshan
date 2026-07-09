@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.android.library)
  alias(libs.plugins.serialization)
  id("parikshan.publishing")
  alias(libs.plugins.dokka)
}

dokka {
  moduleName.set("parikshan-core")
  dokkaSourceSets.configureEach {
    includes.from("src/commonMain/kotlin/Module.md")
    includes.from("src/commonMain/kotlin/io/github/aryapreetam/parikshan/package.md")
  }
}

kotlin {
  jvmToolchain(17)
  androidLibrary {
    namespace = "io.github.aryapreetam.parikshan.core"
    compileSdk = 35
    minSdk = 24
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
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.kotlinx.serialization.json)
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
    }
  }
}

mavenPublishing {
  pom {
    name.set("Parikshan Core")
    description.set("Core protocol and engine for Parikshan Compose Multiplatform E2E")
  }
}
