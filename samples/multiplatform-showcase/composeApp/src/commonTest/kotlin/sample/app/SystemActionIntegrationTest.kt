package sample.app

import io.github.aryapreetam.parikshan.isAndroid
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class SystemActionIntegrationTest {

  @Test
  fun testSystemBackAndHomeActions() = e2eTest {
    relaunchApp()
    
    // System-level gestures are only verified on Android target
    if (isAndroid()) {
      navigateToSection("nav_navigation_playground")
      assertVisible("navigation_playground_screen")

      // Navigate to Screen B
      click("nav_to_b_button")
      assertVisible("screen_b_title")

      // Press system back - should go back to Screen A
      pressBack()
      assertVisible("screen_a_title")

      // Press system back again - should pop out of Navigation Playground to TaskList home screen
      pressBack()
      assertVisible("task_list_screen")

      // Navigate back to playground to test home gesture
      navigateToSection("nav_navigation_playground")
      assertVisible("navigation_playground_screen")

      // Press system home - should navigate to Android home screen
      pressHome()

      // Restore foreground state
      relaunchApp()
      assertVisible("task_list_screen")
    }
  }
}
