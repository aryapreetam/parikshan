package io.github.aryapreetam.parikshan

import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PortAllocatorTest {

  @Test
  fun testAllocateEphemeralPortReturnsValidPort() {
    val port = PortAllocator.allocateEphemeralPort()
    assertTrue(port in 1024..65535, "Ephemeral port must be in unprivileged dynamic range: $port")
  }

  @Test
  fun testAllocateReturnsPreferredPortWhenAvailable() {
    val ephemeral = PortAllocator.allocateEphemeralPort()
    val allocated = PortAllocator.allocate(preferredPort = ephemeral)
    assertEquals(ephemeral, allocated, "Should allocate preferred port when it is free")
  }

  @Test
  fun testAllocateFallsBackWhenPreferredPortOccupied() {
    // Occupy an ephemeral port intentionally
    ServerSocket(0).use { boundSocket ->
      val occupiedPort = boundSocket.localPort
      assertFalse(PortAllocator.isPortFree(occupiedPort), "Port should be detected as occupied")

      val fallbackPort = PortAllocator.allocate(preferredPort = occupiedPort)
      assertNotEquals(occupiedPort, fallbackPort, "Should not return occupied port")
      assertTrue(fallbackPort in 1..65535, "Fallback port should be valid")
      assertTrue(PortAllocator.isPortFree(fallbackPort), "Allocated fallback port must be free")
    }
  }

  @Test
  fun testAllocateWithFallbackRange() {
    val ephemeral = PortAllocator.allocateEphemeralPort()
    val testRange = (ephemeral..ephemeral + 10).takeIf { it.last <= 65535 } ?: (ephemeral - 10..ephemeral)
    val port = PortAllocator.allocate(preferredPort = null, fallbackRange = testRange)
    assertTrue(port in 1..65535, "Allocated port must be valid")
    assertTrue(PortAllocator.isPortFree(port), "Allocated port must be free")
  }

  @Test
  fun testAllocateWithExcludedPorts() {
    val ephemeral = PortAllocator.allocateEphemeralPort()
    val port = PortAllocator.allocate(preferredPort = ephemeral, excludePorts = setOf(ephemeral))
    assertNotEquals(ephemeral, port, "Must not allocate port present in excludePorts")
    assertTrue(PortAllocator.isPortFree(port))
  }

  @Test
  fun testIsPortFreeValidation() {
    assertFalse(PortAllocator.isPortFree(-1), "Negative port is invalid")
    assertFalse(PortAllocator.isPortFree(0), "Port 0 is invalid for binding probe")
    assertFalse(PortAllocator.isPortFree(70000), "Port > 65535 is invalid")

    ServerSocket(0).use { socket ->
      val boundPort = socket.localPort
      assertFalse(PortAllocator.isPortFree(boundPort), "Port bound by ServerSocket must report false")
    }
  }

  @Test
  fun testAllocateUniqueReturnsDistinctPorts() {
    val count = 10
    val ports = PortAllocator.allocateUnique(count)
    assertEquals(count, ports.size)
    assertEquals(count, ports.toSet().size, "All allocated ports must be unique")
    ports.forEach { port ->
      assertTrue(port in 1..65535)
      assertTrue(PortAllocator.isPortFree(port))
    }
  }

  @Test
  fun testAllocateUniqueWithZeroCount() {
    val ports = PortAllocator.allocateUnique(0)
    assertTrue(ports.isEmpty())
  }

  @Test
  fun testConcurrentAllocationsDoNotCollide() {
    val threadCount = 20
    val ports = ConcurrentHashMap.newKeySet<Int>()
    val latch = CountDownLatch(threadCount)
    val startGate = CountDownLatch(1)

    val threads = List(threadCount) {
      thread {
        startGate.await(5, TimeUnit.SECONDS)
        try {
          val port = PortAllocator.allocate()
          ports.add(port)
        } finally {
          latch.countDown()
        }
      }
    }

    startGate.countDown()
    assertTrue(latch.await(10, TimeUnit.SECONDS), "Concurrent allocations timed out")
    threads.forEach { it.join(2000) }

    assertEquals(threadCount, ports.size, "Concurrent allocations must yield distinct ports")
  }
}
