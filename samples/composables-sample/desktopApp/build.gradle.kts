import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
  alias(libs.plugins.jetbrains.kotlin.multiplatform)
  alias(libs.plugins.jetbrains.compose)
  alias(libs.plugins.jetbrains.compose.compiler)
  id("io.github.aryapreetam.parikshan") version "0.0.5"
}

kotlin {
  jvm()

  sourceSets {
    jvmMain.dependencies {
      implementation(projects.shared)
      implementation(compose.desktop.currentOs)
    }
  }
}

compose.desktop {
  application {
    mainClass = "com.example.app.MainKt"

    nativeDistributions {
      targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
      packageName = "com.example.app"
      packageVersion = "1.0.0"
    }
  }
}
