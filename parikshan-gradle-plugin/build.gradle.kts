plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

gradlePlugin {
  plugins {
    create("parikshanGradlePlugin") {
      id = "io.github.aryapreetam.parikshan.gradle-plugin"
      implementationClass = "io.github.aryapreetam.parikshan.gradle.ParikshanGradlePlugin"
      displayName = "Parikshan Gradle Plugin"
      description = "Runs visible Desktop E2E tests with Parikshan"
    }
  }
}
