package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.E2ETestLifecycle

class SubtextScenarios : E2ETestLifecycle {

    override suspend fun E2ETestScope.beforeEach() {
        navigateToSection("nav_subtext_demo")
        assertVisible("subtext_demo_screen")
    }

    override suspend fun E2ETestScope.afterEach() {
        navigateToSection("nav_home_screen")
    }

    @Test
    fun testSubtextMatching() = e2eTest {
        // The screen contains "This is a sample text for testing purpose"
        // We check for a subtext "This is a sample text"
        assertVisible("This is a sample text")
        
        screenshot(screenshotPath("subtext-matching"))
    }
}
