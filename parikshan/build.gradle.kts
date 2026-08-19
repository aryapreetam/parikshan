@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
  alias(libs.plugins.multiplatform)
  id("parikshan.publishing")
  alias(libs.plugins.dokka)
}

kotlin {
  jvmToolchain(17)
  jvm()
  js {
    browser()
  }
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
}

dokka {
  moduleName.set("parikshan")
  dokkaSourceSets.configureEach {
    if (name == "jsMain") {
      suppress.set(true)
    }
  }
  dokkaPublications.html {
    includes.from("src/commonMain/kotlin/Module.md")
  }
  pluginsConfiguration.html {
    customAssets.from(
      rootProject.file("docs/assets/logo.png"),
      rootProject.file("docs/assets/logo-icon.svg")
    )
    customStyleSheets.from(rootProject.file("docs/styles/logo-styles.css"))
  }
}

mavenPublishing {
  pom {
    name.set("Parikshan - Compose Multiplatform E2E Testing")
    description.set("Visible end-to-end UI automation engine for Compose Multiplatform")
  }
}