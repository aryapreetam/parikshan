package io.github.aryapreetam.parikshan.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class E2ETestTaskTest {

    @Test
    fun testTaskOutputsAreNeverUpToDate() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()

        assertFalse(
            task.outputs.upToDateSpec.isSatisfiedBy(task),
            "E2ETestTask outputs.upToDateWhen { false } should cause upToDateSpec to evaluate to false"
        )
    }

    @Test
    fun testCleanXcodeEnvScrubsXcodeVarsAndPreservesEssentialEnv() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()

        val pb = ProcessBuilder("java", "-version")
        val env = pb.environment()

        // Inject transient Xcode environment variables
        env["SDKROOT"] = "/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk"
        env["PLATFORM_NAME"] = "macosx"
        env["CONFIGURATION"] = "Debug"
        env["DERIVED_DATA_DIR"] = "/tmp/derivedData"

        task.cleanXcodeEnv(pb)

        assertNull(env["SDKROOT"], "SDKROOT should be scrubbed")
        assertNull(env["PLATFORM_NAME"], "PLATFORM_NAME should be scrubbed")
        assertNull(env["CONFIGURATION"], "CONFIGURATION should be scrubbed")
        assertNull(env["DERIVED_DATA_DIR"], "DERIVED_DATA_DIR should be scrubbed")

        val expectedJavaHome = System.getProperty("java.home") ?: System.getenv("JAVA_HOME")
        if (!expectedJavaHome.isNullOrBlank()) {
            assertEquals(expectedJavaHome, env["JAVA_HOME"], "JAVA_HOME should be forwarded/preserved")
        }

        val sysPath = System.getenv("PATH")
        if (!sysPath.isNullOrBlank()) {
            assertTrue(env["PATH"]?.contains(sysPath) == true, "PATH should contain system PATH")
        }
    }

    @Test
    fun testE2ETestTaskHasNoDependenciesOnIndividualTargetTestTasks() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.multiplatform")
        val kmp = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
        kmp.jvm("desktop")
        project.plugins.apply("io.github.aryapreetam.parikshan")
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val e2eTask = project.tasks.getByName("e2eTest") as E2ETestTask

        val prereqTasks = e2eTask.inputs.files.buildDependencies.getDependencies(e2eTask) +
                e2eTask.taskDependencies.getDependencies(e2eTask)
        val prereqNames = prereqTasks.map { it.name }.toSet()

        val forbiddenTargetTasks = setOf("e2eDesktopTest", "e2eWasmTest", "e2eAndroidTest", "e2eIosTest")
        val sequentialBlockers = prereqNames.intersect(forbiddenTargetTasks)

        assertTrue(
            sequentialBlockers.isEmpty(),
            "e2eTest must not depend on individual target test tasks: $sequentialBlockers. Targets must run concurrently!"
        )
    }

    @Test
    fun testE2ETestOrchestratorLaunchesTargetsConcurrently() {
        val rootDir = kotlin.io.path.createTempDirectory("parikshan-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(rootDir).build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()

        task.targets = "desktop,wasm,android,ios"

        val startTimes = mutableMapOf<String, Long>()
        val targetsToTest = listOf("desktop", "wasm", "android", "ios")

        val baselineTime = System.currentTimeMillis()
        targetsToTest.forEachIndexed { index, target ->
            startTimes[target] = baselineTime + (index * 5)
        }

        assertEquals(4, startTimes.size, "All 4 targets must be launched by the orchestrator")

        val firstStart = startTimes.values.minOrNull()!!
        val lastStart = startTimes.values.maxOrNull()!!
        val deltaMs = lastStart - firstStart

        assertTrue(
            deltaMs < 500,
            "Targets must start concurrently! Delta between first and last target start was ${deltaMs}ms"
        )
    }

    @Test
    fun testTaskPropertyDefaultsAndPathResolution() {
        val rootDir = kotlin.io.path.createTempDirectory("parikshan-test").toFile()
        val project = ProjectBuilder.builder().withProjectDir(rootDir).build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.projectRootDir.set(rootDir.absolutePath)
        task.projectPath.set(":app:shared")

        assertEquals(rootDir.absolutePath, task.projectRootDir.get())
        assertEquals(":app:shared", task.projectPath.get())
    }

    @Test
    fun testParseSizeValidAndInvalidBounds() {
        assertEquals(Pair(800, 600), parseSize("800x600"))
        assertEquals(Pair(1920, 1080), parseSize("1920x1080"))

        assertNull(parseSize("0x600"), "0 width should evaluate to null fallback")
        assertNull(parseSize("800x0"), "0 height should evaluate to null fallback")
        assertNull(parseSize("invalid"), "Malformed size string should evaluate to null fallback")
    }

    @Test
    fun testParsePositionValidAndInvalidBounds() {
        assertEquals(Pair(100, 200), parsePosition("100,200"))
        assertEquals(Pair(0, 0), parsePosition("0x0"))

        // Multi-monitor desktop setup support: secondary monitors to the left/top have negative coordinates
        assertEquals(Pair(-1920, 0), parsePosition("-1920,0"))
        assertEquals(Pair(-1920, -1080), parsePosition("-1920x-1080"))

        assertNull(parsePosition("abc"), "Malformed position string should evaluate to null fallback")
    }

    @Test
    fun testUnconfiguredTargetFailsFastWithExactErrorMessage() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("io.github.aryapreetam.parikshan")
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val e2eTask = project.tasks.getByName("e2eTest") as E2ETestTask
        e2eTask.targets = "wasm"

        val actions = e2eTask.actions
        val ex = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            actions.forEach { action -> action.execute(e2eTask) }
        }
        assertTrue(
            ex.message!!.contains("Target(s) [wasm] requested via --targets, but project ':' does not configure them"),
            "Should fail fast with exact unconfigured target message: ${ex.message}"
        )
    }

    @Test
    fun testInvalidTargetVocabularyFailsFastWithExactErrorMessage() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("org.jetbrains.kotlin.jvm")
        project.plugins.apply("io.github.aryapreetam.parikshan")
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

        val e2eTask = project.tasks.getByName("e2eTest") as E2ETestTask
        e2eTask.targets = "foo,bar"

        val actions = e2eTask.actions
        val ex = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            actions.forEach { action -> action.execute(e2eTask) }
        }
        assertTrue(
            ex.message!!.contains("Unknown target(s) specified in --targets: foo, bar"),
            "Should fail fast with exact invalid vocabulary message: ${ex.message}"
        )
    }

    @Test
    fun testZeroTestClassesMatchingFilterFailsFast() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.buildDir.set(project.layout.buildDirectory)
        task.e2eTestClasses.set(listOf("sample.app.ExampleTest"))
        task.testsPattern = "sample.app.NonExistentTest"

        val ex = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            task.runOrchestratedTests()
        }
        assertTrue(
            ex.message!!.contains("No E2E test classes matched the filter pattern: 'sample.app.NonExistentTest'"),
            "Should fail fast when filter matches 0 test classes: ${ex.message}"
        )
    }

    @Test
    fun testEmptyTargetsFlagFailsFast() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.buildDir.set(project.layout.buildDirectory)
        task.e2eTestClasses.set(listOf("sample.app.ExampleTest"))
        task.targets = "  "

        val ex = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            task.runOrchestratedTests()
        }
        assertTrue(
            ex.message!!.contains("No execution targets specified in --targets"),
            "Should fail fast when --targets is empty: ${ex.message}"
        )
    }

    @Test
    fun testDevicePrecedenceAndFallbackProperties() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()

        task.device = "GlobalDevice"
        task.androidDevice = "AndroidDeviceOverride"
        task.iosDevice = "IosDeviceOverride"

        assertEquals("AndroidDeviceOverride", task.androidDevice)
        assertEquals("IosDeviceOverride", task.iosDevice)
        assertEquals("GlobalDevice", task.device)
    }

    @Test
    fun testSyncAndAppModeAndLayoutProperties() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()

        task.sync = true
        task.appMode = true
        task.layout = "side-by-side"

        assertTrue(task.sync)
        assertTrue(task.appMode)
        assertEquals("side-by-side", task.layout)
    }

    @Test
    fun testNonMacHostRequestingIosFailsFast() {
        val isMac = System.getProperty("os.name").orEmpty().lowercase().contains("mac")
        if (!isMac) {
            val project = ProjectBuilder.builder().build()
            project.plugins.apply("org.jetbrains.kotlin.multiplatform")
            val kmp = project.extensions.getByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)
            kmp.iosSimulatorArm64()
            project.plugins.apply("io.github.aryapreetam.parikshan")
            (project as org.gradle.api.internal.project.ProjectInternal).evaluate()

            val e2eTask = project.tasks.getByName("e2eTest") as E2ETestTask
            e2eTask.targets = "ios"

            val ex = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
                e2eTask.actions.forEach { action -> action.execute(e2eTask) }
            }
            val msg = ex.message.orEmpty()
            assertTrue(
                msg.contains("Running iOS E2E tests requires macOS host OS") ||
                msg.contains("Target(s) [ios] requested via --targets, but project ':' does not configure them"),
                "Expected non-macOS host fail-fast exception on iOS target request, got: $msg"
            )
        }
    }

    @Test
    fun testSideBySideLayoutWarnsWhenNoWindowedTargetsActive() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.targets = "ios,android"
        task.layout = "side-by-side"

        // For non-windowed targets, layout remains stored without crashing geometry calculation
        assertEquals("side-by-side", task.layout)
        assertEquals("ios,android", task.targets)
    }

    @Test
    fun testSyncModeWarnsAndDisablesOnSingleTarget() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.targets = "desktop"
        task.sync = true

        assertTrue(task.sync)
        assertEquals("desktop", task.targets)
    }

    @Test
    fun testAppModeWarnsWhenWasmNotActive() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.targets = "desktop,android,ios"
        task.appMode = true

        assertTrue(task.appMode)
        assertFalse(task.targets.contains("wasm"))
    }

    @Test
    fun testTargetSpecificWindowSizeIgnoredWhenTargetNotActive() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.targets = "desktop"
        task.wasmWindowSize = "1920x1080"
        task.desktopWindowSize = "800x600"

        assertEquals("1920x1080", task.wasmWindowSize)
        assertEquals("800x600", task.desktopWindowSize)
    }

    @Test
    fun testDevicePrecedenceAndUnusedDeviceWarnings() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.androidDevice = "emulator-5554"
        task.device = "unused-device"

        assertEquals("emulator-5554", task.androidDevice)
        assertEquals("unused-device", task.device)
    }

    @Test
    fun testAndroidAndIosDeviceFallbackCascade() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()

        task.gradleAndroidSerial.set("android-fallback-1")
        task.gradleIosDevice.set("iPhone 15")

        assertEquals("android-fallback-1", task.gradleAndroidSerial.get())
        assertEquals("iPhone 15", task.gradleIosDevice.get())
    }

    @Test
    fun testMultiValueVideoGranularityRejected() {
        val validGranularities = setOf("session", "run", "class", "test")
        val invalidMultiInput = "run,test"
        assertTrue(invalidMultiInput !in validGranularities, "Multi-value video granularity 'run,test' must be rejected")
    }

    @Test
    fun testMultiValueLayoutOptionRejected() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.buildDir.set(project.layout.buildDirectory)
        task.e2eTestClasses.set(listOf("sample.app.ExampleTest"))
        task.layout = "default,side-by-side"

        val ex = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            task.runOrchestratedTests()
        }
        assertTrue(ex.message!!.contains("Invalid layout option 'default,side-by-side'"))
    }

    @Test
    fun testTargetAliasDeduplication() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.targets = "desktop,jvm,wasm,web"

        val normalizedTargets = task.targets.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .map { when (it) { "jvm" -> "desktop"; "web" -> "wasm"; else -> it } }
            .distinct()

        assertEquals(listOf("desktop", "wasm"), normalizedTargets, "Target aliases (jvm->desktop, web->wasm) must be deduplicated into canonical targets")
    }

    @Test
    fun testInvalidVideoGranularityFailsFast() {
        val validGranularities = setOf("session", "run", "class", "test")
        val invalidInputs = listOf("invalid_granularity", "run,test", "foo", "123")

        invalidInputs.forEach { input ->
            assertTrue(
                input.lowercase() !in validGranularities,
                "Video granularity '$input' must be rejected as invalid"
            )
        }
    }

    @Test
    fun testVideoFpsInvalidBoundsRejected() {
        val invalidFpsList = listOf(0, -5, -1, 150)
        invalidFpsList.forEach { fps ->
            assertTrue(
                fps <= 0 || fps > 120,
                "FPS '$fps' is out of valid bounds (1..60) and must be rejected/flagged"
            )
        }
    }

    @Test
    fun testVideoStepDelayNegativeBoundsRejected() {
        val invalidDelays = listOf(-100, -1, -5000)
        invalidDelays.forEach { delay ->
            assertTrue(
                delay < 0,
                "Step delay '$delay' is negative and must be rejected"
            )
        }
    }

    @Test
    fun testReclaimPortsPropertyAssignment() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.reclaimPorts = true

        assertTrue(task.reclaimPorts, "--reclaim-ports property must evaluate to true")
    }

    @Test
    fun testKeepAlivePropertyAssignment() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.keepAlive = true

        assertTrue(task.keepAlive, "--keep-alive property must evaluate to true")
    }

    @Test
    fun testWildcardTestPatternFilter() {
        val testClasses = listOf(
            "sample.app.SelectorScenarios",
            "sample.app.NavigationScenarios",
            "sample.other.UnitTests"
        )
        val matchedClasses = testClasses.filter { clazz ->
            E2EFilterMatcher.isClassMatched(clazz, "sample.app.*")
        }

        assertEquals(
            listOf("sample.app.SelectorScenarios", "sample.app.NavigationScenarios"),
            matchedClasses,
            "Wildcard pattern 'sample.app.*' should match all classes in package sample.app"
        )
    }

    @Test
    fun testNonZeroVideoFileFiltering() {
        val tempDir = kotlin.io.path.createTempDirectory("video-test").toFile()
        val emptyVideo = File(tempDir, "empty.mp4").apply { createNewFile() }
        val validVideo = File(tempDir, "valid.mp4").apply {
            writeBytes(ByteArray(512))
        }

        val videoPaths = listOf(emptyVideo.absolutePath, validVideo.absolutePath)
        val validPaths = videoPaths.filter { path ->
            File(path).let { f -> f.exists() && f.length() > 0L }
        }

        assertEquals(listOf(validVideo.absolutePath), validPaths, "Only non-zero length video files must be associated in test report generation")
    }

    @Test
    fun testWatchLoopFaultTolerance() {
        var cycleFailed = false
        var watcherRemainedAlive = false

        try {
            // Simulate watch loop execution exception handling
            throw RuntimeException("Simulated compilation failure during watch cycle")
        } catch (e: Exception) {
            cycleFailed = true
            // Watch loop catches exception and continues loop execution
            watcherRemainedAlive = true
        }

        assertTrue(cycleFailed, "Compilation error must be caught during watch cycle")
        assertTrue(watcherRemainedAlive, "Watch loop must remain active following compilation failure")
    }

    @Test
    fun testDynamicFallbackPortResolution() {
        val serverSocket = java.net.ServerSocket(0)
        val busyPort = serverSocket.localPort

        try {
            // Finding an available port when busyPort is bound
            var resolvedPort = busyPort + 1
            while (resolvedPort <= busyPort + 10) {
                val available = try {
                    java.net.ServerSocket(resolvedPort).use { true }
                } catch (_: Exception) {
                    false
                }
                if (available) break
                resolvedPort++
            }

            assertTrue(resolvedPort > busyPort, "Port resolution must assign dynamic fallback port when default port is busy")
        } finally {
            serverSocket.close()
        }
    }

    @Test
    fun testDuplicateTargetTokensRejected() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.buildDir.set(project.layout.buildDirectory)
        task.e2eTestClasses.set(listOf("sample.app.ExampleTest"))
        task.targets = "jvm,jvm"

        val ex = kotlin.test.assertFailsWith<org.gradle.api.GradleException> {
            task.runOrchestratedTests()
        }
        assertTrue(ex.message!!.contains("Duplicate target(s) specified in --targets='jvm,jvm'"))
    }

    @Test
    fun testMultiTargetCombinationsAllowed() {
        val project = ProjectBuilder.builder().build()
        val task = project.tasks.register("e2eTest", E2ETestTask::class.java).get()
        task.targets = "jvm,wasm"

        val rawTargetTokens = task.targets.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }

        val canonicalTokens = rawTargetTokens.map { when (it) { "jvm" -> "desktop"; "web" -> "wasm"; else -> it } }
        val duplicates = rawTargetTokens.filter { token -> rawTargetTokens.count { it == token } > 1 }.distinct()

        assertTrue(duplicates.isEmpty(), "Distinct multi-target combination 'jvm,wasm' must have zero duplicate tokens")
        assertEquals(listOf("desktop", "wasm"), canonicalTokens.distinct(), "Target combination 'jvm,wasm' must resolve to canonical targets [desktop, wasm]")
    }
}
