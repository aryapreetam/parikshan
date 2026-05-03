plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
  id("com.vanniktech.maven.publish") version "0.36.0"
}

repositories {
  gradlePluginPortal()
  mavenCentral()
  google()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(17))
  }
}

group = findProperty("parikshanGroup") ?: "io.github.aryapreetam"
version = findProperty("parikshanVersion") ?: "0.0.1"

gradlePlugin {
  plugins {
    create("parikshanGradlePlugin") {
      id = "io.github.aryapreetam.parikshan"
      implementationClass = "io.github.aryapreetam.parikshan.gradle.ParikshanGradlePlugin"
      displayName = "Parikshan Gradle Plugin"
      description = "Runs visible Desktop E2E tests with Parikshan"
    }
  }
}

mavenPublishing {
  coordinates(group.toString(), project.name, version.toString())
}
