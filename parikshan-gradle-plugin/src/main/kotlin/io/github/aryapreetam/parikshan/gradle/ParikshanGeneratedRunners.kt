package io.github.aryapreetam.parikshan.gradle

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.w3c.dom.Element
import org.w3c.dom.Node

internal fun Project.configureGeneratedParikshanRunners(
  extension: ParikshanExtension
) {
  val commonTestSourceDisplayPath = projectDir.resolve("src/commonTest/kotlin").invariantSeparatorsPath
  val commonTestSources =
    fileTree("src/commonTest/kotlin") {
      include("**/*.kt")
    }
  val generatedRootDir = layout.buildDirectory.dir("generated/parikshan/runners")
  val generatedJvmDir = generatedRootDir.map { it.dir("jvmTest/kotlin") }
  val generatedAndroidDir = generatedRootDir.map { it.dir("androidInstrumentedTest/kotlin") }
  val hasAndroidTestCompilation = tasks.names.any { it.endsWith("AndroidTestKotlinAndroid") }
  val resolvedAndroidLaunchActivityClassName =
    if (hasAndroidTestCompilation) {
      resolveAndroidLaunchActivityClassName(
        overrideName = extension.androidLaunchActivityClassName.orNull
      )
    } else {
      null
    }

  configureGeneratedKotlinSourceDir(
    sourceSetName = "jvmTest",
    sourceDir = generatedJvmDir.get().asFile
  )
  if (hasAndroidTestCompilation) {
    configureGeneratedAndroidSourceDir(
      sourceSetName = "androidTest",
      sourceDir = generatedAndroidDir.get().asFile
    )
  }

  val generateRunnersTask =
    tasks.register("generateParikshanRunners") {
      group = "verification"
      description = "Generates Parikshan JVM and Android runner shells from commonTest scenarios"

      inputs.files(commonTestSources)
      inputs.property("androidLaunchActivityClassNameOverride", extension.androidLaunchActivityClassName.orNull ?: "")
      outputs.dir(generatedJvmDir)
      outputs.dir(generatedAndroidDir)

      doLast {
        val scenarios =
          commonTestSources.files
            .sortedBy { it.invariantSeparatorsPath }
            .flatMap { file -> file.parseParikshanScenarios() }

        val jvmOutputDir = generatedJvmDir.get().asFile
        val androidOutputDir = generatedAndroidDir.get().asFile
        jvmOutputDir.deleteRecursively()
        androidOutputDir.deleteRecursively()
        jvmOutputDir.mkdirs()
        androidOutputDir.mkdirs()

        if (scenarios.isEmpty()) {
          logger.lifecycle(
            "Parikshan: No @ParikshanScenario functions found in $commonTestSourceDisplayPath; skipping generated runners."
          )
          return@doLast
        }

        scenarios.validateUniqueTestNames()
        scenarios.writeJvmRunners(outputDir = jvmOutputDir)

        resolvedAndroidLaunchActivityClassName?.let { launchActivityClassName ->
          scenarios.writeAndroidRunners(
            outputDir = androidOutputDir,
            launchActivityClassName = launchActivityClassName
          )
        }

        logger.lifecycle(
          "Parikshan: Generated ${scenarios.size} scenario runners from ${commonTestSources.files.size} commonTest source file(s)."
        )
      }
    }

  tasks.matching { it.name == "compileTestKotlinJvm" }.configureEach {
    dependsOn(generateRunnersTask)
  }
  tasks.matching { it.name.endsWith("AndroidTestKotlinAndroid") }.configureEach {
    dependsOn(generateRunnersTask)
  }
}

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

private data class ParikshanScenarioDefinition(
  val file: File,
  val packageName: String,
  val functionName: String,
  val testName: String
)

private fun File.parseParikshanScenarios(): List<ParikshanScenarioDefinition> {
  val source = readText()
  val packageName =
    PACKAGE_REGEX.find(source)?.groupValues?.get(1).orEmpty()

  return PARIKSHAN_SCENARIO_REGEX.findAll(source).map { match ->
    val explicitTestName = match.groupValues[1].trim()
    val functionName = match.groupValues[2].trim()
    val rawParameters = match.groupValues[3].trim()

    if (rawParameters.hasRequiredParameters()) {
      throw GradleException(
        "Parikshan: @ParikshanScenario function '$functionName' in ${invariantSeparatorsPath} must not declare required parameters. " +
          "Use only the E2ETestScope receiver plus optional parameters with defaults."
      )
    }

    ParikshanScenarioDefinition(
      file = this,
      packageName = packageName,
      functionName = functionName,
      testName = explicitTestName.ifBlank { functionName }
    )
  }.toList()
}

private fun String.hasRequiredParameters(): Boolean {
  if (isBlank()) {
    return false
  }
  return split(',')
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .any { '=' !in it }
}

private fun List<ParikshanScenarioDefinition>.validateUniqueTestNames() {
  groupBy { it.packageName }.forEach { (packageName, definitions) ->
    val duplicates =
      definitions
        .groupBy { it.testName }
        .filterValues { it.size > 1 }
    if (duplicates.isNotEmpty()) {
      val duplicateSummary =
        duplicates.entries.joinToString(separator = ", ") { (testName, matchingDefinitions) ->
          "$testName -> ${matchingDefinitions.joinToString { it.file.name }}"
        }
      val packageLabel = packageName.ifBlank { "<root package>" }
      throw GradleException(
        "Parikshan: Duplicate generated test names in package $packageLabel: $duplicateSummary"
      )
    }
  }
}

private fun List<ParikshanScenarioDefinition>.writeJvmRunners(outputDir: File) {
  groupBy { it.packageName }.forEach { (packageName, definitions) ->
    outputDir.resolvePackageDir(packageName).mkdirs()
    outputDir.resolvePackageFile(packageName, "ParikshanE2ETest.kt").writeText(
      renderJvmRunnerSource(packageName, definitions.sortedBy { it.testName })
    )
  }
}

private fun List<ParikshanScenarioDefinition>.writeAndroidRunners(
  outputDir: File,
  launchActivityClassName: String
) {
  groupBy { it.packageName }.forEach { (packageName, definitions) ->
    outputDir.resolvePackageDir(packageName).mkdirs()
    outputDir.resolvePackageFile(packageName, "ParikshanAndroidE2ETest.kt").writeText(
      renderAndroidRunnerSource(
        packageName = packageName,
        definitions = definitions.sortedBy { it.testName },
        launchActivityClassName = launchActivityClassName
      )
    )
  }
}

private fun renderJvmRunnerSource(
  packageName: String,
  definitions: List<ParikshanScenarioDefinition>
): String =
  buildString {
    appendGeneratedFileHeader(packageName)
    appendLine("import io.github.aryapreetam.parikshan.e2eTest")
    appendLine("import kotlin.test.Test")
    appendLine()
    appendLine("class ParikshanE2ETest {")
    definitions.forEach { definition ->
      appendLine("  @Test")
      appendLine("  fun ${definition.testName}() =")
      appendLine("    e2eTest {")
      appendLine("      ${definition.functionName}()")
      appendLine("    }")
      appendLine()
    }
    appendLine("}")
  }

private fun renderAndroidRunnerSource(
  packageName: String,
  definitions: List<ParikshanScenarioDefinition>,
  launchActivityClassName: String
): String =
  buildString {
    appendGeneratedFileHeader(packageName)
    appendLine("import androidx.compose.ui.test.junit4.createAndroidComposeRule")
    appendLine("import androidx.test.ext.junit.runners.AndroidJUnit4")
    appendLine("import io.github.aryapreetam.parikshan.e2eTest")
    appendLine("import kotlin.test.Test")
    appendLine("import org.junit.Rule")
    appendLine("import org.junit.runner.RunWith")
    appendLine("import $launchActivityClassName")
    appendLine()
    appendLine("@RunWith(AndroidJUnit4::class)")
    appendLine("class ParikshanAndroidE2ETest {")
    appendLine("  @get:Rule")
    appendLine("  val composeRule = createAndroidComposeRule<${launchActivityClassName.substringAfterLast('.')}>()")
    appendLine()
    definitions.forEach { definition ->
      appendLine("  @Test")
      appendLine("  fun ${definition.testName}() =")
      appendLine("    e2eTest(composeRule) {")
      appendLine("      ${definition.functionName}()")
      appendLine("    }")
      appendLine()
    }
    appendLine("}")
  }

private fun StringBuilder.appendGeneratedFileHeader(packageName: String) {
  if (packageName.isNotBlank()) {
    appendLine("package $packageName")
    appendLine()
  }
  appendLine("// Generated by Parikshan. Do not edit.")
  appendLine()
}

private fun File.resolvePackageDir(packageName: String): File =
  if (packageName.isBlank()) {
    this
  } else {
    resolve(packageName.replace('.', File.separatorChar))
  }

private fun File.resolvePackageFile(
  packageName: String,
  fileName: String
): File = resolvePackageDir(packageName).resolve(fileName)

private fun Project.resolveAndroidLaunchActivityClassName(overrideName: String?): String {
  val requestedName = overrideName?.trim().orEmpty()
  if (requestedName.isNotEmpty()) {
    return requestedName
  }

  val manifestFile =
    listOf(
      projectDir.resolve("src/androidMain/AndroidManifest.xml"),
      projectDir.resolve("src/main/AndroidManifest.xml")
    ).firstOrNull { it.exists() && it.isFile }
      ?: throw GradleException(
        "Parikshan could not find an AndroidManifest.xml to auto-detect the launch activity. " +
          "Set parikshan.androidLaunchActivityClassName to override."
      )

  val manifestPackage = parseManifestPackageName(manifestFile)
  val namespace = resolveAndroidNamespace() ?: manifestPackage
  val rawActivityName = parseLauncherActivityName(manifestFile)
    ?: throw GradleException(
      "Parikshan could not find a launcher activity in ${manifestFile.invariantSeparatorsPath}. " +
        "Set parikshan.androidLaunchActivityClassName to override."
    )

  return qualifyAndroidClassName(
    rawClassName = rawActivityName,
    packageName = namespace
  )
}

private fun Project.resolveAndroidNamespace(): String? {
  val androidExtension = extensions.findByName("android") ?: return null
  return runCatching {
    androidExtension.javaClass.methods.firstOrNull { it.name == "getNamespace" }?.invoke(androidExtension) as? String
  }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun qualifyAndroidClassName(
  rawClassName: String,
  packageName: String?
): String =
  when {
    rawClassName.startsWith(".") -> {
      val resolvedPackage = requireNotNull(packageName) {
        "Parikshan could not resolve Android launch activity '$rawClassName' because the application namespace is missing."
      }
      "$resolvedPackage$rawClassName"
    }

    '.' in rawClassName -> rawClassName
    else -> {
      val resolvedPackage = requireNotNull(packageName) {
        "Parikshan could not resolve Android launch activity '$rawClassName' because the application namespace is missing."
      }
      "$resolvedPackage.$rawClassName"
    }
  }

private fun parseManifestPackageName(manifestFile: File): String? {
  val document = parseXmlDocument(manifestFile) ?: return null
  return document.documentElement?.getAttribute("package")?.takeIf { it.isNotBlank() }
}

private fun parseLauncherActivityName(manifestFile: File): String? {
  val document = parseXmlDocument(manifestFile) ?: return null
  val activityNodes = document.getElementsByTagName("activity")
  for (index in 0 until activityNodes.length) {
    val activity = activityNodes.item(index) as? Element ?: continue
    if (activity.isLauncherActivity()) {
      return activity.getAttributeNS(ANDROID_XML_NAMESPACE, "name").takeIf { it.isNotBlank() }
        ?: activity.getAttribute("android:name").takeIf { it.isNotBlank() }
    }
  }
  return null
}

private fun Element.isLauncherActivity(): Boolean {
  val children = childNodes ?: return false
  for (index in 0 until children.length) {
    val child = children.item(index)
    if (child.nodeType != Node.ELEMENT_NODE || child.nodeName != "intent-filter") {
      continue
    }
    val intentFilter = child as Element
    val hasMainAction =
      intentFilter.getElementsByTagName("action").toElementList().any { action ->
        action.getAttributeNS(ANDROID_XML_NAMESPACE, "name") == "android.intent.action.MAIN" ||
          action.getAttribute("android:name") == "android.intent.action.MAIN"
      }
    val hasLauncherCategory =
      intentFilter.getElementsByTagName("category").toElementList().any { category ->
        category.getAttributeNS(ANDROID_XML_NAMESPACE, "name") == "android.intent.category.LAUNCHER" ||
          category.getAttribute("android:name") == "android.intent.category.LAUNCHER"
      }
    if (hasMainAction && hasLauncherCategory) {
      return true
    }
  }
  return false
}

private fun org.w3c.dom.NodeList.toElementList(): List<Element> =
  buildList {
    for (index in 0 until length) {
      val element = item(index) as? Element ?: continue
      add(element)
    }
  }

private fun parseXmlDocument(manifestFile: File): org.w3c.dom.Document? {
  return runCatching {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.isNamespaceAware = true
    factory.newDocumentBuilder().parse(manifestFile)
  }.getOrNull()
}

private fun Project.configureGeneratedKotlinSourceDir(
  sourceSetName: String,
  sourceDir: File
) {
  val kotlinExtension = extensions.findByName("kotlin") ?: return
  val sourceSets =
    runCatching {
      kotlinExtension.javaClass.methods.firstOrNull { it.name == "getSourceSets" }?.invoke(kotlinExtension)
    }.getOrNull()
      ?: return
  val sourceSet =
    runCatching {
      sourceSets.javaClass.methods.firstOrNull {
        it.name == "findByName" && it.parameterCount == 1
      }?.invoke(sourceSets, sourceSetName)
    }.getOrNull()
      ?: return
  val kotlinSourceDirectorySet =
    runCatching {
      sourceSet.javaClass.methods.firstOrNull { it.name == "getKotlin" && it.parameterCount == 0 }?.invoke(sourceSet)
    }.getOrNull()
      ?: return

  runCatching {
    kotlinSourceDirectorySet.javaClass.methods
      .firstOrNull { it.name == "srcDir" && it.parameterCount == 1 }
      ?.invoke(kotlinSourceDirectorySet, sourceDir)
  }
}

private fun Project.configureGeneratedAndroidSourceDir(
  sourceSetName: String,
  sourceDir: File
) {
  val androidExtension = extensions.findByName("android") ?: return
  val sourceSets =
    runCatching {
      androidExtension.javaClass.methods.firstOrNull { it.name == "getSourceSets" }?.invoke(androidExtension)
    }.getOrNull()
      ?: return
  val sourceSet =
    runCatching {
      sourceSets.javaClass.methods.firstOrNull {
        it.name == "findByName" && it.parameterCount == 1
      }?.invoke(sourceSets, sourceSetName)
    }.getOrNull()
      ?: return

  listOf("getKotlin", "getJava").forEach { accessor ->
    val directorySet =
      runCatching {
        sourceSet.javaClass.methods.firstOrNull { it.name == accessor && it.parameterCount == 0 }?.invoke(sourceSet)
      }.getOrNull()
        ?: return@forEach

    runCatching {
      directorySet.javaClass.methods
        .firstOrNull { it.name == "srcDir" && it.parameterCount == 1 }
        ?.invoke(directorySet, sourceDir)
    }
  }
}

private val PACKAGE_REGEX =
  Regex("""(?m)^\s*package\s+([A-Za-z0-9_.]+)\s*$""")

private val PARIKSHAN_SCENARIO_REGEX =
  Regex(
    pattern =
      """@(?:io\.github\.aryapreetam\.parikshan\.)?ParikshanScenario(?:\(\s*(?:(?:testName\s*=\s*)?"([^"]+)")?\s*\))?\s*(?:public\s+|internal\s+)?suspend\s+fun\s+E2ETestScope\.([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)""",
    options = setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
  )

private const val ANDROID_XML_NAMESPACE = "http://schemas.android.com/apk/res/android"
private const val LEGACY_ANDROID_TEST_RUNNER = "android.test.InstrumentationTestRunner"
private const val MODERN_ANDROID_TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner"
