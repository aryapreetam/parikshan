package sample.app

import io.github.aryapreetam.parikshan.BeforeAll
import io.github.aryapreetam.parikshan.AfterAll
import io.github.aryapreetam.parikshan.Order
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isAndroid
import kotlin.test.Test
import kotlin.test.assertEquals

class LifecycleIntegrationTest {

  companion object {
    private val executionLog = mutableListOf<String>()

    @BeforeAll
    fun setUpClass() {
      executionLog.add("beforeAll")
    }

    @AfterAll
    fun tearDownClass() {
      executionLog.add("afterAll")
      println("LIFECYCLE_SUCCESS_HOOK")
    }
  }

  @Test
  @Order(1)
  fun testFirst() = e2eTest {
    executionLog.add("firstTest")
    if (!isAndroid()) {
      assertEquals(listOf("beforeAll", "firstTest"), executionLog)
    }
  }

  @Test
  @Order(2)
  fun testSecond() = e2eTest {
    executionLog.add("secondTest")
    if (!isAndroid()) {
      assertEquals(listOf("beforeAll", "firstTest", "secondTest"), executionLog)
    }
  }
}
