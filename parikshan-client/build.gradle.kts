plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.serialization)
}

kotlin {
  jvmToolchain(17)
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(project(":parikshan-core"))
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
    }
  }
}
