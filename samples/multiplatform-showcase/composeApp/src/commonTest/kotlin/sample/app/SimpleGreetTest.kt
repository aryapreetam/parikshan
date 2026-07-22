package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class SimpleGreetTest {
  @Test
  fun testSimpleGreeting() = e2eTest {
    if (isDemo) {
      // Enter name
      input("name_input", "परिक्षण")
      
      // Click Greet button
      click("greet_button")
      
      // Check if greeting is displayed
      assertVisible("Hello, परिक्षण!")
    }
  }
}
