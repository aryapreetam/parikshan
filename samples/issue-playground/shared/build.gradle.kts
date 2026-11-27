import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("io.github.aryapreetam.parikshan") version "0.1.0"
}

val profile = providers.gradleProperty("cmpProfile").orNull ?: "1.11"

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).plus(
        if (profile == "1.10") listOf(iosX64()) else emptyList()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    android {
       namespace = "org.parikshan.issueplayground.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.filekit.core)
            implementation(libs.filekit.dialogs.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

abstract class CreateEmptyFixtureDirTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun execute() {
        outputDir.get().asFile.mkdirs()
    }
}

val createEmptyFixtureDirTask = tasks.register<CreateEmptyFixtureDirTask>("createEmptyFixtureDir") {
    outputDir.set(layout.projectDirectory.dir("src/commonTest/resources/fixtures/issue29_dir/empty_dir"))
}

abstract class PrepareIssue32FixturesTask : DefaultTask() {
    @get:OutputDirectory
    abstract val fixturesRootDir: DirectoryProperty

    @TaskAction
    fun execute() {
        val root = fixturesRootDir.get().asFile
        val sourceDir = File(root, "picker-write-source")
        val destDir = File(root, "picker-write-destination")

        if (destDir.exists()) {
            destDir.deleteRecursively()
        }
        destDir.mkdirs()

        sourceDir.mkdirs()
        File(sourceDir, "ready.txt").writeText("Android matrix readiness\n", Charsets.UTF_8)
    }
}

val prepareIssue32FixturesTask = tasks.register<PrepareIssue32FixturesTask>("prepareIssue32Fixtures") {
    fixturesRootDir.set(layout.buildDirectory.dir("parikshan-fixtures/issue32"))
    outputs.upToDateWhen { false }
}

tasks.matching { it.name.startsWith("e2e") || it.name.contains("Test") }.configureEach {
    dependsOn(createEmptyFixtureDirTask)
    dependsOn(prepareIssue32FixturesTask)
}
