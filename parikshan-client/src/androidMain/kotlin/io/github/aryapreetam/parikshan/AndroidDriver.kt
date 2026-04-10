package io.github.aryapreetam.parikshan

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTestApi::class)
class AndroidDriver private constructor(
  private val composeUiTest: AndroidComposeTestRule<*, *>
) : TestDriver {
  override fun resolveArtifactPath(relativePath: String): String =
    File(
      composeUiTest.activity.cacheDir,
      "parikshan/${relativePath.trimStart('/', '\\')}"
    ).absolutePath

  override suspend fun send(command: Command): Response {
    return runCatching {
      handleCommand(command)
    }.getOrElse { throwable ->
      Response.Error(
        id = command.id,
        message = throwable.message ?: "Android driver error"
      )
    }
  }

  override suspend fun close() {
    // Lifecycle is owned by the Android test rule.
  }

  private fun handleCommand(command: Command): Response {
    return when (command) {
      is Command.Click -> {
        val interaction = findFirstInteraction(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        interaction.performClick()
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val interaction = findFirstInteraction(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        interaction.performTextClearance()
        interaction.performTextInput(command.text)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = findFirstNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        val bridgeHandled =
          ParikshanTagBridgeHooks.performScroll(
            tag = command.tag,
            direction = command.direction,
            viewportHeightPx = node.boundsInRoot.height
          )
        if (!bridgeHandled) {
          performDeviceSwipe(node, command.direction)
        }
        composeUiTest.waitForIdle()
        Response.Ok(command.id)
      }

      is Command.AssertVisible -> {
        val node = findFirstNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!isVisible(node)) {
          return Response.Error(command.id, "Node '${command.tag}' exists but is not visible")
        }
        Response.NodeInfo(
          id = command.id,
          bounds = boundsOf(node),
          visible = true,
          text = snapshotTextOf(node)
        )
      }

      is Command.AssertText -> {
        val node = findFirstNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        val actual = snapshotTextOf(node).orEmpty()
        if (actual != command.expected) {
          return Response.Error(
            id = command.id,
            message = "Text mismatch for '${command.tag}'. expected='${command.expected}' actual='$actual'"
          )
        }
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        val success =
          runCatching {
            composeUiTest.waitUntil(timeoutMillis = command.timeoutMs) {
              val node = findFirstNode(command.tag)
              node != null && isVisible(node)
            }
            true
          }.getOrDefault(false)
        if (!success) {
          return Response.Error(
            id = command.id,
            message = "Timed out waiting for '${command.tag}' after ${command.timeoutMs}ms"
          )
        }
        val node = findFirstNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        Response.NodeInfo(
          id = command.id,
          bounds = boundsOf(node),
          visible = isVisible(node),
          text = snapshotTextOf(node)
        )
      }

      is Command.Screenshot -> {
        val bitmap = captureRootBitmapWithRetry()
        writeBitmap(bitmap = bitmap, path = command.path)
        Response.Ok(command.id)
      }

      is Command.GetTree ->
        Response.Tree(
          id = command.id,
          nodes = snapshotTree()
        )

      is Command.PressBack -> {
        Espresso.pressBack()
        Response.Ok(command.id)
      }

      is Command.PressHome -> {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.pressHome()
        Response.Ok(command.id)
      }

      is Command.StartRecording -> Response.Ok(command.id)
      is Command.StopRecording -> Response.Ok(command.id)
      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  private fun findFirstInteraction(tag: String) =
    composeUiTest.onAllNodesWithTag(tag, useUnmergedTree = true)
      .takeIf { it.fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
      ?.onFirst()

  private fun findFirstNode(tag: String): SemanticsNode? =
    composeUiTest.onAllNodesWithTag(tag, useUnmergedTree = true)
      .fetchSemanticsNodes(atLeastOneRootRequired = false)
      .firstOrNull()

  private fun snapshotTree(): List<NodeSnapshot> {
    val root =
      composeUiTest.onRoot(useUnmergedTree = true).fetchSemanticsNode(
        errorMessageOnFail = "Unable to read root semantics node"
      )
    return buildList {
      appendTaggedNodes(root, this)
    }
  }

  private fun appendTaggedNodes(
    node: SemanticsNode,
    snapshots: MutableList<NodeSnapshot>
  ) {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag)
    if (tag != null) {
      snapshots +=
        NodeSnapshot(
          tag = tag,
          bounds = boundsOf(node),
          visible = isVisible(node),
          text = snapshotTextOf(node)
        )
    }
    node.children.forEach { child ->
      appendTaggedNodes(child, snapshots)
    }
  }

  private fun boundsOf(node: SemanticsNode): Bounds {
    val bounds = node.boundsInRoot
    return Bounds(
      left = bounds.left.toDouble(),
      top = bounds.top.toDouble(),
      right = bounds.right.toDouble(),
      bottom = bounds.bottom.toDouble()
    )
  }

  private fun isVisible(node: SemanticsNode): Boolean {
    val bounds = node.boundsInRoot
    return bounds.width > 0f && bounds.height > 0f && node.layoutInfo.isPlaced
  }

  private fun performDeviceSwipe(
    node: SemanticsNode,
    direction: ScrollDirection
  ) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val bounds = node.boundsInRoot
    val horizontalPadding = maxOf(bounds.width * 0.12f, 24f)
    val verticalPadding = maxOf(bounds.height * 0.12f, 24f)
    val centerX = ((bounds.left + bounds.right) / 2f).roundToInt()
    val centerY = ((bounds.top + bounds.bottom) / 2f).roundToInt()
    val leftX = (bounds.left + horizontalPadding).roundToInt()
    val rightX = (bounds.right - horizontalPadding).roundToInt()
    val topY = (bounds.top + verticalPadding).roundToInt()
    val bottomY = (bounds.bottom - verticalPadding).roundToInt()

    val (startX, startY, endX, endY) =
      when (direction) {
        ScrollDirection.Up -> listOf(centerX, topY, centerX, bottomY)
        ScrollDirection.Down -> listOf(centerX, bottomY, centerX, topY)
        ScrollDirection.Left -> listOf(leftX, centerY, rightX, centerY)
        ScrollDirection.Right -> listOf(rightX, centerY, leftX, centerY)
      }
    device.swipe(startX, startY, endX, endY, 18)
  }

  private fun snapshotTextOf(node: SemanticsNode): String? =
    directTextOf(node) ?: descendantTextsOf(node).takeIf { it.isNotEmpty() }?.joinToString(separator = "")

  private fun directTextOf(node: SemanticsNode): String? {
    node.config.getOrNull(SemanticsProperties.EditableText)?.text
      ?.takeIf { it.isNotBlank() }
      ?.let { return it }

    val values = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
    if (values.isNotEmpty()) {
      return values.joinToString(separator = "") { it.text }.takeIf { it.isNotBlank() }
    }
    return null
  }

  private fun descendantTextsOf(node: SemanticsNode): List<String> =
    buildList {
      node.children.forEach { child ->
        appendDescendantText(child, this)
      }
    }

  private fun appendDescendantText(
    node: SemanticsNode,
    texts: MutableList<String>
  ) {
    directTextOf(node)?.let { texts += it }
    node.children.forEach { child ->
      appendDescendantText(child, texts)
    }
  }

  private fun writeBitmap(
    bitmap: Bitmap,
    path: String
  ) {
    val file = File(path)
    file.parentFile?.mkdirs()
    FileOutputStream(file).use { output ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
  }

  private fun captureRootBitmapWithRetry(
    maxAttempts: Int = 4,
    retryDelayMs: Long = 250L
  ): Bitmap {
    var lastFailure: Throwable? = null
    repeat(maxAttempts) { attempt ->
      composeUiTest.waitForIdle()
      try {
        return composeUiTest
          .onRoot(useUnmergedTree = true)
          .captureToImage()
          .asAndroidBitmap()
      } catch (throwable: Throwable) {
        lastFailure = throwable
        if (attempt == maxAttempts - 1) {
          throw throwable
        }
        Thread.sleep(retryDelayMs)
      }
    }
    throw IllegalStateException("Android screenshot capture failed", lastFailure)
  }

  companion object {
    fun from(composeUiTest: AndroidComposeTestRule<*, *>): AndroidDriver =
      AndroidDriver(composeUiTest)
  }
}

@OptIn(ExperimentalTestApi::class)
fun e2eTest(
  composeUiTest: AndroidComposeTestRule<*, *>,
  config: E2ETestConfig = E2ETestConfig(),
  block: suspend E2ETestScope.() -> Unit
) {
  runBlocking {
    e2eTest(
      driver = AndroidDriver.from(composeUiTest),
      config = config,
      block = block
    )
  }
}
