package org.example.project

import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class SimpleE2ETest {
  @Test
  fun test() = e2eTest {
    assertNotVisible("Compose: Hello")
    click("Click me!")
    assertVisible("Compose: Hello")
  }
}