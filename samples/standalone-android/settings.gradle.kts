pluginManagement {
  includeBuild("../../gradle-plugins")
  repositories {
    mavenLocal()
    gradlePluginPortal()
    google {
      content {
        includeGroupByRegex("com\\.android.*")
        includeGroupByRegex("com\\.google.*")
        includeGroupByRegex("androidx.*")
      }
    }
    mavenCentral()
  }
}
dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal()
    google()
    mavenCentral()
  }
}

includeBuild("../../")

rootProject.name = "standalone-android"
include(":app")
