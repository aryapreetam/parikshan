package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.atIndex
import io.github.aryapreetam.parikshan.protocol.first
import io.github.aryapreetam.parikshan.protocol.last
import io.github.aryapreetam.parikshan.resolveNode
import io.github.aryapreetam.parikshan.E2ETestLifecycle
import kotlin.test.Test

class SelectorParityTest : E2ETestLifecycle {

  override suspend fun E2ETestScope.beforeEach() {
    navigateToSection("nav_selector_parity_playground")
    assertVisible("selector_parity_playground_screen")
  }

  override suspend fun E2ETestScope.afterEach() {
    navigateToSection("nav_home_screen")
  }

  @Test
  fun testExistentialAssertsSucceedWithDuplicates() = e2eTest {
    // Existence checks should succeed even if multiple nodes match
    scrollUntilVisible(Selector.Tag("selector_parity_playground_screen"), Selector.Auto("Duplicate Action"))
    assertVisible(Selector.Auto("Duplicate Action"))
    
    scrollUntilVisible(Selector.Tag("selector_parity_playground_screen"), Selector.Tag("duplicate_input_2"))
    assertVisible(Selector.Auto("Duplicate Input"))
  }

  @Test
  fun testActionsFailOnAmbiguityWithoutIndex() = e2eTest {
    scrollUntilVisible(Selector.Tag("selector_parity_playground_screen"), Selector.Auto("Duplicate Action"))

    // Clicking should fail because text is ambiguous
    assertFailure("matched multiple visible nodes") {
      click("Duplicate Action")
    }

    assertFailure("matched multiple visible nodes") {
      input("Duplicate Input", "some text")
    }
  }

  @Test
  fun testActionsSucceedWithExplicitIndices() = e2eTest {
    // Just scroll a bit to ensure both are in viewport
    scrollUntilVisible(Selector.Tag("selector_parity_playground_screen"), Selector.Auto("Duplicate Action"))

    // Click with explicit indices
    click(Selector.Auto("Duplicate Action").first())
    click(Selector.Auto("Duplicate Action").atIndex(1))
    click(Selector.Auto("Duplicate Action").last())

    // Input with explicit indices
    input(Selector.Auto("Duplicate Input").first(), "First Input")
    assertText("duplicate_input_1", "First Input")

    input(Selector.Auto("Duplicate Input").last(), "Last Input")
    assertText("duplicate_input_2", "Last Input")
  }
}
