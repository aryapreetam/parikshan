package sample.app

import io.github.aryapreetam.parikshan.BeforeAll
import io.github.aryapreetam.parikshan.AfterAll
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isAndroid
import kotlin.test.Test
import kotlin.test.assertTrue

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
  fun testLifecycleBeforeAll() = e2eTest {
    executionLog.add("testBody")
    if (!isAndroid()) {
      assertTrue(executionLog.contains("beforeAll"), "Expected beforeAll hook to have executed")
    }
  }
}
