package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalParikshanApi::class)
class E2EMultiInstanceScopeTest {

  private class MockDriver(
    override val targetPlatform: String = "desktop",
    var shouldFailPing: Boolean = false
  ) : TestDriver {
    val receivedCommands = mutableListOf<Command>()
    var isClosed = false
    var resetCount = 0

    override suspend fun send(command: Command): Response {
      receivedCommands.add(command)
      if (command is Command.Ping && shouldFailPing) {
        return Response.Error(command.id, "Simulated ping connection failure")
      }
      return Response.Ok(command.id)
    }

    override suspend fun reset() {
      resetCount++
    }

    override suspend fun close() {
      isClosed = true
    }
  }

  @Test
  fun testResolveRegisteredInstances() {
    val driverAlice = MockDriver("desktop")
    val driverBob = MockDriver("wasm")
    val scopeAlice = E2ETestScope(driverAlice, E2ETestConfig())
    val scopeBob = E2ETestScope(driverBob, E2ETestConfig())

    val multiScope = E2EMultiInstanceScope(
      mapOf("alice" to scopeAlice, "bob" to scopeBob)
    )

    assertEquals(2, multiScope.size)
    assertEquals(setOf("alice", "bob"), multiScope.instanceNames)
    assertEquals("desktop", multiScope.instance("alice").targetPlatform)
    assertEquals("wasm", multiScope["bob"].targetPlatform)
  }

  @Test
  fun testResolveUnregisteredInstanceThrowsIllegalArgumentException() {
    val driver = MockDriver("desktop")
    val scope = E2ETestScope(driver, E2ETestConfig())
    val multiScope = E2EMultiInstanceScope(mapOf("alice" to scope))

    val error = assertFailsWith<IllegalArgumentException> {
      multiScope.instance("charlie")
    }

    assertTrue(error.message?.contains("No application instance named 'charlie' found") == true)
    assertTrue(error.message?.contains("Available instances: [alice]") == true)
  }

  @Test
  fun testInParallelExecutesAllInstancesConcurrently() = runBlocking {
    val driverAlice = MockDriver("desktop")
    val driverBob = MockDriver("wasm")
    val scopeAlice = E2ETestScope(driverAlice, E2ETestConfig())
    val scopeBob = E2ETestScope(driverBob, E2ETestConfig())

    val multiScope = E2EMultiInstanceScope(
      mapOf("alice" to scopeAlice, "bob" to scopeBob)
    )

    val executed = mutableListOf<String>()
    multiScope.inParallel {
      delay(10)
      executed.add(targetPlatform)
    }

    assertEquals(2, executed.size)
    assertTrue(executed.contains("desktop"))
    assertTrue(executed.contains("wasm"))
  }

  @Test
  fun testInParallelWithNamedActions() = runBlocking {
    val driverAlice = MockDriver("desktop")
    val driverBob = MockDriver("wasm")
    val scopeAlice = E2ETestScope(driverAlice, E2ETestConfig())
    val scopeBob = E2ETestScope(driverBob, E2ETestConfig())

    val multiScope = E2EMultiInstanceScope(
      mapOf("alice" to scopeAlice, "bob" to scopeBob)
    )

    var aliceExecuted = false
    var bobExecuted = false

    multiScope.inParallel(
      "alice" to {
        delay(5)
        aliceExecuted = true
      },
      "bob" to {
        delay(5)
        bobExecuted = true
      }
    )

    assertTrue(aliceExecuted)
    assertTrue(bobExecuted)
  }

  @Test
  fun testScopeEmptyInstancesThrowsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> {
      E2EMultiInstanceScope(emptyMap())
    }
  }

  @Test
  fun testE2eTestEmptyDriversThrowsIllegalArgumentException() {
    runBlocking {
      assertFailsWith<IllegalArgumentException> {
        e2eTest(drivers = emptyMap()) {}
      }
    }
  }

  @Test
  fun testE2eTestLifecycleSuccess() = runBlocking {
    val driverAlice = MockDriver("desktop")
    val driverBob = MockDriver("wasm")

    var blockExecuted = false
    e2eTest(
      drivers = mapOf("alice" to driverAlice, "bob" to driverBob)
    ) {
      blockExecuted = true
      assertEquals("desktop", instance("alice").targetPlatform)
      assertEquals("wasm", instance("bob").targetPlatform)
    }

    assertTrue(blockExecuted)
    assertTrue(driverAlice.isClosed)
    assertTrue(driverBob.isClosed)
    assertEquals(2, driverAlice.resetCount)
    assertEquals(2, driverBob.resetCount)
  }

  @Test
  fun testE2eTestPingFailureThrowsAndClosesDrivers() = runBlocking {
    val driverAlice = MockDriver("desktop", shouldFailPing = true)
    val driverBob = MockDriver("wasm")

    assertFailsWith<IllegalStateException> {
      e2eTest(
        drivers = mapOf("alice" to driverAlice, "bob" to driverBob)
      ) {
        // block should not execute
      }
    }

    assertTrue(driverAlice.isClosed)
    assertTrue(driverBob.isClosed)
  }

  @Test
  fun testE2eTestPropagatesBlockExceptionAndClosesDrivers() = runBlocking {
    val driverAlice = MockDriver("desktop")
    val driverBob = MockDriver("wasm")

    assertFailsWith<AssertionError> {
      e2eTest(
        drivers = mapOf("alice" to driverAlice, "bob" to driverBob)
      ) {
        throw AssertionError("Test failure inside scenario")
      }
    }

    assertTrue(driverAlice.isClosed)
    assertTrue(driverBob.isClosed)
  }
}
