package io.github.aryapreetam.parikshan.gradle

import java.net.ServerSocket
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

class DesktopProcessTest {

  @Test
  fun `given no desktop process when stopping then return without startup delay`() {
    val unusedPort = ServerSocket(0).use { it.localPort }

    val elapsedMs = measureTimeMillis {
      DesktopProcess.stop(host = "127.0.0.1", port = unusedPort)
    }

    assertTrue(
      elapsedMs < 2_500L,
      "Stopping without a running desktop process took ${elapsedMs}ms",
    )
  }
}