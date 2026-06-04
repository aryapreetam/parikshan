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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private suspend fun E2ETestScope.scrollUntilVisible(
  containerSelector: Selector,
  targetSelector: Selector,
  maxScrolls: Int = 10
) {
  repeat(maxScrolls + 1) { attempt ->
    if (hasVisibleNode(targetSelector)) {
      return
    }
    if (attempt == maxScrolls) {
      throw AssertionError(
        "Could not make '${targetSelector.raw}' visible after $maxScrolls scroll actions"
      )
    }
    scroll(containerSelector, ScrollDirection.Down)
    kotlinx.coroutines.delay(200)
  }
}

class SelectorParityTest {

  @Test
  fun testExistentialAssertsSucceedWithDuplicates() = e2eTest {
    relaunchApp()
    click("nav_input_form")
    assertVisible("input_form_screen")
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Duplicate Action"))
    waitFor(Selector.Auto("Duplicate Action"))
    assertVisible(Selector.Auto("Duplicate Action"))
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Tag("duplicate_input_2"))
    waitFor(Selector.Auto("Duplicate Input"))
    assertVisible(Selector.Auto("Duplicate Input"))
  }

  private suspend fun E2ETestScope.countVisibleMatches(selector: Selector): Int {
    return try {
      val resolved = selector.resolveNode(nodes = getTree(), requireVisible = true)
      resolved.allMatches.size
    } catch (e: Exception) {
      0
    }
  }

  @Test
  fun testActionsFailOnAmbiguityWithoutIndex() = e2eTest {
    relaunchApp()
    click("nav_input_form")
    assertVisible("input_form_screen")

    // Just scroll a bit to ensure both are in viewport
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Duplicate Action"))
    
    val clickError = kotlin.runCatching {
      click("Duplicate Action")
    }.exceptionOrNull() as? AssertionError
      ?: throw AssertionError("Expected click to fail due to ambiguity.")

    assertContains(clickError.message.orEmpty(), "multiple visible text nodes")

    val inputError = kotlin.runCatching {
      input("Duplicate Input", "some text")
    }.exceptionOrNull() as? AssertionError
      ?: throw AssertionError("Expected input to fail due to ambiguity.")

    assertContains(inputError.message.orEmpty(), "multiple visible text nodes")
  }

  @Test
  fun testActionsSucceedWithExplicitIndices() = e2eTest {
    relaunchApp()
    click("nav_input_form")
    assertVisible("input_form_screen")

    // Just scroll a bit to ensure both are in viewport
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Duplicate Action"))

    // Click with explicit indices
    click(Selector.Auto("Duplicate Action").first())
    click(Selector.Auto("Duplicate Action").atIndex(1))
    click(Selector.Auto("Duplicate Action").last())

    // First input
    input(Selector.Auto("Duplicate Input").first(), "First Input")
    assertText("duplicate_input_1", "First Input")

    // Last input
    input(Selector.Auto("Duplicate Input").last(), "Last Input")
    assertText("duplicate_input_2", "Last Input")
  }

  @Test
  fun testLongFormSubmission() = e2eTest {
    relaunchApp()
    click("nav_input_form")
    assertVisible("input_form_screen")
    
    // Fill out the long form organically scrolling as needed
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Email Address"))
    input("Email Address", "test@example.com")
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Phone Number"))
    input("Phone Number", "1234567890")
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Shipping Address"))
    input("Shipping Address", "123 Main St\nApt 4")
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Password"))
    input("Password", "secretpassword")
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Confirm Password"))
    input("Confirm Password", "secretpassword")
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("I agree to the terms and conditions"))
    click("I agree to the terms and conditions") // Toggles checkbox via row click or label
    
    scrollUntilVisible(Selector.Tag("input_form_screen"), Selector.Auto("Final Submit"))
    click("Final Submit")
    
    // Check for success message on Snackbar
    waitFor(Selector.Auto("Final Form Submitted Successfully!"))
    assertVisible("Final Form Submitted Successfully!")
  }
}