plugins {
  alias(libs.plugins.jvm)
  alias(libs.plugins.serialization)
  alias(libs.plugins.compose)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.maven.publish)
  alias(libs.plugins.dokka)
}

dokka {
  moduleName.set("parikshan-server")
  dokkaSourceSets.configureEach {
    includes.from("src/main/kotlin/Module.md")
    includes.from("src/main/kotlin/io/github/aryapreetam/parikshan/server/package.md")
  }
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
  implementation(compose.desktop.currentOs)
}

mavenPublishing {
  coordinates(project.group.toString(), project.name, project.version.toString())
}
