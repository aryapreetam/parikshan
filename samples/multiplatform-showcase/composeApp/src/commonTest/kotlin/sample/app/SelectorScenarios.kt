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
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testAmbiguousTextSelectorFailsClearly() = e2eTest {
    navigateToSection("nav_selector_parity_playground")
    assertVisible("selector_parity_playground_screen")

    // Scroll to the second button to ensure BOTH "Duplicate Action" buttons are physically visible
    scrollUntilVisible(Selector.Tag("selector_parity_playground_screen"), Selector.Tag("duplicate_action_secondary"))

    assertFailure("matched multiple visible nodes") {
      click("Duplicate Action")
    }
  }
}

private suspend fun E2ETestScope.openInputForm() {
  navigateToSection("nav_form_playground")
  assertVisible("form_playground_screen")
}

