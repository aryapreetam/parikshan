package com.example.standaloneandroid

import io.github.aryapreetam.parikshan.e2eTest
import org.junit.Test

class CalculatorE2ETest {
  @Test
  fun testCalculatorOperations() = e2eTest {
    // 1. Ensure a clean state by launching/relaunching the application
   // relaunchApp()

    // 2. Input first number (e.g. 15)
    input("firstNumberInput", "15")

    // 3. Input second number (e.g. 27)
    input("secondNumberInput", "27")

    // 4. Click the Calculate button to run the logic
    click("calculateButton")

    // 5. Verify that the correct sum result (42) is displayed on screen
    assertVisible("42")
    // Or assert visibility of the result component directly using its tag:
    // assertVisible("resultText")
  }
}
