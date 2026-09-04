rootProject.name = "parikshan-root"

pluginManagement {
  includeBuild("build-logic")
  includeBuild("gradle-plugins")

  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("android.*")
      }
    }
    gradlePluginPortal()
    mavenCentral()
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
        includeGroupByRegex("android.*")
      }
    }
    mavenCentral()
  }
  val cmpProfile = providers.gradleProperty("cmpProfile").orNull ?: "1.10"
  versionCatalogs {
    create("libs") {
      when (cmpProfile) {
        "1.10" -> {
          version("kotlin", "2.3.21")
          version("compose", "1.10.1")
          version("androidx-activityCompose", "1.10.1")
        }
        "1.11" -> {
          version("kotlin", "2.4.0")
          version("compose", "1.11.1")
          version("androidx-activityCompose", "1.13.0")
        }
        "1.12" -> {
          version("kotlin", "2.4.0")
          version("compose", "1.12.0")
          version("androidx-activityCompose", "1.13.0")
        }
        else -> error("Invalid cmpProfile '$cmpProfile'. Supported values are: '1.10', '1.11', '1.12'.")
      }

      val kotlinOverride = providers.gradleProperty("kotlinVersion").orNull
      if (!kotlinOverride.isNullOrBlank()) {
        version("kotlin", kotlinOverride)
      }
      val composeOverride = providers.gradleProperty("composeVersion").orNull
      if (!composeOverride.isNullOrBlank()) {
        version("compose", composeOverride)
      }
    }
  }
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":parikshan")
include(":parikshan-core")
include(":parikshan-server")
include(":parikshan-client")
include(":samples:multiplatform-showcase:composeApp")
include(":samples:multiplatform-showcase:androidApp")
project(":samples:multiplatform-showcase:composeApp").projectDir = file("samples/multiplatform-showcase/composeApp")
project(":samples:multiplatform-showcase:androidApp").projectDir = file("samples/multiplatform-showcase/androidApp")
