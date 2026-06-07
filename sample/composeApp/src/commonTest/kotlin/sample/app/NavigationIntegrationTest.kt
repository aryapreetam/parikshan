package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class NavigationIntegrationTest {

  @Test
  fun testNestedNavigationAndStatePreservation() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_navigation_playground")
    assertVisible("navigation_playground_screen")

    // Input text in Screen A
    input("input_screen_a", "Saved State A")
    
    // Navigate to Screen B
    click("nav_to_b_button")
    assertVisible("screen_b_title")

    // Input text in Screen B
    input("input_screen_b", "Saved State B")

    // Navigate to Screen C
    click("nav_to_c_button")
    assertVisible("screen_c_title")

    // Go Back to B & Verify text remains
    click("nav_back_button")
    assertVisible("screen_b_title")
    assertText("input_screen_b", "Saved State B")

    // Go Back to A & Verify text remains
    click("nav_back_button")
    assertVisible("screen_a_title")
    assertText("input_screen_a", "Saved State A")
  }

  @Test
  fun testBackNavigationInterceptionFlow() = e2eTest {
    relaunchApp()
    openAppNavigation(); click("nav_navigation_playground")
    assertVisible("navigation_playground_screen")

    // Go to Screen B
    click("nav_to_b_button")
    assertVisible("screen_b_title")

    // Enable Interceptor Switch
    click("back_intercept_switch")

    // Click Back, assert alert dialog blocks it
    click("nav_back_button")
    assertVisible("back_intercept_dialog")

    // Cancel Back -> Should remain on B
    click("cancel_back_btn")
    assertVisible("screen_b_title")

    // Click Back again and Confirm
    click("nav_back_button")
    assertVisible("back_intercept_dialog")
    click("confirm_back_btn")

    // Should successfully pop to Screen A
    assertVisible("screen_a_title")
  }
}
