package com.example.app

import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import org.junit.jupiter.api.Test

class SampleE2ETest {
    @Test
    fun testHelloWorldVisible() = e2eTest {
        assertVisible("Hello Beautiful World!")
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
