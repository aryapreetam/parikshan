package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.isWasm
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.resolveNode
import kotlinx.coroutines.delay
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import kotlin.test.Test

class AccessibilityIntegrationTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    navigateToSection("nav_accessibility_playground")
    assertVisible("accessibility_playground_screen")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testAccessibilityLabelContentDescriptionMatching() = e2eTest {
    // Target by accessibility label (content description)
    click("a11y_icon_button")
    
    assertText("a11y_result_message", "Clicked Settings Option")
  }

  @Test
  fun testIndexBasedDuplicateResolution() = e2eTest {
    // Scroll to the bottom area where the duplicate buttons are
    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("duplicate_action_item_0")
    )

    // On Native targets, we verify that ambiguous clicks fail fast
    if (!isWasm()) {
        assertFailure("matched multiple visible nodes") {
          click("Duplicate Action Item")
        }
    }

    // Resolve via unique tags (Deterministic across all platforms)
    click("duplicate_action_item_0")
    assertText("a11y_result_message", "Clicked Index 0")

    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("duplicate_action_item_1")
    )
    click("duplicate_action_item_1")
    assertText("a11y_result_message", "Clicked Index 1")

    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("duplicate_action_item_2")
    )
    click("duplicate_action_item_2")
    assertText("a11y_result_message", "Clicked Index 2")
  }

  @Test
  fun testSubtextMatching() = e2eTest {
    
    scrollUntilVisible(
      containerSelector = Selector.Tag("accessibility_playground_screen"),
      targetSelector = Selector.Tag("subtext_sample_target")
    )
    // The screen contains "This is a sample text for testing purpose"
    // We check for a subtext "This is a sample text"
    assertVisible("This is a sample text")
    
    screenshot(screenshotPath("subtext-matching"))
  }
}
