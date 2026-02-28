plugins {
  alias(libs.plugins.multiplatform)
  alias(libs.plugins.maven.publish)
  alias(libs.plugins.dokka)
}

kotlin {
  jvmToolchain(17)
  jvm()

  sourceSets {
    commonMain.dependencies {
      api(project(":parikshan-core"))
    }

    commonTest.dependencies {
      implementation(kotlin("test"))
    }

    jvmMain.dependencies {
      api(project(":parikshan-client"))
      api(project(":parikshan-server"))
    }
  }
}

dependencies {
  dokkaPlugin(libs.android.documentation.plugin)
}

mavenPublishing {
  publishToMavenCentral()
  coordinates("io.github.aryapreetam", "parikshan", "0.0.1")

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
