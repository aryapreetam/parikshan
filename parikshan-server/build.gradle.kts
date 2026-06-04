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
  compileOnly(compose.desktop.currentOs)
}

mavenPublishing {
  publishToMavenCentral()
  coordinates(project.group.toString(), project.name, project.version.toString())

  pom {
    name = "Parikshan Server"
    description = "Server library for Parikshan Compose Multiplatform E2E"
    url = "https://github.com/aryapreetam/parikshan"

    licenses {
      license {
        name = "MIT"
        url = "https://opensource.org/licenses/MIT"
      }
    }

    developers {
      developer {
        id = "aryapreetam"
        name = "Preetam Bhosle"
      }
    }

    scm {
      url = "https://github.com/aryapreetam/parikshan"
    }
  }

  if (project.hasProperty("signing.keyId") || project.hasProperty("signingInMemoryKey")) {
    signAllPublications()
  }
}
