package io.github.aryapreetam.parikshan.gradle

import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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

        val sysJavaHome = System.getenv("JAVA_HOME")
        if (!sysJavaHome.isNullOrBlank()) {
            assertEquals(sysJavaHome, env["JAVA_HOME"], "JAVA_HOME should be forwarded/preserved")
        }

        val sysPath = System.getenv("PATH")
        if (!sysPath.isNullOrBlank()) {
            assertEquals(sysPath, env["PATH"], "PATH should be forwarded/preserved")
        }
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
}
