package io.github.aryapreetam.parikshan.gradle

import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class E2ETestCase(
  val name: String,
  val methodName: String,
  val className: String,
  val packageName: String,
  val duration: Double,
  val status: String, // "passed", "failed", "ignored"
  val failureMessage: String?,
  val failureType: String?,
  val failureDetail: String?,
  val videoPath: String? = null
)

internal data class E2EClassSummary(
  val name: String,
  val packageName: String,
  val tests: Int,
  val failures: Int,
  val errors: Int,
  val ignored: Int,
  val time: Double,
  val testCases: List<E2ETestCase>
)

internal data class E2EPackageSummary(
  val name: String,
  val tests: Int,
  val failures: Int,
  val errors: Int,
  val ignored: Int,
  val time: Double,
  val classes: List<E2EClassSummary>
)

internal fun generateUnifiedReport(
  resDirFile: File,
  repDirFile: File,
  logger: org.gradle.api.logging.Logger
) {
  repDirFile.deleteRecursively()
  repDirFile.mkdirs()
  
  val testCases = mutableListOf<E2ETestCase>()
  val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
  
  val buildDir = resDirFile.let {
    var dir: File? = it
    while (dir != null && dir.name != "build") {
      dir = dir.parentFile
    }
    dir ?: it.parentFile.parentFile
  }

  if (resDirFile.exists()) {
    val xmlFiles = resDirFile.walkTopDown()
      .filter { it.isFile && it.name.startsWith("TEST-") && it.extension.lowercase() == "xml" }
      .toList()
      
    xmlFiles.forEach { file ->
       val pathSegments = file.absolutePath.split(File.separator)
       val e2eTestIdx = pathSegments.indexOfLast { it.equals("e2eTest", ignoreCase = true) }
       val rawTarget = if (e2eTestIdx >= 0 && e2eTestIdx < pathSegments.size - 1) {
         pathSegments[e2eTestIdx + 1].lowercase()
       } else {
         file.parentFile.name.lowercase()
       }
       val target = when (rawTarget) {
         "desktop" -> "desktop"
         "wasm" -> "wasm"
         "ios" -> "ios"
         "android" -> "android"
         else -> rawTarget
       }
      
      try {
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(file)
        doc.documentElement.normalize()
        
        val caseNodes = doc.getElementsByTagName("testcase")
        
        val rawClassName = if (file.name.contains("junit-")) {
          file.parentFile.name
        } else {
          file.name.substringAfter("TEST-").substringBefore(".xml")
        }
        val logFile = File(buildDir, "parikshan/logs/$target-$rawClassName.log")
        val suiteVideoPaths = mutableListOf<String>()

        // 1. Read from target-specific video index file
        val targetIndexFile = File(buildDir, "parikshan/videos/$target/video-index.txt")
        if (targetIndexFile.exists()) {
          targetIndexFile.useLines { lines ->
            lines.forEach { line ->
              val trimmed = line.trim()
              if (trimmed.isNotEmpty()) {
                suiteVideoPaths.add(trimmed)
              }
            }
          }
        }

        // 2. Read from XML system-out tags
        val systemOuts = doc.getElementsByTagName("system-out")
        for (j in 0 until systemOuts.length) {
          val content = systemOuts.item(j).textContent
          content.lineSequence().forEach { line ->
            if (line.contains("[PARIKSHAN_VIDEO_PATH]")) {
              suiteVideoPaths.add(line.substringAfter("[PARIKSHAN_VIDEO_PATH]").trim())
            }
          }
        }

        // 3. Fallback: Read from the old target execution log file
        if (logFile.exists()) {
          logFile.useLines { lines ->
            lines.forEach { line ->
              if (line.contains("[PARIKSHAN_VIDEO_PATH]")) {
                suiteVideoPaths.add(line.substringAfter("[PARIKSHAN_VIDEO_PATH]").trim())
              }
            }
          }
        }

        for (i in 0 until caseNodes.length) {
          val caseNode = caseNodes.item(i) as org.w3c.dom.Element
          val rawMethodName = caseNode.getAttribute("name")                                                                                                                       
          val methodName = if (rawMethodName.endsWith("()")) {                                                                                                                    
            rawMethodName.substring(0, rawMethodName.length - 2)                                                                                                                
          } else {                                                                                                                                                                
            rawMethodName                                                                                                                                                       
          }
          val caseClassName = caseNode.getAttribute("classname")
          val caseDuration = caseNode.getAttribute("time").toDoubleOrNull() ?: 0.0
          
          val packageName = caseClassName.substringBeforeLast('.', "")
          val nameWithTarget = "$methodName[$target]"
          
          var status = "passed"
          var failureMessage: String? = null
          var failureType: String? = null
          var failureDetail: String? = null
          
          val failureNodes = caseNode.getElementsByTagName("failure")
          val errorNodes = caseNode.getElementsByTagName("error")
          val skippedNodes = caseNode.getElementsByTagName("skipped")
          
          if (failureNodes.length > 0) {
            status = "failed"
            val failureEl = failureNodes.item(0) as org.w3c.dom.Element
            failureMessage = failureEl.getAttribute("message").takeIf { it.isNotBlank() }
            failureType = failureEl.getAttribute("type").takeIf { it.isNotBlank() }
            failureDetail = failureEl.textContent.takeIf { it.isNotBlank() }
          } else if (errorNodes.length > 0) {
            status = "failed"
            val errorEl = errorNodes.item(0) as org.w3c.dom.Element
            failureMessage = errorEl.getAttribute("message").takeIf { it.isNotBlank() }
            failureType = errorEl.getAttribute("type").takeIf { it.isNotBlank() }
            failureDetail = errorEl.textContent.takeIf { it.isNotBlank() }
          } else if (skippedNodes.length > 0) {
            status = "ignored"
          }

          val existingVideoPaths = suiteVideoPaths.filter { File(it).let { f -> f.exists() && f.length() > 0L } }
          val videoPath = existingVideoPaths.firstOrNull { path ->
            path.contains(methodName)
          } ?: existingVideoPaths.firstOrNull { path ->
            path.contains(caseClassName.substringAfterLast('.'))
          } ?: existingVideoPaths.firstOrNull()
          testCases.add(E2ETestCase(
            name = nameWithTarget,
            methodName = methodName,
            className = caseClassName,
            packageName = packageName,
            duration = caseDuration,
            status = status,
            failureMessage = failureMessage,
            failureType = failureType,
            failureDetail = failureDetail,
            videoPath = videoPath
          ))
        }
      } catch (e: Exception) {
        logger.error("Parikshan: Failed to parse XML test report: ${file.absolutePath}", e)
      }
    }
  }
  
  val totalTests = testCases.size
  val totalFailures = testCases.count { it.status == "failed" }
  val totalIgnored = testCases.count { it.status == "ignored" }
  val totalDuration = testCases.sumOf { it.duration }
  val successRate = if (totalTests - totalIgnored > 0) {
    ((totalTests - totalFailures - totalIgnored) * 100) / (totalTests - totalIgnored)
  } else {
    100
  }
  val successRateClass = if (totalFailures > 0) "failures" else "success"
  
  val classesList = testCases.groupBy { it.className }.map { (className, cases) ->
    val pkgName = cases.first().packageName
    val total = cases.size
    val failures = cases.count { it.status == "failed" }
    val ignored = cases.count { it.status == "ignored" }
    val duration = cases.sumOf { it.duration }
    E2EClassSummary(className, pkgName, total, failures, 0, ignored, duration, cases)
  }
  
  val packagesList = classesList.groupBy { it.packageName }.map { (pkgName, classes) ->
    val total = classes.sumOf { it.tests }
    val failures = classes.sumOf { it.failures }
    val ignored = classes.sumOf { it.ignored }
    val duration = classes.sumOf { it.time }
    E2EPackageSummary(pkgName, total, failures, 0, ignored, duration, classes)
  }
  
  // Write CSS & JS assets
  val cssDir = File(repDirFile, "css")
  cssDir.mkdirs()
  File(cssDir, "base-style.css").writeText(BASE_STYLE_CSS)
  File(cssDir, "style.css").writeText(STYLE_CSS)
  
  val jsDir = File(repDirFile, "js")
  jsDir.mkdirs()
  File(jsDir, "report.js").writeText(REPORT_JS)
  
  // Write index.html
  val indexHtml = File(repDirFile, "index.html")
  indexHtml.writeText(generateIndexHtml(totalTests, totalFailures, totalIgnored, totalDuration, successRate, successRateClass, packagesList, classesList, repDirFile))
  
  // Write package files
  val packagesDir = File(repDirFile, "packages")
  packagesDir.mkdirs()
  packagesList.forEach { pkg ->
    File(packagesDir, "${pkg.name}.html").writeText(generatePackageHtml(pkg, repDirFile))
  }
  
  // Write class files
  val classesDir = File(repDirFile, "classes")
  classesDir.mkdirs()
  classesList.forEach { clazz ->
    File(classesDir, "${clazz.name}.html").writeText(generateClassHtml(clazz, classesDir))
  }
  
  logger.lifecycle("Parikshan: Unified E2E HTML Report generated at file://${indexHtml.absolutePath}")
}

internal fun generateIndexHtml(
  totalTests: Int,
  totalFailures: Int,
  totalIgnored: Int,
  totalDuration: Double,
  successRate: Int,
  successRateClass: String,
  packages: List<E2EPackageSummary>,
  classes: List<E2EClassSummary>,
  repDirFile: File
): String {
  val dateString = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")
    .format(ZonedDateTime.now())
  
  val allTestCases = classes.flatMap { it.testCases }
  val allVideoPaths = allTestCases.mapNotNull { it.videoPath }.distinct()
  val videoStrategy = detectVideoStrategy(allVideoPaths)
  
  val packagesRows = packages.joinToString("\n") { pkg ->
    val statusClass = if (pkg.failures > 0) "failures" else "success"
    val videoCell = if (videoStrategy == ReportVideoStrategy.RUN) {
      val paths = pkg.classes.flatMap { it.testCases }.mapNotNull { it.videoPath }.distinct()
      if (paths.isNotEmpty()) {
        val links = paths.mapNotNull { path ->
          val targetName = File(path).parentFile.name
          val relVideo = getRelativeVideoPath(repDirFile, path)
          if (relVideo != null) "<a href=\"$relVideo\" target=\"_blank\">$targetName</a>" else null
        }.filterNotNull().joinToString(" | ")
        "<td>${links.ifEmpty { "-" }}</td>"
      } else "<td>-</td>"
    } else ""
    """
<tr>
<td class="$statusClass">
<a href="packages/${pkg.name}.html">${pkg.name}</a>
</td>
<td>${pkg.tests}</td>
<td>${pkg.failures}</td>
<td>${pkg.ignored}</td>
<td>${String.format(Locale.US, "%.3f", pkg.time)}s</td>
<td class="$statusClass">${if (pkg.tests - pkg.ignored > 0) ((pkg.tests - pkg.failures - pkg.ignored) * 100) / (pkg.tests - pkg.ignored) else 100}%</td>
$videoCell
</tr>
    """.trimIndent()
  }

  val classesRows = classes.joinToString("\n") { clazz ->
    val statusClass = if (clazz.failures > 0) "failures" else "success"
    val videoCell = if (videoStrategy == ReportVideoStrategy.CLASS) {
      val paths = clazz.testCases.mapNotNull { it.videoPath }.distinct()
      if (paths.isNotEmpty()) {
        val links = paths.mapNotNull { path ->
          val targetName = File(path).parentFile.name
          val relVideo = getRelativeVideoPath(repDirFile, path)
          if (relVideo != null) "<a href=\"$relVideo\" target=\"_blank\">$targetName</a>" else null
        }.filterNotNull().joinToString(" | ")
        "<td>${links.ifEmpty { "-" }}</td>"
      } else "<td>-</td>"
    } else ""
    """
<tr>
<td class="$statusClass">
<a href="classes/${clazz.name}.html">${clazz.name}</a>
</td>
<td>${clazz.tests}</td>
<td>${clazz.failures}</td>
<td>${clazz.ignored}</td>
<td>${String.format(Locale.US, "%.3f", clazz.time)}s</td>
<td class="$statusClass">${if (clazz.tests - clazz.ignored > 0) ((clazz.tests - clazz.failures - clazz.ignored) * 100) / (clazz.tests - clazz.ignored) else 100}%</td>
$videoCell
</tr>
    """.trimIndent()
  }

  return """
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="x-ua-compatible" content="IE=edge"/>
<title>Test results - Test Summary</title>
<link href="css/base-style.css" rel="stylesheet" type="text/css"/>
<link href="css/style.css" rel="stylesheet" type="text/css"/>
<script src="js/report.js" type="text/javascript"></script>
</head>
<body>
<div id="content">
<h1>Test Summary</h1>
<div id="summary">
<table>
<tr>
<td>
<div class="summaryGroup">
<table>
<tr>
<td>
<div class="infoBox" id="tests">
<div class="counter">$totalTests</div>
<p>tests</p>
</div>
</td>
<td>
<div class="infoBox" id="failures">
<div class="counter">$totalFailures</div>
<p>failures</p>
</div>
</td>
<td>
<div class="infoBox" id="ignored">
<div class="counter">$totalIgnored</div>
<p>ignored</p>
</div>
</td>
<td>
<div class="infoBox" id="duration">
<div class="counter">${String.format(Locale.US, "%.3f", totalDuration)}s</div>
<p>duration</p>
</div>
</td>
</tr>
</table>
</div>
</td>
<td>
<div class="infoBox $successRateClass" id="successRate">
<div class="percent">$successRate%</div>
<p>successful</p>
</div>
</td>
</tr>
</table>
</div>
<div class="tab-container">
<ul class="tabLinks">
<li>
<a href="#tab0">Packages</a>
</li>
<li>
<a href="#tab1">Classes</a>
</li>
</ul>
<div class="tab" id="tab0">
<h2>Packages</h2>
<table>
<thead>
<tr>
<th>Package</th>
<th>Tests</th>
<th>Failures</th>
<th>Ignored</th>
<th>Duration</th>
<th>Success rate</th>
${if (videoStrategy == ReportVideoStrategy.RUN) "<th>Video</th>" else ""}
</tr>
</thead>
<tbody>
$packagesRows
</tbody>
</table>
</div>
<div class="tab" id="tab1">
<h2>Classes</h2>
<table>
<thead>
<tr>
<th>Class</th>
<th>Tests</th>
<th>Failures</th>
<th>Ignored</th>
<th>Duration</th>
<th>Success rate</th>
${if (videoStrategy == ReportVideoStrategy.CLASS) "<th>Video</th>" else ""}
</tr>
</thead>
<tbody>
$classesRows
</tbody>
</table>
</div>
</div>
<div id="footer">
<p>
<div>
<label class="hidden" id="label-for-line-wrapping-toggle" for="line-wrapping-toggle">Wrap lines
<input id="line-wrapping-toggle" type="checkbox" autocomplete="off"/>
</label>
</div>Generated by 
<a href="https://www.gradle.org">Gradle 9.1.0</a> at $dateString</p>
</div>
</div>
</body>
</html>
  """.trimIndent()
}

internal fun generatePackageHtml(pkg: E2EPackageSummary, repDirFile: File): String {
  val dateString = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")
    .format(ZonedDateTime.now())
  
  val allTestCases = pkg.classes.flatMap { it.testCases }
  val allVideoPaths = allTestCases.mapNotNull { it.videoPath }.distinct()
  val videoStrategy = detectVideoStrategy(allVideoPaths)
  
  val successRate = if (pkg.tests - pkg.ignored > 0) {
    ((pkg.tests - pkg.failures - pkg.ignored) * 100) / (pkg.tests - pkg.ignored)
  } else {
    100
  }
  val successRateClass = if (pkg.failures > 0) "failures" else "success"

  val classesRows = pkg.classes.joinToString("\n") { clazz ->
    val statusClass = if (clazz.failures > 0) "failures" else "success"
    val videoCell = if (videoStrategy == ReportVideoStrategy.CLASS) {
      val paths = clazz.testCases.mapNotNull { it.videoPath }.distinct()
      if (paths.isNotEmpty()) {
        val links = paths.mapNotNull { path ->
          val targetName = File(path).parentFile.name
          val relVideo = getRelativeVideoPath(File(repDirFile, "packages"), path)
          if (relVideo != null) "<a href=\"$relVideo\" target=\"_blank\">$targetName</a>" else null
        }.filterNotNull().joinToString(" | ")
        "<td>${links.ifEmpty { "-" }}</td>"
      } else "<td>-</td>"
    } else ""
    """
<tr>
<td class="$statusClass">
<a href="../classes/${clazz.name}.html">${clazz.name.substringAfterLast('.')}</a>
</td>
<td>${clazz.tests}</td>
<td>${clazz.failures}</td>
<td>${clazz.ignored}</td>
<td>${String.format(Locale.US, "%.3f", clazz.time)}s</td>
<td class="$statusClass">${if (clazz.tests - clazz.ignored > 0) ((clazz.tests - clazz.failures - clazz.ignored) * 100) / (clazz.tests - clazz.ignored) else 100}%</td>
$videoCell
</tr>
    """.trimIndent()
  }

  return """
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="x-ua-compatible" content="IE=edge"/>
<title>Test results - Package ${pkg.name}</title>
<link href="../css/base-style.css" rel="stylesheet" type="text/css"/>
<link href="../css/style.css" rel="stylesheet" type="text/css"/>
<script src="../js/report.js" type="text/javascript"></script>
</head>
<body>
<div id="content">
<h1>Package ${pkg.name}</h1>
<div class="breadcrumbs">
<a href="../index.html">all</a> &gt; ${pkg.name}</div>
<div id="summary">
<table>
<tr>
<td>
<div class="summaryGroup">
<table>
<tr>
<td>
<div class="infoBox" id="tests">
<div class="counter">${pkg.tests}</div>
<p>tests</p>
</div>
</td>
<td>
<div class="infoBox" id="failures">
<div class="counter">${pkg.failures}</div>
<p>failures</p>
</div>
</td>
<td>
<div class="infoBox" id="ignored">
<div class="counter">${pkg.ignored}</div>
<p>ignored</p>
</div>
</td>
<td>
<div class="infoBox" id="duration">
<div class="counter">${String.format(Locale.US, "%.3f", pkg.time)}s</div>
<p>duration</p>
</div>
</td>
</tr>
</table>
</div>
</td>
<td>
<div class="infoBox $successRateClass" id="successRate">
<div class="percent">$successRate%</div>
<p>successful</p>
</div>
</td>
</tr>
</table>
</div>
<div class="tab-container">
<ul class="tabLinks">
<li>
<a href="#tab0">Classes</a>
</li>
</ul>
<div class="tab" id="tab0">
<h2>Classes</h2>
<table>
<thead>
<tr>
<th>Class</th>
<th>Tests</th>
<th>Failures</th>
<th>Ignored</th>
<th>Duration</th>
<th>Success rate</th>
${if (videoStrategy == ReportVideoStrategy.CLASS) "<th>Video</th>" else ""}
</tr>
</thead>
<tbody>
$classesRows
</tbody>
</table>
</div>
</div>
<div id="footer">
<p>
<div>
<label class="hidden" id="label-for-line-wrapping-toggle" for="line-wrapping-toggle">Wrap lines
<input id="line-wrapping-toggle" type="checkbox" autocomplete="off"/>
</label>
</div>Generated by 
<a href="https://www.gradle.org">Gradle 9.1.0</a> at $dateString</p>
</div>
</div>
</body>
</html>
  """.trimIndent()
}

internal fun generateClassHtml(clazz: E2EClassSummary, classesDir: File): String {
  val dateString = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss")
    .format(ZonedDateTime.now())
  
  val allVideoPaths = clazz.testCases.mapNotNull { it.videoPath }.distinct()
  val videoStrategy = detectVideoStrategy(allVideoPaths)
  
  val successRate = if (clazz.tests - clazz.ignored > 0) {
    ((clazz.tests - clazz.failures - clazz.ignored) * 100) / (clazz.tests - clazz.ignored)
  } else {
    100
  }
  val successRateClass = if (clazz.failures > 0) "failures" else "success"
  val simpleClassName = clazz.name.substringAfterLast('.')

  val failedCases = clazz.testCases.filter { it.status == "failed" }
  val hasFailed = failedCases.isNotEmpty()

  val tabLinks = buildString {
    if (hasFailed) {
      append("""
<li>
<a href="#tab0">Failed tests</a>
</li>
<li>
<a href="#tab1">Tests</a>
</li>
      """.trimIndent())
    } else {
      append("""
<li>
<a href="#tab0">Tests</a>
</li>
      """.trimIndent())
    }
  }

  val failedTestsTab = if (hasFailed) {
    val failedBlocks = failedCases.joinToString("\n") { case ->
      val detailEscaped = case.failureDetail?.replace("<", "&lt;")?.replace(">", "&gt;") ?: ""
      val relVideo = getRelativeVideoPath(classesDir, case.videoPath)
      val videoEl = if (relVideo != null) {
        """
<div style="margin-top: 10px;">
<video src="$relVideo" controls style="max-width: 600px; border: 1px solid #ccc; border-radius: 4px;"></video>
</div>
        """.trimIndent()
      } else ""
      """
<div class="test">
<a name="${case.name}"></a>
<h3 class="failures">${case.name}</h3>
<span class="code">
<pre>${case.failureMessage ?: "Test Failed"}
$detailEscaped
</pre>
</span>
$videoEl
</div>
      """.trimIndent()
    }
    """
<div class="tab" id="tab0">
<h2>Failed tests</h2>
$failedBlocks
</div>
    """.trimIndent()
  } else ""

  val testsTabId = if (hasFailed) "tab1" else "tab0"
  
  val testCasesRows = clazz.testCases.joinToString("\n") { case ->
    val resultClass = when (case.status) {
      "failed" -> "failures"
      "ignored" -> "skipped"
      else -> "success"
    }
    val resultText = when (case.status) {
      "failed" -> "failed"
      "ignored" -> "ignored"
      else -> "passed"
    }
    val videoCell = if (videoStrategy == ReportVideoStrategy.TEST) {
      val relVideo = getRelativeVideoPath(classesDir, case.videoPath)
      if (relVideo != null) "<td><a href=\"$relVideo\" target=\"_blank\">Watch Video</a></td>" else "<td>-</td>"
    } else ""
    """
<tr>
<td class="$resultClass">
<a href="#${case.name}">${case.name}</a>
</td>
<td>${case.methodName}</td>
<td>${String.format(Locale.US, "%.3f", case.duration)}s</td>
<td class="$resultClass">$resultText</td>
$videoCell
</tr>
    """.trimIndent()
  }

  val testsTab = """
<div class="tab" id="$testsTabId">
<h2>Tests</h2>
<table>
<thead>
<tr>
<th>Test</th>
<th>Method name</th>
<th>Duration</th>
<th>Result</th>
${if (videoStrategy == ReportVideoStrategy.TEST) "<th>Video</th>" else ""}
</tr>
</thead>
<tbody>
$testCasesRows
</tbody>
</table>
</div>
  """.trimIndent()

  return """
<!DOCTYPE html>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8"/>
<meta http-equiv="x-ua-compatible" content="IE=edge"/>
<title>Test results - Class ${clazz.name}</title>
<link href="../css/base-style.css" rel="stylesheet" type="text/css"/>
<link href="../css/style.css" rel="stylesheet" type="text/css"/>
<script src="../js/report.js" type="text/javascript"></script>
</head>
<body>
<div id="content">
<h1>Class ${clazz.name}</h1>
<div class="breadcrumbs">
<a href="../index.html">all</a> &gt; <a href="../packages/${clazz.packageName}.html">${clazz.packageName}</a> &gt; $simpleClassName</div>
<div id="summary">
<table>
<tr>
<td>
<div class="summaryGroup">
<table>
<tr>
<td>
<div class="infoBox" id="tests">
<div class="counter">${clazz.tests}</div>
<p>tests</p>
</div>
</td>
<td>
<div class="infoBox" id="failures">
<div class="counter">${clazz.failures}</div>
<p>failures</p>
</div>
</td>
<td>
<div class="infoBox" id="ignored">
<div class="counter">${clazz.ignored}</div>
<p>ignored</p>
</div>
</td>
<td>
<div class="infoBox" id="duration">
<div class="counter">${String.format(Locale.US, "%.3f", clazz.time)}s</div>
<p>duration</p>
</div>
</td>
</tr>
</table>
</div>
</td>
<td>
<div class="infoBox $successRateClass" id="successRate">
<div class="percent">$successRate%</div>
<p>successful</p>
</div>
</td>
</tr>
</table>
</div>
<div class="tab-container">
<ul class="tabLinks">
$tabLinks
</ul>
$failedTestsTab
$testsTab
</div>
<div id="footer">
<p>
<div>
<label class="hidden" id="label-for-line-wrapping-toggle" for="line-wrapping-toggle">Wrap lines
<input id="line-wrapping-toggle" type="checkbox" autocomplete="off"/>
</label>
</div>Generated by 
<a href="https://www.gradle.org">Gradle 9.1.0</a> at $dateString</p>
</div>
</div>
</body>
</html>
  """.trimIndent()
}

internal enum class ReportVideoStrategy { TEST, CLASS, RUN, NONE }

internal fun detectVideoStrategy(allVideoPaths: List<String>): ReportVideoStrategy {
  if (allVideoPaths.isEmpty()) return ReportVideoStrategy.NONE
  if (allVideoPaths.any { it.contains("e2e_session") }) return ReportVideoStrategy.RUN
  
  val hasTestLevel = allVideoPaths.any { path ->
    val name = File(path).nameWithoutExtension
    val parts = name.split('_')
    parts.size > 1 && (parts.last().firstOrNull()?.isLowerCase() == true || parts.last().startsWith("test"))
  }
  return if (hasTestLevel) ReportVideoStrategy.TEST else ReportVideoStrategy.CLASS
}

private fun getRelativeVideoPath(htmlFileParent: File, videoPathStr: String?): String? {
  if (videoPathStr == null) return null
  val videoFile = File(videoPathStr).absoluteFile
  if (!videoFile.exists()) return null
  
  return try {
    val htmlPath = htmlFileParent.toPath().toAbsolutePath()
    val videoPath = videoFile.toPath().toAbsolutePath()
    htmlPath.relativize(videoPath).toString().replace('\\', '/')
  } catch (e: Exception) {
    "file://${videoFile.absolutePath.replace('\\', '/')}"
  }
}


internal const val BASE_STYLE_CSS = """
body {
    margin: 0;
    padding: 0;
    font-family: sans-serif;
    font-size: 12pt;
}
body, a, a:visited {
    color: #303030;
}
#content {
    padding: 30px 50px;
}
#content h1 {
    font-size: 160%;
    margin-bottom: 10px;
}
#footer {
    margin-top: 100px;
    font-size: 80%;
    white-space: nowrap;
}
#footer, #footer a {
    color: #a0a0a0;
}
#line-wrapping-toggle {
    vertical-align: middle;
}
#label-for-line-wrapping-toggle {
    vertical-align: middle;
}
ul {
    margin-left: 0;
}
h1, h2, h3 {
    white-space: nowrap;
}
h2 {
    font-size: 120%;
}
.tab-container .tab-container {
    margin-left: 8px;
}
ul.tabLinks {
    padding: 0;
    margin-bottom: 0;
    overflow: auto;
    min-width: 800px;
    width: auto;
    border-bottom: solid 1px #aaa;
}
ul.tabLinks li {
    float: left;
    height: 100%;
    list-style: none;
    padding: 5px 10px;
    border-radius: 7px 7px 0 0;
    border: solid 1px transparent;
    border-bottom: none;
    margin-right: 6px;
    background-color: #f0f0f0;
}
ul.tabLinks li.deselected > a {
    color: #6d6d6d;
}
ul.tabLinks li:hover {
    background-color: #fafafa;
}
ul.tabLinks li.selected {
    background-color: #c5f0f5;
    border-color: #aaa;
}
ul.tabLinks a {
    font-size: 120%;
    display: block;
    outline: none;
    text-decoration: none;
    margin: 0;
    padding: 0;
}
ul.tabLinks li h2 {
    margin: 0;
    padding: 0;
}
div.tab {
}
div.selected {
    display: block;
}
div.deselected {
    display: none;
}
div.tab table {
    min-width: 350px;
    width: auto;
    border-collapse: collapse;
}
div.tab th, div.tab table {
    border-bottom: solid 1px #d0d0d0;
}
div.tab th {
    text-align: left;
    white-space: nowrap;
    padding-left: 6em;
}
div.tab th:first-child {
    padding-left: 0;
}
div.tab td {
    white-space: nowrap;
    padding-left: 6em;
    padding-top: 5px;
    padding-bottom: 5px;
}
div.tab td:first-child {
    padding-left: 0;
}
div.tab td.numeric, div.tab th.numeric {
    text-align: right;
}
span.code {
    display: inline-block;
    margin-top: 0;
    margin-bottom: 1em;
}
span.code pre {
    font-size: 11pt;
    padding: 10px;
    margin: 0;
    background-color: #f7f7f7;
    border: solid 1px #d0d0d0;
    min-width: 700px;
    width: auto;
}
span.wrapped pre {
    word-wrap: break-word;
    white-space: pre-wrap;
    word-break: break-all;
}
label.hidden {
    display: none;
}
"""

internal const val STYLE_CSS = """
#summary {
    margin-top: 30px;
    margin-bottom: 40px;
}
#summary table {
    border-collapse: collapse;
}
#summary td {
    vertical-align: top;
}
.breadcrumbs, .breadcrumbs a {
    color: #606060;
}
.infoBox {
    width: 110px;
    padding-top: 15px;
    padding-bottom: 15px;
    text-align: center;
}
.infoBox p {
    margin: 0;
}
.counter, .percent {
    font-size: 120%;
    font-weight: bold;
    margin-bottom: 8px;
}
#duration {
    width: 125px;
}
#successRate, .summaryGroup {
    border: solid 2px #d0d0d0;
    -moz-border-radius: 10px;
    border-radius: 10px;
}
#successRate {
    width: 140px;
    margin-left: 35px;
}
#successRate .percent {
    font-size: 180%;
}
.success, .success a {
    color: #008000;
}
div.success, #successRate.success {
    background-color: #bbd9bb;
    border-color: #008000;
}
.failures, .failures a {
    color: #b60808;
}
.skipped, .skipped a {
    color: #c09853;
}
div.failures, #successRate.failures {
    background-color: #ecdada;
    border-color: #b60808;
}
ul.linkList {
    padding-left: 0;
}
ul.linkList li {
    list-style: none;
    margin-bottom: 5px;
}
.code {
    position: relative;
}
.clipboard-copy-btn {
    position: absolute;
    top: 8px;
    right: 8px;
    padding: 4px 8px;
    font-size: 0.9em;
    cursor: pointer;
}
"""

internal const val REPORT_JS = """
(function (window, document) {
    "use strict";
 
    function changeElementClass(element, classValue) {
        if (element.getAttribute("className")) {
            element.setAttribute("className", classValue);
        } else {
            element.setAttribute("class", classValue);
        }
    }
 
    function getClassAttribute(element) {
        if (element.getAttribute("className")) {
            return element.getAttribute("className");
        } else {
            return element.getAttribute("class");
        }
    }
 
    function addClass(element, classValue) {
        changeElementClass(element, getClassAttribute(element) + " " + classValue);
    }
 
    function removeClass(element, classValue) {
        changeElementClass(element, getClassAttribute(element).replace(classValue, ""));
    }
 
    function getCheckBox() {
        return document.getElementById("line-wrapping-toggle");
    }
 
    function getLabelForCheckBox() {
        return document.getElementById("label-for-line-wrapping-toggle");
    }
 
    function findCodeBlocks() {
        const codeBlocks = [];
        const tabContainers = getTabContainers();
        for (let i = 0; i < tabContainers.length; i++) {
            const spans = tabContainers[i].getElementsByTagName("span");
            for (let i = 0; i < spans.length; ++i) {
                if (spans[i].className.indexOf("code") >= 0) {
                    codeBlocks.push(spans[i]);
                }
            }
        }
        return codeBlocks;
    }
 
    function forAllCodeBlocks(operation) {
        const codeBlocks = findCodeBlocks();
 
        for (let i = 0; i < codeBlocks.length; ++i) {
            operation(codeBlocks[i], "wrapped");
        }
    }
 
    function toggleLineWrapping() {
        const checkBox = getCheckBox();
 
        if (checkBox.checked) {
            forAllCodeBlocks(addClass);
        } else {
            forAllCodeBlocks(removeClass);
        }
    }
 
    function initClipboardCopyButton() {
        document.querySelectorAll(".clipboard-copy-btn").forEach((button) => {
            const copyElementId = button.getAttribute("data-copy-element-id");
            const elementWithCodeToSelect = document.getElementById(copyElementId);
 
            button.addEventListener("click", () => {
                const text = elementWithCodeToSelect.innerText.trim();
                navigator.clipboard
                    .writeText(text)
                    .then(() => {
                        button.textContent = "Copied!";
                        setTimeout(() => {
                            button.textContent = "Copy";
                        }, 1500);
                    })
                    .catch((err) => {
                        alert("Failed to copy to the clipboard: '" + err.message + "'. Check JavaScript console for more details.")
                        console.warn("Failed to copy to the clipboard", err);
                    });
            });
        });
    }
 
    function initControls() {
        if (findCodeBlocks().length > 0) {
            const checkBox = getCheckBox();
            const label = getLabelForCheckBox();
 
            checkBox.onclick = toggleLineWrapping;
            checkBox.checked = false;
 
            removeClass(label, "hidden");
         }
 
         initClipboardCopyButton()
    }
 
    class TabManager {
        baseId;
        tabs;
        titles;
        headers;
 
        constructor(baseId, tabs, titles, headers) {
            this.baseId = baseId;
            this.tabs = tabs;
            this.titles = titles;
            this.headers = headers;
        }
 
        select(i) {
            this.deselectAll();
 
            changeElementClass(this.tabs[i], "tab selected");
            changeElementClass(this.headers[i], "selected");
 
            while (this.headers[i].firstChild) {
                this.headers[i].removeChild(this.headers[i].firstChild);
            }
 
            const a = document.createElement("a");
 
            a.appendChild(document.createTextNode(this.titles[i]));
            this.headers[i].appendChild(a);
        }
 
        deselectAll() {
            for (let i = 0; i < this.tabs.length; i++) {
                changeElementClass(this.tabs[i], "tab deselected");
                changeElementClass(this.headers[i], "deselected");
 
                while (this.headers[i].firstChild) {
                    this.headers[i].removeChild(this.headers[i].firstChild);
                }
 
                const a = document.createElement("a");
 
                const id = this.baseId + "-tab" + i;
                a.setAttribute("id", id);
                a.setAttribute("href", "#tab" + i);
                a.onclick = () => {
                    this.select(i);
                    return false;
                };
                a.appendChild(document.createTextNode(this.titles[i]));
 
                this.headers[i].appendChild(a);
            }
        }
    }
 
    function getTabContainers() {
        const tabContainers = Array.from(document.getElementsByClassName("tab-container"));
 
        // Used by existing TabbedPageRenderer users, which have not adjusted to use TabsRenderer yet.
        const legacyContainer = document.getElementById("tabs");
        if (legacyContainer) {
            tabContainers.push(legacyContainer);
        }
 
        return tabContainers;
    }
 
    function initTabs() {
        let tabGroups = 0;
 
        function createTab(num, container) {
            const tabElems = findTabs(container);
            const tabManager = new TabManager("tabs" + num, tabElems, findTitles(tabElems), findHeaders(container));
            tabManager.select(0);
        }
 
        const tabContainers = getTabContainers();
 
        for (let i = 0; i < tabContainers.length; i++) {
            createTab(tabGroups, tabContainers[i]);
            tabGroups++;
        }
 
        return true;
    }
 
    // Helper functions...
    function findTabs(container) {
        return findChildElements(container, "DIV", "tab");
    }
 
    function findHeaders(container) {
        const owner = findChildElements(container, "UL", "tabLinks");
        return findChildElements(owner[0], "LI", null);
    }
 
    function findTitles(tabs) {
        const titles = [];
 
        for (let i = 0; i < tabs.length; i++) {
            const tab = tabs[i];
            const header = findChildElements(tab, "H2", null)[0];
 
            header.parentNode.removeChild(header);
 
            if (header.innerText) {
                titles.push(header.innerText);
            } else {
                titles.push(header.textContent);
            }
        }
 
        return titles;
    }
 
    function findChildElements(container, name, targetClass) {
        const elements = [];
        const children = container.childNodes;
 
        for (let i = 0; i < children.length; i++) {
            const child = children.item(i);
 
            if (child.nodeType === 1 && child.nodeName === name) {
                if (targetClass && child.className.indexOf(targetClass) < 0) {
                    continue;
                }
 
                elements.push(child);
            }
        }
 
        return elements;
    }
 
    // Entry point.
    window.onload = function() {
        initTabs();
        initControls();
    };
} (window, window.document));
"""
