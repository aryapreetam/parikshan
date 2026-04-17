package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Project

internal fun Project.configureAndroidInstrumentationDefaults() {
  val androidExtension = extensions.findByName("android") ?: return
  val defaultConfig =
    runCatching {
      androidExtension.javaClass.methods.firstOrNull { it.name == "getDefaultConfig" }?.invoke(androidExtension)
    }.getOrNull() ?: return

  val currentRunner =
    runCatching {
      defaultConfig.javaClass.methods.firstOrNull { it.name == "getTestInstrumentationRunner" }?.invoke(defaultConfig) as? String
    }.getOrNull()
  if (currentRunner.isNullOrBlank() || currentRunner == LEGACY_ANDROID_TEST_RUNNER) {
    runCatching {
      defaultConfig.javaClass.methods
        .firstOrNull { it.name == "setTestInstrumentationRunner" && it.parameterCount == 1 }
        ?.invoke(defaultConfig, MODERN_ANDROID_TEST_RUNNER)
    }
  }

  val testFilter = providers.gradleProperty("parikshan.testFilter").orNull ?: System.getProperty("parikshan.testFilter")
  if (testFilter.isNullOrBlank()) {
    return
  }

  @Suppress("UNCHECKED_CAST")
  val runnerArguments =
    runCatching {
      defaultConfig.javaClass.methods
        .firstOrNull { it.name == "getTestInstrumentationRunnerArguments" }
        ?.invoke(defaultConfig) as? MutableMap<String, Any>
    }.getOrNull()
      ?: return

  runnerArguments["class"] = testFilter
}

private const val LEGACY_ANDROID_TEST_RUNNER = "android.test.InstrumentationTestRunner"
private const val MODERN_ANDROID_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
