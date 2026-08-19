package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalWasmDsl::class)
class KmpWasmSourceResolverTest {

  private lateinit var testProjectDir: File

  @BeforeTest
  fun setup() {
    testProjectDir = kotlin.io.path.createTempDirectory("parikshan-resolver-test").toFile().canonicalFile
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
  fun `test standard single-target wasmJs layout resolution`() {
    val project = createProject()
    
    // Create physical wasmJsMain/kotlin directory on disk
    val wasmMainDir = File(testProjectDir, "src/wasmJsMain/kotlin").apply { mkdirs() }
    File(wasmMainDir, "main.kt").apply { writeText("fun main() {}") }

    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.wasmJs { browser() }

    evaluate(project)

    // Verify resolved source set name is exactly "wasmJsMain"
    val resolvedSourceSet = KmpWasmSourceResolver.resolveWasmMainSourceSet(project)
    assertNotNull(resolvedSourceSet)
    assertEquals("wasmJsMain", resolvedSourceSet.name)

    // Verify that the files list tracks the correct physical folder
    val sourceFiles = project.objects.fileCollection()
    KmpWasmSourceResolver.resolveWasmSources(project, sourceFiles)
    
    assertTrue(sourceFiles.files.any { it.canonicalPath == wasmMainDir.canonicalPath })
  }

  @Test
  fun `test shared-parent webMain layout resolution`() {
    val project = createProject()
    
    // Create physical webMain/kotlin directory on disk
    val webMainDir = File(testProjectDir, "src/webMain/kotlin").apply { mkdirs() }
    File(webMainDir, "main.kt").apply { writeText("fun main() {}") }

    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.js { browser() }
    kmp.wasmJs { browser() }

    evaluate(project)

    // Verify that the resolver resolves to the shared "webMain" parent set
    val resolvedSourceSet = KmpWasmSourceResolver.resolveWasmMainSourceSet(project)
    assertNotNull(resolvedSourceSet)
    assertEquals("webMain", resolvedSourceSet.name)

    // Verify that resolved sources contain webMain directory
    val sourceFiles = project.objects.fileCollection()
    KmpWasmSourceResolver.resolveWasmSources(project, sourceFiles)
    
    assertTrue(sourceFiles.files.any { it.canonicalPath == webMainDir.canonicalPath })
  }

  @Test
  fun `test custom target name layout resolution`() {
    val project = createProject()
    
    // Create physical custom target source directory on disk
    val customMainDir = File(testProjectDir, "src/customWebMain/kotlin").apply { mkdirs() }
    File(customMainDir, "main.kt").apply { writeText("fun main() {}") }

    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    
    // Register custom-named target
    kmp.wasmJs("customWeb") { browser() }

    evaluate(project)

    // Verify that our resolver successfully identifies the custom "customWebMain" source set
    val resolvedSourceSet = KmpWasmSourceResolver.resolveWasmMainSourceSet(project)
    assertNotNull(resolvedSourceSet)
    assertEquals("customWebMain", resolvedSourceSet.name)

    // Verify that resolved sources track the custom target's physical folder
    val sourceFiles = project.objects.fileCollection()
    KmpWasmSourceResolver.resolveWasmSources(project, sourceFiles)
    
    assertTrue(sourceFiles.files.any { it.canonicalPath == customMainDir.canonicalPath })
  }

  @Test
  fun `test resolver filters out build directory generated directories`() {
    val project = createProject()
    
    // Create a physical custom target source directory on disk
    val customMainDir = File(testProjectDir, "src/wasmJsMain/kotlin").apply { mkdirs() }
    File(customMainDir, "main.kt").apply { writeText("fun main() {}") }

    // Create a fake generated directory inside build directory
    val buildDir = File(testProjectDir, "build/generated/compose/resourceGenerator/kotlin/wasmJsMainResourceCollectors").apply { mkdirs() }

    project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
    val kmp = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
    kmp.wasmJs { browser() }

    evaluate(project)

    // Add the fake generated directory dynamically to the resolved source set's srcDirs to mimic Compose Multiplatform resource generation
    val resolvedSourceSet = KmpWasmSourceResolver.resolveWasmMainSourceSet(project)
    assertNotNull(resolvedSourceSet)
    resolvedSourceSet.kotlin.srcDirs(buildDir)

    // Run the resolver
    val sourceFiles = project.objects.fileCollection()
    KmpWasmSourceResolver.resolveWasmSources(project, sourceFiles)
    
    // Verify that the actual source directory is tracked
    assertTrue(sourceFiles.files.any { it.canonicalPath == customMainDir.canonicalPath }, "Physical source directory should be tracked")
    
    // Verify that the build-generated directory is filtered out and NOT tracked
    assertTrue(sourceFiles.files.none { it.canonicalPath == buildDir.canonicalPath }, "Build-generated directory must be filtered out to avoid Gradle validation clashes")
  }
}
