package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.PickerAction
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FilePickerDslTest {

  private class MockTestDriver(
    private val handled: Boolean = true
  ) : TestDriver {
    override val targetPlatform: String = "desktop"
    val sentCommands = mutableListOf<Command>()

    override suspend fun send(command: Command): Response {
      sentCommands.add(command)
      return when (command) {
        is Command.RegisterFilePicker -> Response.Ok(command.id)
        is Command.UnregisterFilePicker -> Response.FilePickerHandled(
          id = command.id,
          wasHandled = handled,
          selectedPaths = if (handled) listOf("/test/file.png") else emptyList(),
          wasCancelled = false,
          elapsedMs = 10L
        )
        else -> Response.Ok(command.id)
      }
    }

    override suspend fun close() {}
  }

  @Test
  fun testWithFilePickerSuccess() = runTest {
      val mockDriver = MockTestDriver(handled = true)
      val scope = E2ETestScope(mockDriver, E2ETestConfig())

      var blockExecuted = false
      scope.withFilePicker("/test/file.png") {
        blockExecuted = true
      }

      assertTrue(blockExecuted)
      assertEquals(2, mockDriver.sentCommands.size)
      val reg = mockDriver.sentCommands[0] as Command.RegisterFilePicker
      assertTrue(reg.action is PickerAction.Select)
      assertEquals(listOf("/test/file.png"), reg.action.paths)

      val unreg = mockDriver.sentCommands[1] as Command.UnregisterFilePicker
      assertTrue(unreg.awaitCompletion)
  }

  @Test
  fun testWithFilePickerDirectorySuccess() = runTest {
    val mockDriver = MockTestDriver(handled = true)
    val scope = E2ETestScope(mockDriver, E2ETestConfig())

    var blockExecuted = false
    scope.withFilePicker("/test/directory") {
      blockExecuted = true
    }

    assertTrue(blockExecuted)
    assertEquals(2, mockDriver.sentCommands.size)
    val reg = mockDriver.sentCommands[0] as Command.RegisterFilePicker
    assertTrue(reg.action is PickerAction.Select)
    assertEquals(listOf("/test/directory"), reg.action.paths)
  }

  @Test
  fun testWithFilePickerCancelSuccess() = runTest {
    val mockDriver = MockTestDriver(handled = true)
    val scope = E2ETestScope(mockDriver, E2ETestConfig())

    var blockExecuted = false
    scope.withFilePickerCancel {
      blockExecuted = true
    }

    assertTrue(blockExecuted)
    val reg = mockDriver.sentCommands[0] as Command.RegisterFilePicker
    assertTrue(reg.action is PickerAction.Cancel)
  }

  @Test
  fun testWithFilePickerEmptyPathsThrows() = runTest {
    val mockDriver = MockTestDriver(handled = true)
    val scope = E2ETestScope(mockDriver, E2ETestConfig())

    assertFailsWith<IllegalArgumentException> {
      scope.withFilePicker(emptyList()) {}
    }
  }

  @Test
  fun testWithFilePickerThrowsPickerNotOpenedWhenNotHandled() = runTest {
    val mockDriver = MockTestDriver(handled = false)
    val scope = E2ETestScope(mockDriver, E2ETestConfig())

    val error = assertFailsWith<PickerNotOpenedException> {
      scope.withFilePicker("/test/file.png") {
        // Did not open dialog
      }
    }
    assertTrue(error.message?.contains("not triggered") == true)
  }

  @Test
  fun testWithFilePickerPreservesBlockExceptionAndUnregisters() = runTest {
    val mockDriver = MockTestDriver(handled = false)
    val scope = E2ETestScope(mockDriver, E2ETestConfig())

    val error = assertFailsWith<IllegalStateException> {
      scope.withFilePicker("/test/file.png") {
        error("Block failed explicitly")
      }
    }
    assertEquals("Block failed explicitly", error.message)
    assertEquals(2, mockDriver.sentCommands.size)
    val unreg = mockDriver.sentCommands[1] as Command.UnregisterFilePicker
    // Block threw, so awaitCompletion is false
    assertEquals(false, unreg.awaitCompletion)
  }
}
