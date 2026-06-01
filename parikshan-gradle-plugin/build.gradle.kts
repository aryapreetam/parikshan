import java.util.Properties

plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
  id("com.gradle.plugin-publish") version "2.1.1"
  id("com.vanniktech.maven.publish") version "0.36.0"
  id("org.jetbrains.dokka") version "2.2.0"
}

dokka {
  moduleName.set("parikshan-gradle-plugin")
  dokkaSourceSets.configureEach {
    includes.from("src/main/kotlin/Module.md")
    includes.from("src/main/kotlin/io/github/aryapreetam/parikshan/gradle/package.md")
  }
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
  website.set("https://github.com/aryapreetam/parikshan")
  vcsUrl.set("https://github.com/aryapreetam/parikshan")
  plugins {
    create("parikshan") {
      id = "io.github.aryapreetam.parikshan"
      implementationClass = "io.github.aryapreetam.parikshan.gradle.ParikshanGradlePlugin"
      displayName = "Parikshan Gradle Plugin"
      description = "Visible end-to-end UI automation engine for Compose Multiplatform"
      tags.set(listOf("compose", "multiplatform", "testing", "e2e"))
    }
  }
}

mavenPublishing {
  coordinates(group.toString(), project.name, version.toString())
}
