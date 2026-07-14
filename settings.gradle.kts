rootProject.name = "parikshan-root"

pluginManagement {
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
  versionCatalogs {
    create("libs") {
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
