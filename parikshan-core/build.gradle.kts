plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.serialization)
}

kotlin {
  jvmToolchain(17)
  jvm()

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
