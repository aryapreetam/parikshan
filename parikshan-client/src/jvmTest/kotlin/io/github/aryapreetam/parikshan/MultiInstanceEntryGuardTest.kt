@file:OptIn(InternalParikshanApi::class)

package io.github.aryapreetam.parikshan

import kotlinx.coroutines.runBlocking
import org.opentest4j.TestAbortedException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiInstanceEntryGuardTest {

  private val trackedProps = listOf(
    "parikshan.target",
    "parikshan.executionMode",
    "parikshan.activeTargets",
    "parikshan.instance.alice.target",
    "parikshan.instance.bob.target"
  )

  @AfterTest
  fun cleanupSystemProperties() {
    trackedProps.forEach { System.clearProperty(it) }
  }

  @Test
  fun testMultiTargetRejectsAndroidAndIosIn010() {
    val androidErr = assertFailsWith<UnsupportedOperationException> {
      e2eTest(instances = mapOf("alice" to Target.Desktop, "bob" to Target.Android)) { }
    }
    assertTrue(androidErr.message?.contains("0.1.0") == true)
    assertTrue(androidErr.message?.contains("0.2.0") == true)

    val iosErr = assertFailsWith<UnsupportedOperationException> {
      e2eTest(instances = mapOf("alice" to Target.Wasm, "bob" to Target.Ios)) { }
    }
    assertTrue(iosErr.message?.contains("0.1.0") == true)
  }

  @Test
  fun testMultiInstanceSkipsCleanlyOnIosAndroidAndSyncRunners() {
    for (runnerTarget in listOf("ios", "android", "sync")) {
      System.setProperty("parikshan.target", runnerTarget)
      val aborted = assertFailsWith<TestAbortedException>("Expected TestAbortedException for target=$runnerTarget") {
        e2eTest(listOf("alice", "bob")) { }
      }
      assertTrue(!aborted.message.isNullOrBlank())
    }
  }

  @Test
  fun testMultiTargetSkipsCleanlyWhenRequiredTargetNotActiveAndDoesNotLeakSystemProps() {
    System.setProperty("parikshan.target", "desktop")
    assertFailsWith<TestAbortedException> {
      e2eTest(instances = mapOf("alice" to Target.Desktop, "bob" to Target.Wasm)) { }
    }
    assertNull(System.getProperty("parikshan.instance.alice.target"))
    assertNull(System.getProperty("parikshan.instance.bob.target"))
  }

  @Test
  fun testInvalidActorIdRejectedAtEntryPoint() {
    assertFailsWith<IllegalArgumentException> {
      e2eTest(listOf("alice", "../bob")) { }
    }
    assertFailsWith<IllegalArgumentException> {
      e2eTest(instances = mapOf("alice user" to Target.Desktop, "bob" to Target.Wasm)) { }
    }
  }

  @Test
  fun testDesktopDriverRelaunchAppRejectedForMultiInstanceActor() = runBlocking {
    val ctx = ParikshanTestContext(
      instanceId = "bob",
      host = "127.0.0.1",
      port = 19999,
      sessionToken = "test-token"
    )
    val driver = DesktopDriver(ctx)
    val err = assertFailsWith<UnsupportedOperationException> {
      driver.relaunchApp()
    }
    assertTrue(err.message?.contains("bob") == true)
    assertTrue(err.message?.contains("relaunchApp()") == true)
  }

  @Test
  fun testWasmInstanceSlotsReuseReleasedIndicesAcrossTests() {
    WasmDriver.resetInstanceSlotsForTesting()
    try {
      val aliceSlot = WasmDriver.acquireInstanceSlot("alice")
      val bobSlot = WasmDriver.acquireInstanceSlot("bob")
      kotlin.test.assertEquals(0, aliceSlot)
      kotlin.test.assertEquals(1, bobSlot)

      WasmDriver.releaseInstanceSlot("alice")
      WasmDriver.releaseInstanceSlot("bob")

      val buyerSlot = WasmDriver.acquireInstanceSlot("buyer")
      val sellerSlot = WasmDriver.acquireInstanceSlot("seller")
      kotlin.test.assertEquals(0, buyerSlot)
      kotlin.test.assertEquals(1, sellerSlot)
    } finally {
      WasmDriver.resetInstanceSlotsForTesting()
    }
  }

  @Test
  fun testWasmMultiInstanceViewportUsesNullWhenHeadedAndFixedWhenHeadless() {
    val headedConfig = io.github.aryapreetam.parikshan.client.ParikshanWasmConfig(
      appUrl = "http://127.0.0.1:8081",
      headless = false,
      viewportWidth = 800,
      viewportHeight = 600,
      bridgeReadyTimeoutMs = 30_000L
    )
    assertNull(WasmDriver.resolveMultiInstanceViewportSize(headedConfig))

    val headlessConfig = headedConfig.copy(headless = true, viewportWidth = 600, viewportHeight = 700)
    kotlin.test.assertEquals(Pair(600, 700), WasmDriver.resolveMultiInstanceViewportSize(headlessConfig))
  }
}
