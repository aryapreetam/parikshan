package com.example.app

import io.github.aryapreetam.parikshan.e2eTest
import org.junit.jupiter.api.Test

class SampleE2ETest {
    @Test
    fun testHelloWorldVisible() = e2eTest {
        assertVisible("Hello Beautiful World!")
    }
}
