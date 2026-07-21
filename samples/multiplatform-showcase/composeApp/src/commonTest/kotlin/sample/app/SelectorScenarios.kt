package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.resolveNode
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import kotlin.test.Test

class SelectorScenarios : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    openInputForm()
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testInputForm() = e2eTest {
    input("input_name_field", "New Task")
    assertText("input_name_preview", "New Task")
    click("form_submit_button")
    assertVisible("form_success_message")
  }

  @Test
  fun testUniqueTextSelector() = e2eTest {
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Unique Text Action"))
    click("Unique Text Action")
    assertVisible("Unique text clicked")
  }

  @Test
  fun testTagPrecedenceOverText() = e2eTest {
    click("Submit")
    assertText("selector_result_message", "Tag selector won")
  }

  @Test
  fun testAmbiguousTextSelectorFailsClearly() = e2eTest {
    // Scroll to the second button to ensure BOTH "Duplicate Action" buttons are physically visible
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Tag("duplicate_action_secondary"))

    assertFailure("multiple visible nodes") {
      click("Duplicate Action")
    }
  }
}

private suspend fun E2ETestScope.openInputForm() {
  openAppNavigation(); click("nav_input_form")
  assertVisible("input_form_screen")
}

