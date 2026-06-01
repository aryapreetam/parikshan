@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.maven.publish)
  alias(libs.plugins.dokka)
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
      api(project(":parikshan-core"))
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
      implementation(project(":parikshan-client"))
    }

    jvmMain.dependencies {
      api(project(":parikshan-server"))
    }
  }
}

dependencies {
  dokkaPlugin(libs.android.documentation.plugin)
  dokka(project(":parikshan-core"))
  dokka(project(":parikshan-client"))
  dokka(project(":parikshan-server"))
}

dokka {
  moduleName.set("parikshan")
  dokkaPublications.html {
    includes.from("src/commonMain/kotlin/Module.md")
  }
}

mavenPublishing {
  publishToMavenCentral()
  coordinates(
    findProperty("libGroup")?.toString() ?: project.group.toString(),
    findProperty("libArtifactId")?.toString() ?: "parikshan",
    findProperty("libVersion")?.toString() ?: project.version.toString()
  )

  pom {
    name = "Parikshan - Compose Multiplatform E2E Testing"
    description = "Visible end-to-end UI automation engine for Compose Multiplatform"
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
