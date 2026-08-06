package io.github.aryapreetam.parikshan.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ParikshanPluginInstrumentationTest {

    @Test
    fun pluginAppliesAndRegistersE2ETask() {
        val project = ProjectBuilder.builder().build()
        project.plugins.apply("io.github.aryapreetam.parikshan")
        (project as org.gradle.api.internal.project.ProjectInternal).evaluate()
        val e2eTask = project.tasks.findByName("e2eTest")
        assertNotNull(e2eTask, "e2eTest task should be registered by the plugin")
        // Sanity check: the task type name should reference E2ETestTask
        assertTrue(e2eTask.javaClass.simpleName.contains("E2ETestTask") || e2eTask is E2ETestTask)
    }
}

