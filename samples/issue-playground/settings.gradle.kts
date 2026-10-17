rootProject.name = "issue-playground"

pluginManagement {
    includeBuild("../../gradle-plugins")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        mavenLocal()
    }
}

val profile = providers.gradleProperty("cmpProfile").orNull ?: "1.11"

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        mavenLocal()
    }
    versionCatalogs {
        create("libs") {
            when (profile) {
                "1.10" -> {
                    version("kotlin", "2.3.21")
                    version("composeMultiplatform", "1.10.1")
                    version("androidx-lifecycle", "2.9.6")
                    version("androidx-activity", "1.10.1")
                }
                "1.11" -> {
                    version("kotlin", "2.4.10")
                    version("composeMultiplatform", "1.11.1")
                    version("androidx-lifecycle", "2.11.0-beta01")
                    version("androidx-activity", "1.13.0")
                }
                "1.12" -> {
                    version("kotlin", "2.4.20")
                    version("composeMultiplatform", "1.12.1")
                    version("androidx-lifecycle", "2.11.0")
                    version("androidx-activity", "1.13.0")
                }
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

includeBuild("../../")

include(":androidApp")
include(":desktopApp")
include(":shared")
include(":webApp")