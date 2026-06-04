@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.serialization)
  alias(libs.plugins.maven.publish)
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
  publishToMavenCentral()
  coordinates(project.group.toString(), project.name, project.version.toString())

  pom {
    name = "Parikshan Core"
    description = "Core protocol and engine for Parikshan Compose Multiplatform E2E"
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
