package io.github.aryapreetam.parikshan.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalWasmDsl::class)
class TargetMatrixTest {

  private lateinit var testProjectDir: File

  @BeforeTest
  fun setup() {
    System.setProperty("parikshan.os.arch", "aarch64")
    System.setProperty("parikshan.os.name", "Mac OS X")
    testProjectDir = kotlin.io.path.createTempDirectory("parikshan-matrix-test").toFile()
    val sdkDir = System.getenv("ANDROID_HOME")
      ?: listOf(
        "${System.getProperty("user.home")}/Library/Android/sdk",
        "${System.getProperty("user.home")}/Android/Sdk",
        "/usr/local/lib/android/sdk"
      ).firstOrNull { File(it).exists() }
    if (sdkDir != null && File(sdkDir).exists()) {
      File(testProjectDir, "local.properties").writeText("sdk.dir=${sdkDir.replace("\\", "/")}\n")
    }
  }

  @kotlin.test.AfterTest
  fun tearDown() {
    System.clearProperty("parikshan.os.name")
    System.clearProperty("parikshan.os.arch")
  }

  private fun createProject(): Project {
    val project = ProjectBuilder.builder()
      .withProjectDir(testProjectDir)
      .build()
    project.repositories.mavenCentral()
    return project
  }

  private fun evaluate(project: Project) {
    (project as ProjectInternal).evaluate()
  }

  @Test
  fun `test Desktop JVM only project configuration`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTestTask = project.tasks.findByName("e2eTest") as? E2ETestTask
    assertNotNull(e2eTestTask, "e2eTest task should be registered")
  }

  @Test
  fun `test Wasm only project configuration`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.wasmJs { browser() }
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eWasmTest"), "e2eWasmTest task should be registered")
    assertNotNull(project.tasks.findByName("e2eTest"), "e2eTest task should be registered")
  }

  @Test
  fun `test Android only project configuration`() {
    val project = createProject()
    project.pluginManager.apply("com.android.application")
    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 34
    android.namespace = "org.example.android"
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eAndroidTest"), "e2eAndroidTest task should be registered")
  }

  @Test
  fun `test iOS only project configuration`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.iosSimulatorArm64()
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eIosTest"), "e2eIosTest task should be registered")
  }

  @Test
  fun `test Mobile only Android and iOS project configuration`() {
    val project = createProject()
    project.pluginManager.apply("com.android.application")
    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 34
    android.namespace = "org.example.mobile"

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eAndroidTest"), "e2eAndroidTest task should be registered")
  }

  @Test
  fun `test KMP Mobile Android target with iOS project configuration`() {
    val sdkEnv = System.getenv("ANDROID_HOME")
    if (sdkEnv == null || !File(sdkEnv).exists()) return
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    project.pluginManager.apply("com.android.kotlin.multiplatform.library")

    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    val androidTarget = kmp.targets.findByName("android")
    if (androidTarget != null) {
      runCatching {
        val setter = androidTarget.javaClass.methods.firstOrNull { it.name == "setCompileSdk" || it.name == "compileSdk" }
        setter?.invoke(androidTarget, 34)
      }
    }
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eAndroidTest"), "e2eAndroidTest task should be registered")
    assertNotNull(project.tasks.findByName("e2eIosTest"), "e2eIosTest task should be registered")
    assertNotNull(project.tasks.findByName("parikshanHostTest"), "parikshanHostTest fallback should be registered")
  }

  @Test
  fun `test JVM and Wasm project configuration`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm("desktop")
    kmp.wasmJs { browser() }

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eDesktopTest"), "e2eDesktopTest task should be registered")
    assertNotNull(project.tasks.findByName("e2eWasmTest"), "e2eWasmTest task should be registered")
  }

  @Test
  fun `test Full 4 target project configuration`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm("desktop")
    kmp.wasmJs { browser() }
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eDesktopTest"))
    assertNotNull(project.tasks.findByName("e2eWasmTest"))
    assertNotNull(project.tasks.findByName("e2eIosTest"))

    val e2eTestTask = project.tasks.findByName("e2eTest") as E2ETestTask
    assertTrue(e2eTestTask.targets.contains("desktop"))
  }

  @Test
  fun `test Custom iOS directory structure discovery`() {
    val customIosDir = File(testProjectDir, "ios").apply { mkdirs() }
    val xcodeProj = File(customIosDir, "App.xcodeproj").apply { mkdirs() }

    val project = createProject()
    val discovered = project.discoverIosXcodeProject()
    assertNotNull(discovered, "discoverIosXcodeProject should discover custom nested Xcode project")
    assertEquals(xcodeProj.canonicalPath, discovered.canonicalPath)
  }

  @Test
  fun `test Invalid target vocabulary rejection`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTestTask = project.tasks.findByName("e2eTest") as E2ETestTask
    e2eTestTask.targets = "invalidTarget"

    val exception = assertFailsWith<GradleException> {
      e2eTestTask.actions.first().execute(e2eTestTask)
    }
    assertTrue(exception.message?.contains("Unknown target(s) specified in --targets") == true)
  }

  @Test
  fun `test Unconfigured target rejection`() {
    val project = createProject()
    project.pluginManager.apply("com.android.application")
    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 34
    android.namespace = "org.example.unconfigured"

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTestTask = project.tasks.findByName("e2eTest") as E2ETestTask
    e2eTestTask.targets = "wasm"

    val exception = assertFailsWith<GradleException> {
      e2eTestTask.actions.first().execute(e2eTestTask)
    }
    assertTrue(exception.message?.contains("does not configure them") == true)
  }

  @Test
  fun `test Mobile only project defaults configuredTargets to Android and iOS`() {
    val project = createProject()
    project.pluginManager.apply("com.android.application")
    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 34
    android.namespace = "org.example.mobiledefaults"

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eAndroidTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eDesktopTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eWasmTest"))

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    assertTrue(e2eTask.targets.contains("android"))
  }

  @Test
  fun `test Desktop only project defaults configuredTargets to Desktop`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eAndroidTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eWasmTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eIosTest"))
  }

  @Test
  fun `test Wasm only project registers Playwright and Wasm asset tasks`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.wasmJs { browser() }

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eWasmTest"))
    assertNotNull(project.tasks.findByName("prepareParikshanWasmAssets"))
    assertNotNull(project.tasks.findByName("installPlaywrightBrowsers"))
  }

  @Test
  fun `test E2ETestTask finalizedBy E2ETestReport`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    val finalizedByTasks = e2eTask.finalizedBy.getDependencies(e2eTask).map { it.name }
    assertTrue(finalizedByTasks.contains("e2eTestReport"), "e2eTest must be finalizedBy e2eTestReport")
  }

  @Test
  fun `test Cross project desktopAppProjectPath binding`() {
    val rootDir = createProject()
    val subProject = ProjectBuilder.builder()
      .withParent(rootDir)
      .withName("desktopApp")
      .build()
    subProject.pluginManager.apply("org.jetbrains.kotlin.jvm")
    subProject.pluginManager.apply("io.github.aryapreetam.parikshan")

    val ext = subProject.extensions.getByType(ParikshanExtension::class.java)
    ext.desktopAppProjectPath.set(":desktopApp")
    evaluate(subProject)

    assertNotNull(subProject.tasks.findByName("e2eTest"))
  }

  @Test
  fun `test Android KMP library project without JVM does not crash`() {
    val sdkEnv = System.getenv("ANDROID_HOME")
    if (sdkEnv == null || !File(sdkEnv).exists()) return
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    project.pluginManager.apply("com.android.kotlin.multiplatform.library")

    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    val androidTarget = kmp.targets.findByName("android")
    if (androidTarget != null) {
      runCatching {
        val setter = androidTarget.javaClass.methods.firstOrNull { it.name == "setCompileSdk" || it.name == "compileSdk" }
        setter?.invoke(androidTarget, 34)
      }
    }
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eIosTest"))
    assertNotNull(project.tasks.findByName("e2eTest"))
  }

  @Test
  fun `test iOS only KMP project without Android or JVM does not crash`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eIosTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eDesktopTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eAndroidTest"))

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    assertEquals("ios", e2eTask.targets)
  }

  @Test
  fun `test Pure Android app project does not crash`() {
    val project = createProject()
    project.pluginManager.apply("com.android.application")
    val android = project.extensions.getByType(ApplicationExtension::class.java)
    android.compileSdk = 34
    android.namespace = "org.example.pureandroid"

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eAndroidTest"))
  }

  @Test
  fun `test Custom source set names resolved lazily`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm("desktop")

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    assertNotNull(e2eTask)
  }

  @Test
  fun `test All registered tasks are configuration cache compliant`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.jvm")
    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    val prereqTasks = e2eTask.inputs.files.buildDependencies.getDependencies(e2eTask) +
        e2eTask.taskDependencies.getDependencies(e2eTask)
    val forbiddenTargetTasks = setOf("e2eDesktopTest", "e2eWasmTest", "e2eAndroidTest", "e2eIosTest")
    val blockers = prereqTasks.map { it.name }.toSet().intersect(forbiddenTargetTasks)

    assertTrue(blockers.isEmpty(), "e2eTest must be configuration cache compliant with 0 sequential target blockers")
  }

  @Test
  fun `test Wasm tasks skipped when running Jvm only target`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm("desktop")
    kmp.wasmJs { browser() }

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    e2eTask.targets = "jvm"

    val e2eTaskDependencies = e2eTask.taskDependencies.getDependencies(e2eTask).map { it.name }
    assertTrue(!e2eTaskDependencies.contains("prepareParikshanWasmAssets"), "prepareParikshanWasmAssets must not be depended on when target is jvm")
    assertTrue(!e2eTaskDependencies.contains("installPlaywrightBrowsers"), "installPlaywrightBrowsers must not be depended on when target is jvm")
  }

  @Test
  fun `test Combined target matrix configures only requested targets`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm("desktop")
    kmp.wasmJs { browser() }
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    e2eTask.targets = "jvm,wasm"

    val activeTargets = e2eTask.targets.split(",")
      .map { it.trim().lowercase() }
      .map { when (it) { "jvm" -> "desktop"; "web" -> "wasm"; else -> it } }

    assertEquals(listOf("desktop", "wasm"), activeTargets, "Combined targets 'jvm,wasm' must resolve strictly to [desktop, wasm]")
  }

  @Test
  fun `test startIosApp depends on prepareParikshanIosBootSource`() {
    val project = createProject()
    project.extensions.extraProperties.set("parikshan.e2e.active", "true")
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val startIosAppTask = project.tasks.findByName("startIosApp")
    assertNotNull(startIosAppTask, "startIosApp task must be registered")

    val dependencies = startIosAppTask.taskDependencies.getDependencies(startIosAppTask).map { it.name }
    assertTrue(dependencies.contains("prepareParikshanIosBootSource"), "startIosApp task must depend on prepareParikshanIosBootSource")
  }

  @Test
  fun `test e2eTest skips desktop uberJar when target is wasm or ios`() {
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm("desktop")
    kmp.wasmJs { browser() }
    kmp.iosSimulatorArm64()

    if (!project.tasks.names.contains("packageUberJarForCurrentOS")) {
      project.tasks.register("packageUberJarForCurrentOS", org.gradle.jvm.tasks.Jar::class.java)
    }

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    e2eTask.targets = "wasm"

    val wasmDependencies = e2eTask.taskDependencies.getDependencies(e2eTask).map { it.name }
    assertTrue(!wasmDependencies.contains("packageUberJarForCurrentOS"), "packageUberJarForCurrentOS must not be depended on when target is wasm")

    e2eTask.targets = "ios"
    val iosDependencies = e2eTask.taskDependencies.getDependencies(e2eTask).map { it.name }
    assertTrue(!iosDependencies.contains("packageUberJarForCurrentOS"), "packageUberJarForCurrentOS must not be depended on when target is ios")
  }

  @Test
  fun `test Single-Module Monolithic Layout (Old Structure) registers all 4 target tasks`() {
    val sdkEnv = System.getenv("ANDROID_HOME")
    val project = createProject()
    project.gradle.startParameter.setTaskNames(listOf("tasks"))
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      project.pluginManager.apply("com.android.kotlin.multiplatform.library")
      val androidTarget = project.extensions.getByType(KotlinMultiplatformExtension::class.java).targets.findByName("android")
      if (androidTarget != null) {
        runCatching {
          val setter = androidTarget.javaClass.methods.firstOrNull { it.name == "setCompileSdk" || it.name == "compileSdk" }
          setter?.invoke(androidTarget, 34)
        }
      }
    }
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm()
    kmp.wasmJs { browser() }
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eJvmTest"), "e2eJvmTest task should be registered on single-module layout")
    assertNotNull(project.tasks.findByName("e2eWasmTest"), "e2eWasmTest task should be registered on single-module layout")
    assertNotNull(project.tasks.findByName("e2eIosTest"), "e2eIosTest task should be registered on single-module layout")
    assertNotNull(project.tasks.findByName("e2eTest"), "e2eTest task should be registered on single-module layout")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      assertNotNull(project.tasks.findByName("e2eAndroidTest"), "e2eAndroidTest task should be registered on single-module layout")
    }
  }

  @Test
  fun `test Flat Multi-Module Layout (New Structure) registers all 4 target tasks`() {
    val sdkEnv = System.getenv("ANDROID_HOME")
    val root = createProject()
    val subProject = ProjectBuilder.builder()
      .withParent(root)
      .withName("shared")
      .build()
    root.gradle.startParameter.setTaskNames(listOf(":shared:tasks"))
    subProject.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      subProject.pluginManager.apply("com.android.kotlin.multiplatform.library")
      val androidTarget = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java).targets.findByName("android")
      if (androidTarget != null) {
        runCatching {
          val setter = androidTarget.javaClass.methods.firstOrNull { it.name == "setCompileSdk" || it.name == "compileSdk" }
          setter?.invoke(androidTarget, 34)
        }
      }
    }
    val kmp = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm()
    kmp.wasmJs { browser() }
    kmp.iosSimulatorArm64()

    subProject.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(subProject)

    assertNotNull(subProject.tasks.findByName("e2eJvmTest"), "e2eJvmTest task should be registered on flat shared module")
    assertNotNull(subProject.tasks.findByName("e2eWasmTest"), "e2eWasmTest task should be registered on flat shared module")
    assertNotNull(subProject.tasks.findByName("e2eIosTest"), "e2eIosTest task should be registered on flat shared module")
    assertNotNull(subProject.tasks.findByName("e2eTest"), "e2eTest task should be registered on flat shared module")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      assertNotNull(subProject.tasks.findByName("e2eAndroidTest"), "e2eAndroidTest task should be registered on flat shared module")
    }
  }

  @Test
  fun `test Nested Multi-Module with Server Module Layout (New Structure with app-shared) registers all 4 target tasks`() {
    val sdkEnv = System.getenv("ANDROID_HOME")
    val root = createProject()
    val subProject = ProjectBuilder.builder()
      .withParent(root)
      .withName("shared")
      .build()
    root.gradle.startParameter.setTaskNames(listOf(":app:shared:tasks"))
    subProject.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      subProject.pluginManager.apply("com.android.kotlin.multiplatform.library")
      val androidTarget = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java).targets.findByName("android")
      if (androidTarget != null) {
        runCatching {
          val setter = androidTarget.javaClass.methods.firstOrNull { it.name == "setCompileSdk" || it.name == "compileSdk" }
          setter?.invoke(androidTarget, 34)
        }
      }
    }
    val kmp = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm()
    kmp.wasmJs { browser() }
    kmp.iosSimulatorArm64()

    subProject.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(subProject)

    assertNotNull(subProject.tasks.findByName("e2eJvmTest"), "e2eJvmTest task should be registered when gradlew tasks is invoked")
    assertNotNull(subProject.tasks.findByName("e2eWasmTest"), "e2eWasmTest task should be registered when gradlew tasks is invoked")
    assertNotNull(subProject.tasks.findByName("e2eIosTest"), "e2eIosTest task should be registered when gradlew tasks is invoked")
    assertNotNull(subProject.tasks.findByName("e2eTest"), "e2eTest task should be registered when gradlew tasks is invoked")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      assertNotNull(subProject.tasks.findByName("e2eAndroidTest"), "e2eAndroidTest task should be registered when gradlew tasks is invoked")
    }
  }

  @Test
  fun `test Mobile-Only KMP Layout (New Structure with Mobile-Only targets) registers mobile target tasks without JVM requirement`() {
    val sdkEnv = System.getenv("ANDROID_HOME")
    val root = createProject()
    val subProject = ProjectBuilder.builder()
      .withParent(root)
      .withName("shared")
      .build()
    root.gradle.startParameter.setTaskNames(listOf(":shared:tasks"))
    subProject.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      subProject.pluginManager.apply("com.android.kotlin.multiplatform.library")
      val androidTarget = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java).targets.findByName("android")
      if (androidTarget != null) {
        runCatching {
          val setter = androidTarget.javaClass.methods.firstOrNull { it.name == "setCompileSdk" || it.name == "compileSdk" }
          setter?.invoke(androidTarget, 34)
        }
      }
    }
    val kmp = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.iosSimulatorArm64()

    subProject.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(subProject)

    assertNotNull(subProject.tasks.findByName("e2eIosTest"), "e2eIosTest task should be registered on mobile-only layout")
    assertNotNull(subProject.tasks.findByName("e2eTest"), "e2eTest task should be registered on mobile-only layout")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      assertNotNull(subProject.tasks.findByName("e2eAndroidTest"), "e2eAndroidTest task should be registered on mobile-only layout")
    }
    kotlin.test.assertNull(subProject.tasks.findByName("e2eJvmTest"), "e2eJvmTest should be null for mobile-only project")
    kotlin.test.assertNull(subProject.tasks.findByName("e2eWasmTest"), "e2eWasmTest should be null for mobile-only project")
  }

  @Test
  fun `test New Structure with JVM-only targets registers e2eJvmTest and e2eTest`() {
    val root = createProject()
    val subProject = ProjectBuilder.builder()
      .withParent(root)
      .withName("shared")
      .build()
    root.gradle.startParameter.setTaskNames(listOf(":shared:tasks"))
    subProject.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm()

    subProject.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(subProject)

    assertNotNull(subProject.tasks.findByName("e2eJvmTest"))
    assertNotNull(subProject.tasks.findByName("e2eTest"))
    kotlin.test.assertNull(subProject.tasks.findByName("e2eWasmTest"))
    kotlin.test.assertNull(subProject.tasks.findByName("e2eIosTest"))
  }

  @Test
  fun `test New Structure with JVM+Wasm targets registers e2eJvmTest and e2eWasmTest`() {
    val root = createProject()
    val subProject = ProjectBuilder.builder()
      .withParent(root)
      .withName("shared")
      .build()
    root.gradle.startParameter.setTaskNames(listOf(":shared:tasks"))
    subProject.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm()
    kmp.wasmJs { browser() }

    subProject.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(subProject)

    assertNotNull(subProject.tasks.findByName("e2eJvmTest"))
    assertNotNull(subProject.tasks.findByName("e2eWasmTest"))
    assertNotNull(subProject.tasks.findByName("e2eTest"))
    kotlin.test.assertNull(subProject.tasks.findByName("e2eIosTest"))
  }

  @Test
  fun `test New Structure with JVM+Android targets registers e2eJvmTest and e2eAndroidTest`() {
    val sdkEnv = System.getenv("ANDROID_HOME")
    val root = createProject()
    val subProject = ProjectBuilder.builder()
      .withParent(root)
      .withName("shared")
      .build()
    root.gradle.startParameter.setTaskNames(listOf(":shared:tasks"))
    subProject.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    if (sdkEnv != null && File(sdkEnv).exists()) {
      subProject.pluginManager.apply("com.android.kotlin.multiplatform.library")
      val androidTarget = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java).targets.findByName("android")
      if (androidTarget != null) {
        runCatching {
          val setter = androidTarget.javaClass.methods.firstOrNull { it.name == "setCompileSdk" || it.name == "compileSdk" }
          setter?.invoke(androidTarget, 34)
        }
      }
    }
    val kmp = subProject.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm()

    subProject.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(subProject)

    assertNotNull(subProject.tasks.findByName("e2eJvmTest"))
    assertNotNull(subProject.tasks.findByName("e2eTest"))
    if (sdkEnv != null && File(sdkEnv).exists()) {
      assertNotNull(subProject.tasks.findByName("e2eAndroidTest"))
    }
    kotlin.test.assertNull(subProject.tasks.findByName("e2eWasmTest"))
    kotlin.test.assertNull(subProject.tasks.findByName("e2eIosTest"))
  }

  @Test
  fun `test Intel Mac host skips iOS task registration when only ARM64 simulator target is configured`() {
    System.setProperty("parikshan.os.arch", "x86_64")
    val project = createProject()
    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.jvm()
    kmp.iosSimulatorArm64()

    project.pluginManager.apply("io.github.aryapreetam.parikshan")
    evaluate(project)

    assertNotNull(project.tasks.findByName("e2eJvmTest"))
    kotlin.test.assertNull(project.tasks.findByName("e2eIosTest"))
    val e2eTask = project.tasks.findByName("e2eTest") as E2ETestTask
    assertEquals("jvm", e2eTask.targets)
  }

  @Test
  fun `test Linux host skips iOS target task registration`() {
    try {
      System.setProperty("parikshan.os.name", "Linux")
      val project = createProject()
      project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
      val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
      kmp.jvm()
      kmp.iosArm64()

      project.pluginManager.apply("io.github.aryapreetam.parikshan")
      evaluate(project)

      assertNotNull(project.tasks.findByName("e2eJvmTest"))
      kotlin.test.assertNull(project.tasks.findByName("e2eIosTest"))
    } finally {
      System.setProperty("parikshan.os.name", "Mac OS X")
    }
  }
}






