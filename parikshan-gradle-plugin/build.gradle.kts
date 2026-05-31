import java.util.Properties

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

val rootProps = Properties().apply {
    file("../gradle.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

group = rootProps.getProperty("libGroup")?.let { "$it.parikshan" } ?: "io.github.aryapreetam.parikshan"
version = rootProps.getProperty("libVersion") ?: "0.0.1"

tasks.withType<Jar> {
  manifest {
    attributes["Implementation-Version"] = project.version
  }
}

gradlePlugin {
  plugins {
    create("parikshan") {
      id = "io.github.aryapreetam.parikshan"
      implementationClass = "io.github.aryapreetam.parikshan.gradle.ParikshanGradlePlugin"
      displayName = "Parikshan Gradle Plugin"
      description = "Visible end-to-end UI automation engine for Compose Multiplatform"
    }
  }
}

mavenPublishing {
  coordinates(group.toString(), project.name, version.toString())
}
