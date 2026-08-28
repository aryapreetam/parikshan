package io.github.aryapreetam.parikshan.gradle

import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectCollection
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileCollection

/**
 * Immutable specification holding the resolved test classes, runtime classpath,
 * and compilation task dependencies required to execute the Parikshan host JVM test runner.
 */
data class HostTestClasspathSpec(
  val testClassesDirs: FileCollection,
  val runtimeClasspath: FileCollection,
  val compileDependencies: List<Any>
)

/**
 * Centralized, deterministic resolver for Parikshan host test execution classpaths.
 *
 * Implements a priority-based strategy pipeline:
 * 1. Primary JVM Target: Resolves test & main compilation outputs from KMP JVM / Desktop targets.
 * 2. Android Host Target: Resolves Android host unit test bytecode (KMP Android or standalone AGP).
 * 3. Fallback: Generic unit test compilation discovery for standalone non-KMP projects.
 */
object HostTestClasspathResolver {

  fun resolve(project: Project): HostTestClasspathSpec {
    val hasKmp = project.pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
    val kmp = if (hasKmp) project.extensions.findByName("kotlin") else null

    if (hasKmp && kmp != null) {
      val kmpSpec = resolveKmpJvmTarget(project, kmp)
      if (kmpSpec != null) {
        return kmpSpec
      }
    }

    val spec = resolveNonKmpOrAndroidTarget(project)
    val hasAndroid = project.pluginManager.hasPlugin("com.android.application") ||
                     project.pluginManager.hasPlugin("com.android.library") ||
                     project.pluginManager.hasPlugin("com.android.kotlin.multiplatform.library")

    if (hasKmp && !hasAndroid && spec.testClassesDirs.isEmpty) {
      throw org.gradle.api.GradleException(
        "Parikshan: No JVM host target found in project '${project.path}'. " +
        "Parikshan E2E orchestration requires at least one JVM/Desktop target (e.g., `jvm()`) to compile and execute host-side test runners. " +
        "See: https://aryapreetam.github.io/parikshan/getting-started/known-limitations/#host-jvm-target-requirement"
      )
    }

    return spec
  }

  private fun resolveKmpJvmTarget(project: Project, kmp: Any): HostTestClasspathSpec? {
    return try {
      @Suppress("UNCHECKED_CAST")
      val targets = kmp.javaClass.getMethod("getTargets").invoke(kmp) as NamedDomainObjectCollection<Any>
      val jvmTargets = targets.filter { t ->
        val targetName = (t as Named).name
        val className = t.javaClass.name
        className.contains("KotlinJvmTarget", ignoreCase = true) ||
          targetName.contains("jvm", ignoreCase = true) ||
          targetName.contains("desktop", ignoreCase = true)
      }

      // Prioritize explicit user targets ('desktop', 'jvm')
      val jvmTarget = jvmTargets.find { (it as Named).name in listOf("desktop", "jvm") }
        ?: jvmTargets.firstOrNull()

      if (jvmTarget == null) return null

      @Suppress("UNCHECKED_CAST")
      val compilations = jvmTarget.javaClass.getMethod("getCompilations").invoke(jvmTarget) as NamedDomainObjectCollection<Any>
      val testCompilation = compilations.findByName("test") ?: return null
      val mainCompilation = compilations.findByName("main")

      val testOutput = testCompilation.javaClass.getMethod("getOutput").invoke(testCompilation)
      @Suppress("UNCHECKED_CAST")
      val testClassesDirs = testOutput.javaClass.getMethod("getClassesDirs").invoke(testOutput) as FileCollection
      @Suppress("UNCHECKED_CAST")
      val runtimeFiles = testCompilation.javaClass.getMethod("getRuntimeDependencyFiles").invoke(testCompilation) as FileCollection

      val mainClassesDirs = mainCompilation?.let {
        val mainOutput = it.javaClass.getMethod("getOutput").invoke(it)
        @Suppress("UNCHECKED_CAST")
        mainOutput.javaClass.getMethod("getClassesDirs").invoke(mainOutput) as? FileCollection
      }

      val hostClasspathList = mutableListOf<Any>()
      val clientProject = project.rootProject.findProject(":parikshan-client")
      if (clientProject != null) {
        val clientDep = project.dependencies.project(mapOf("path" to clientProject.path))
        val hostJvmConfig = project.configurations.detachedConfiguration(clientDep)
        hostClasspathList.add(hostJvmConfig.incoming.files)
      }

      hostClasspathList.add(runtimeFiles)
      hostClasspathList.add(testClassesDirs)
      if (mainClassesDirs != null) {
        hostClasspathList.add(mainClassesDirs)
      }

      val compileDeps = mutableListOf<Any>()
      compileDeps.add(testClassesDirs.buildDependencies)
      if (mainClassesDirs != null) {
        compileDeps.add(mainClassesDirs.buildDependencies)
      }

      HostTestClasspathSpec(
        testClassesDirs = testClassesDirs,
        runtimeClasspath = project.files(hostClasspathList),
        compileDependencies = compileDeps
      )
    } catch (_: Exception) {
      null
    }
  }

  private fun resolveNonKmpOrAndroidTarget(project: Project): HostTestClasspathSpec {
    val compileTasks = project.tasks.matching { task ->
      val n = task.name
      n in setOf(
        "compileDebugUnitTestKotlin",
        "compileDebugUnitTestJavaWithJavac",
        "compileAndroidHostTest",
        "compileTestKotlin",
        "compileTestJava"
      ) || (n.startsWith("compile") && (n.endsWith("UnitTestKotlin") || n.endsWith("UnitTestJavaWithJavac")))
    }

    val e2eTestDirs = project.files(
      compileTasks.map { task ->
        task.outputs.files.filter { dir ->
          dir.isDirectory && (dir.path.contains("classes") || dir.name == "classes" || dir.name == "hostTest")
        }
      }
    )

    val jvmRuntimeConfig = project.configurations.findByName("debugUnitTestRuntimeClasspath")
      ?: project.configurations.findByName("androidUnitTestRuntimeClasspath")
      ?: project.configurations.findByName("androidHostTestRuntimeClasspath")
      ?: project.configurations.findByName("testRuntimeClasspath")

    val hostClasspathList = mutableListOf<Any>()
    val clientProject = project.rootProject.findProject(":parikshan-client")
    if (clientProject != null) {
      val clientDep = project.dependencies.project(mapOf("path" to clientProject.path))
      val hostJvmConfig = project.configurations.detachedConfiguration(clientDep)
      hostClasspathList.add(hostJvmConfig.incoming.files)
    }

    if (jvmRuntimeConfig != null) {
      val artifactTypeAttr = Attribute.of("artifactType", String::class.java)
      val resolvedJars = jvmRuntimeConfig.incoming.artifactView {
        attributes { attribute(artifactTypeAttr, "jar") }
        lenient(true)
      }.files
      hostClasspathList.add(project.files(resolvedJars))
      hostClasspathList.add(e2eTestDirs)
    } else {
      hostClasspathList.add(e2eTestDirs)
    }

    return HostTestClasspathSpec(
      testClassesDirs = e2eTestDirs,
      runtimeClasspath = project.files(hostClasspathList),
      compileDependencies = listOf(compileTasks)
    )
  }
}

/**
 * Extension on Gradle [Project] to cleanly retrieve the [HostTestClasspathSpec].
 */
fun Project.resolveHostTestClasspathSpec(): HostTestClasspathSpec {
  return HostTestClasspathResolver.resolve(this)
}
