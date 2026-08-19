package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.runBlocking
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeTargetDriver(
  override val targetPlatform: String,
  private val responseHandler: (Command) -> Response = { Response.Ok("fake-ok") }
) : TestDriver {
  val receivedCommands = Collections.synchronizedList(mutableListOf<Command>())

  override suspend fun send(command: Command): Response {
    receivedCommands.add(command)
    return responseHandler(command)
  }

  override suspend fun close() {}
}

class BroadcastDriverTest {

  @Test
  fun testBroadcastDispatchesToAllTargetsInParallel() = runBlocking {
    val desktopDriver = FakeTargetDriver("desktop")
    val wasmDriver = FakeTargetDriver("wasm")
    val androidDriver = FakeTargetDriver("android")
    val iosDriver = FakeTargetDriver("ios")

    val broadcast = BroadcastDriver(listOf(desktopDriver, wasmDriver, androidDriver, iosDriver))

    val cmd = Command.Click(id = "click-1", tag = "login_btn")
    val response = broadcast.send(cmd)

    assertTrue(response is Response.Ok)
    assertEquals(1, desktopDriver.receivedCommands.size)
    assertEquals(1, wasmDriver.receivedCommands.size)
    assertEquals(1, androidDriver.receivedCommands.size)
    assertEquals(1, iosDriver.receivedCommands.size)
    assertEquals(cmd, desktopDriver.receivedCommands[0])
    assertEquals(cmd, wasmDriver.receivedCommands[0])
  }

  @Test
  fun testDragCoordinateTransformationAcrossTargets() = runBlocking {
    val desktopNode = NodeSnapshot(
      tag = "form_slider",
      text = null,
      bounds = Bounds(left = 100.0, top = 100.0, right = 300.0, bottom = 200.0) // width = 200, height = 100
    )
    val wasmNode = NodeSnapshot(
      tag = "form_slider",
      text = null,
      bounds = Bounds(left = 50.0, top = 80.0, right = 150.0, bottom = 140.0) // width = 100, height = 60
    )

    val desktopDriver = FakeTargetDriver("desktop") { cmd ->
      if (cmd is Command.GetTree) {
        Response.Tree(id = cmd.id, nodes = listOf(desktopNode))
      } else {
        Response.Ok("desktop-drag-ok")
      }
    }

    val wasmDriver = FakeTargetDriver("wasm") { cmd ->
      if (cmd is Command.GetTree) {
        Response.Tree(id = cmd.id, nodes = listOf(wasmNode))
      } else {
        Response.Ok("wasm-drag-ok")
      }
    }

    val broadcast = BroadcastDriver(listOf(desktopDriver, wasmDriver))

    // Drag from 25% (x=150) to 75% (x=250) on desktop
    val dragCmd = Command.Drag(
      id = "drag-test",
      fromX = 150.0,
      fromY = 150.0,
      toX = 250.0,
      toY = 150.0,
      durationMs = 200
    )

    val response = broadcast.send(dragCmd)
    assertTrue(response is Response.Ok)

    val wasmDragCmd = wasmDriver.receivedCommands.filterIsInstance<Command.Drag>().firstOrNull()
    assertTrue(wasmDragCmd != null, "Wasm driver should have received a Drag command")

    // On wasm: left=50, width=100 -> 25% is 75.0, 75% is 125.0
    assertEquals(75.0, wasmDragCmd.fromX, 0.001)
    assertEquals(125.0, wasmDragCmd.toX, 0.001)
  }

  @Test
  fun testPartialFailureThrowsMultiTargetFailureException() = runBlocking {
    val desktopDriver = FakeTargetDriver("desktop") { Response.Ok("success") }
    val wasmDriver = FakeTargetDriver("wasm") { Response.Error(id = it.id, message = "Element 'submit_btn' not found") }

    val broadcast = BroadcastDriver(listOf(desktopDriver, wasmDriver))

    val ex = assertFailsWith<MultiTargetFailureException> {
      runBlocking {
        broadcast.send(Command.Click(id = "click-err", tag = "submit_btn"))
      }
    }

    assertTrue(
      ex.message?.contains("[Wasm] Element 'submit_btn' not found") == true,
      "Expected aggregated failure message containing [Wasm] error, got: ${ex.message}"
    )
  }

  @Test
  fun testRouteTargetScopingFiltersExecution() = runBlocking {
    val desktopDriver = FakeTargetDriver("desktop")
    val wasmDriver = FakeTargetDriver("wasm")

    val broadcast = BroadcastDriver(listOf(desktopDriver, wasmDriver))

    broadcast.setRouteTarget("wasm")
    broadcast.send(Command.Click(id = "wasm-routed", tag = "web_banner"))

    assertEquals(0, desktopDriver.receivedCommands.size)
    assertEquals(1, wasmDriver.receivedCommands.size)
    assertEquals("web_banner", (wasmDriver.receivedCommands[0] as Command.Click).tag)

    broadcast.setRouteTarget(null)
    broadcast.send(Command.Click(id = "broadcast-all", tag = "common_btn"))

    assertEquals(1, desktopDriver.receivedCommands.size)
    assertEquals(2, wasmDriver.receivedCommands.size)
  }

  @Test
  fun testScreenshotPathIsolation() = runBlocking {
    val desktopDriver = FakeTargetDriver("desktop")
    val wasmDriver = FakeTargetDriver("wasm")

    val broadcast = BroadcastDriver(listOf(desktopDriver, wasmDriver))

    val screenshotCmd = Command.Screenshot(
      id = "shot-1",
      hostPath = "build/parikshan/shot.png",
      devicePath = "parikshan/shot.png"
    )

    broadcast.send(screenshotCmd)

    val desktopScreenshot = desktopDriver.receivedCommands.filterIsInstance<Command.Screenshot>().firstOrNull()
    val wasmScreenshot = wasmDriver.receivedCommands.filterIsInstance<Command.Screenshot>().firstOrNull()

    assertTrue(desktopScreenshot != null)
    assertTrue(wasmScreenshot != null)
    assertEquals("build/parikshan/shot_desktop.png", desktopScreenshot.hostPath)
    assertEquals("build/parikshan/shot_wasm.png", wasmScreenshot.hostPath)
  }

  @Test
  fun testExecuteParallelRunsOnEachTargetIndependently() = runBlocking {
    val desktopDriver = FakeTargetDriver("desktop")
    val wasmDriver = FakeTargetDriver("wasm")

    val broadcast = BroadcastDriver(listOf(desktopDriver, wasmDriver))
    val visitedPlatforms = Collections.synchronizedList(mutableListOf<String>())

    broadcast.executeParallel { driver ->
      visitedPlatforms.add(driver.targetPlatform)
    }

    assertEquals(2, visitedPlatforms.size)
    assertTrue(visitedPlatforms.contains("desktop"))
    assertTrue(visitedPlatforms.contains("wasm"))
  }
}
