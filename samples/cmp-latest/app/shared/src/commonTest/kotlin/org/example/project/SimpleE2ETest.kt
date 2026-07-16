package org.example.project

import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.test.Test

class SimpleE2ETest {
  @Test
  fun test() = e2eTest {
    assertNotVisible("Compose: Hello")
    click("Click me!")
    assertVisible("Compose: Hello")
  }

  @Test
  fun testDropdownAndAlert() = e2eTest {
    click("Select Color")
    scrollUntilVisible(
      containerSelector = Selector.Tag("dropdown_menu"),
      targetSelector = Selector.Text("Maroon")
    )
    click("Maroon")
    assertVisible("Chosen color was: Maroon")
    click("OK")
  }

}