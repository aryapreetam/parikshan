package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.atIndex
import io.github.aryapreetam.parikshan.protocol.first
import io.github.aryapreetam.parikshan.protocol.last
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class AccessibilityIntegrationTest {

  @Test
  fun testAccessibilityLabelContentDescriptionMatching() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_accessibility_playground")
    assertVisible("accessibility_playground_screen")

    // The button has no text, only a content description (accessibility label)
    // We target it via Selector.Auto("Settings Control Button")
    click(Selector.Auto("Settings Control Button"))
    
    // Assert click successfully registered
    assertText("a11y_result_message", "Clicked Settings Option")
  }

  @Test
  fun testIndexBasedDuplicateResolution() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_accessibility_playground")
    assertVisible("accessibility_playground_screen")
    scroll("accessibility_playground_screen", ScrollDirection.Down)

    // Assert that attempting to click "Duplicate Action Item" without index fails due to ambiguity
    val ambiguityError = assertFailsWith<AssertionError> {
      click("Duplicate Action Item")
    }
    
    assertContains(ambiguityError.message.orEmpty(), "matched multiple visible text nodes")

    // Resolve via explicit indices
    click(Selector.Tag("duplicate_action_item").atIndex(0))
    assertText("a11y_result_message", "Clicked Index 0")

    click(Selector.Tag("duplicate_action_item").atIndex(1))
    assertText("a11y_result_message", "Clicked Index 1")

    click(Selector.Tag("duplicate_action_item").atIndex(2))
    assertText("a11y_result_message", "Clicked Index 2")


    click(Selector.Tag("duplicate_action_item").last())
    assertText("a11y_result_message", "Clicked Index 2")
  }

  @Test
  fun testHiddenElementVisibilityStrictness() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_accessibility_playground")
    assertVisible("accessibility_playground_screen")

    // The target "invisible_click_target" is size 0.dp and alpha 0f
    // Assert that it is not visible
    assertNotVisible("invisible_click_target")

    // Attempting to click it should fail since it's invisible
    val clickError = assertFailsWith<AssertionError> {
      click("invisible_click_target")
    }
    
    assertContains(clickError.message.orEmpty(), "invisible_click_target")
  }
}
