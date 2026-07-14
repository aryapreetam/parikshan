plugins {
  alias(libs.plugins.jvm)
  alias(libs.plugins.serialization)
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
  id("parikshan.publishing")
  alias(libs.plugins.binary.compatibility.validator)
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  api(project(":parikshan-core"))
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.swing)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.websockets)
  implementation(libs.jcodec.javase)
  compileOnly(compose.desktop.currentOs)
}

mavenPublishing {
  pom {
    name.set("Parikshan Server")
    description.set("Server library for Parikshan Compose Multiplatform E2E")
  }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
  compilerOptions {
    freeCompilerArgs.add("-opt-in=io.github.aryapreetam.parikshan.InternalParikshanApi")
  }
}
