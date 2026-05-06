package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class SubtextScenarios {

    @Test
    fun testSubtextMatching() = e2eTest {
        // Navigate to Subtext Demo
        click("nav_subtext_demo")
        
        // Assert that the full text screen is visible
        assertVisible("subtext_demo_screen")
        
        // The screen contains "This is a sample text for testing purpose"
        // We check for a subtext "This is a sample text"
        assertVisible("This is a sample text")
        
        screenshot(screenshotPath("subtext-matching"))
    }
}
