package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.isWasm
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.resolveNode
import kotlinx.coroutines.delay
import kotlin.test.Test

class AccessibilityIntegrationTest {

  @Test
  fun testAccessibilityLabelContentDescriptionMatching() = e2eTest {
    relaunchApp()
    navigateToSection("nav_accessibility_playground")
    assertVisible("accessibility_playground_screen")

    // Target by accessibility label (content description)
    click(Selector.Auto("Settings Control Button"))
    
    assertVisible("Clicked Settings Option")
  }

  @Test
  fun testIndexBasedDuplicateResolution() = e2eTest {
    relaunchApp()
    navigateToSection("nav_accessibility_playground")
    assertVisible("accessibility_playground_screen")
    
    // Scroll to the bottom area where the duplicate buttons are
    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("duplicate_action_item_0")
    )

    // On Native targets, we verify that ambiguous clicks fail.
    if (!isWasm()) {
        assertFailure("matched multiple visible nodes") {
          click("Duplicate Action Item")
        }
    }

    // Resolve via unique tags (Deterministic across all platforms)
    click("duplicate_action_item_0")
    
    // Scroll back to the top to see the status message (it might be pushed out of viewport on small screens)
    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("a11y_result_message"),
        direction = ScrollDirection.Up
    )
    assertText("a11y_result_message", "Clicked Index 0")

    // Scroll back down for the next button
    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("duplicate_action_item_1")
    )
    click("duplicate_action_item_1")

    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("a11y_result_message"),
        direction = ScrollDirection.Up
    )
    assertText("a11y_result_message", "Clicked Index 1")

    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("duplicate_action_item_2")
    )
    click("duplicate_action_item_2")
    
    scrollUntilVisible(
        containerSelector = Selector.Tag("accessibility_playground_screen"),
        targetSelector = Selector.Tag("a11y_result_message"),
        direction = ScrollDirection.Up
    )
    assertText("a11y_result_message", "Clicked Index 2")
  }

  @Test
  fun testHiddenElementVisibilityStrictness() = e2eTest {
    relaunchApp()
    navigateToSection("nav_accessibility_playground")
    assertVisible("accessibility_playground_screen")

    // The target "invisible_click_target" is size 0.dp and alpha 0f
    assertNotVisible("invisible_click_target")
    
    // Attempting to click it should fail since it's invisible
    assertFailure("invisible_click_target") {
      click("invisible_click_target")
    }
  }
}
