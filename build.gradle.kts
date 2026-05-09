plugins {
  alias(libs.plugins.multiplatform).apply(false)
  alias(libs.plugins.jvm).apply(false)
  alias(libs.plugins.android.library).apply(false)
  alias(libs.plugins.android.library.legacy).apply(false)
  alias(libs.plugins.maven.publish).apply(false)
  alias(libs.plugins.compose).apply(false)
  alias(libs.plugins.compose.compiler).apply(false)
  alias(libs.plugins.serialization).apply(false)
  alias(libs.plugins.android.application).apply(false)
}

// Apply template setup check
apply(from = "gradle/check-template-setup.gradle.kts")

allprojects {
  group = findProperty("parikshanGroup") ?: "io.github.aryapreetam"
  version = findProperty("parikshanVersion") ?: "0.0.1"

  plugins.withId("maven-publish") {
    tasks.named("publishToMavenLocal") {
      dependsOn(gradle.includedBuild("parikshan-gradle-plugin").task(":publishToMavenLocal"))
    }
  }
}
