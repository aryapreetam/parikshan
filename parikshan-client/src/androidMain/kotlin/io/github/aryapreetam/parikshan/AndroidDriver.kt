package io.github.aryapreetam.parikshan

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.resolvedSelector
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

  private data class SelectorCandidate(
    val node: SemanticsNode,
    val score: Int,
    val area: Float,
    val depth: Int
  )

  private fun findFirstInteraction(command: Command): SemanticsNodeInteraction? =
    findFirstNode(command)?.let { interactionFor(it) }

  private fun findFirstNode(command: Command): SemanticsNode? {
    val selector = command.resolvedSelector() ?: return null
    return selectorCandidates(selector).firstOrNull()?.node
  }

  private fun selectorCandidates(selector: Selector): List<SelectorCandidate> {
    if (selector.raw.isBlank()) return emptyList()
    val matcher =
      SemanticsMatcher("Parikshan selector '${selector.raw}'") { node ->
        selectorScore(node = node, selector = selector) != null
      }

    return composeUiTest
      .onAllNodes(matcher, useUnmergedTree = true)
      .fetchSemanticsNodes(atLeastOneRootRequired = false)
      .asSequence()
      .filter { isVisible(it) }
      .mapNotNull { node ->
        selectorScore(node = node, selector = selector)?.let { score ->
          SelectorCandidate(
            node = node,
            score = score,
            area = nodeArea(node),
            depth = nodeDepth(node)
          )
        }
      }
      .sortedWith(
        compareBy<SelectorCandidate> { it.score }
          .thenBy { it.area }
          .thenByDescending { it.depth }
      )
      .toList()
  }

  private fun selectorScore(
    node: SemanticsNode,
    selector: Selector
  ): Int? {
    val raw = selector.raw.trim()
    if (raw.isEmpty()) return null

    val tag = node.config.getOrNull(SemanticsProperties.TestTag)?.trim()
    val text = directTextOf(node)?.trim()

    return when (selector) {
      is Selector.Tag ->
        if (tag == selector.value.trim()) 0 else null

      is Selector.Text ->
        textScore(text = text, raw = selector.value.trim())

      is Selector.Auto ->
        when {
          tag == raw -> 0
          text?.equals(raw, ignoreCase = true) == true -> 10
          text?.contains(raw, ignoreCase = true) == true -> 20
          else -> null
        }
    }
  }

  private fun textScore(
    text: String?,
    raw: String
  ): Int? =
    when {
      raw.isEmpty() || text == null -> null
      text.equals(raw, ignoreCase = true) -> 10
      text.contains(raw, ignoreCase = true) -> 20
      else -> null
    }

  private fun interactionFor(node: SemanticsNode): SemanticsNodeInteraction =
    composeUiTest.onNode(
      matcher = SemanticsMatcher("SemanticsNode(id=${node.id})") { it.id == node.id },
      useUnmergedTree = true
    )

  private fun clickTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.OnClick) != null) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun nodeArea(node: SemanticsNode): Float {
    val bounds = node.boundsInRoot
    return bounds.width * bounds.height
  }

  private fun nodeDepth(node: SemanticsNode): Int {
    var depth = 0
    var current = node.parent
    while (current != null) {
      depth += 1
      current = current.parent
    }
    return depth
  }

  private fun handleCommand(command: Command): Response {
    return when (command) {
      is Command.Click -> {
        val matched = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${command.selector?.raw ?: command.tag}'")
        val target = clickTargetFor(matched)
          ?: return Response.Error(command.id, "Node '${command.selector?.raw ?: command.tag}' is not clickable")
        interactionFor(target).performClick()
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val interaction = findFirstInteraction(command)
          ?: return Response.Error(command.id, "No node found for selector '${command.selector?.raw ?: command.tag}'")
        interaction.performTextClearance()
        interaction.performTextInput(command.text)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${command.selector?.raw ?: command.tag}'")
        val bridgeHandled =
          ParikshanTagBridgeHooks.performScroll(
            tag = command.tag, // keep fallback
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
        val node = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${command.selector?.raw ?: command.tag}'")
        if (!isVisible(node)) {
          return Response.Error(command.id, "Node '${command.selector?.raw ?: command.tag}' exists but is not visible")
        }
        Response.NodeInfo(
          id = command.id,
          bounds = boundsOf(node),
          visible = true,
          text = snapshotTextOf(node)
        )
      }

      is Command.AssertText -> {
        val node = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${command.selector?.raw ?: command.tag}'")
        val actual = snapshotTextOf(node).orEmpty()
        if (actual != command.expected) {
          return Response.Error(
            id = command.id,
            message = "Text mismatch for '${command.selector?.raw ?: command.tag}'. expected='${command.expected}' actual='$actual'"
          )
        }
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        val success =
          runCatching {
            composeUiTest.waitUntil(timeoutMillis = command.timeoutMs) {
              val node = findFirstNode(command)
              node != null && isVisible(node)
            }
            true
          }.getOrDefault(false)
        if (!success) {
          return Response.Error(
            id = command.id,
            message = "Timed out waiting for '${command.selector?.raw ?: command.tag}' after ${command.timeoutMs}ms"
          )
        }
        val node = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${command.selector?.raw ?: command.tag}'")
        Response.NodeInfo(
          id = command.id,
          bounds = boundsOf(node),
          visible = isVisible(node),
          text = snapshotTextOf(node)
        )
      }

      is Command.Screenshot -> {
        val bitmap = captureRootBitmapWithRetry()
        writeBitmap(bitmap = bitmap, path = command.devicePath)
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

      is Command.RelaunchApp -> relaunchTargetApp(command.id)
      is Command.StartRecording -> Response.Ok(command.id)
      is Command.StopRecording -> Response.Ok(command.id)
      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  private fun relaunchTargetApp(commandId: String): Response {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val intent =
      context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: return Response.Error(commandId, "Could not find launch intent for package ${context.packageName}")
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    instrumentation.waitForIdleSync()
    composeUiTest.waitForIdle()
    return Response.Ok(commandId)
  }


  private fun snapshotTree(): List<NodeSnapshot> {
    val root =
      composeUiTest.onRoot(useUnmergedTree = false).fetchSemanticsNode(
        errorMessageOnFail = "Unable to read root semantics node"
      )
    val rootBounds = try {
      composeUiTest.onRoot().fetchSemanticsNode().boundsInRoot
    } catch (e: Throwable) {
      null
    }
    return buildList {
      appendSnapshots(root, this, rootBounds)
    }
  }

  private fun appendSnapshots(
    node: SemanticsNode,
    snapshots: MutableList<NodeSnapshot>,
    rootBounds: androidx.compose.ui.geometry.Rect?
  ) {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
    val editableText = node.config.getOrNull(SemanticsProperties.EditableText)?.text
    val spokenText = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
    val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()

    val textValue = editableText?.takeIf { it.isNotBlank() }
      ?: spokenText.takeIf { it.isNotBlank() }
      ?: contentDescription.ifBlank { null }
    
    if (tag.isNotEmpty() || textValue != null) {
      snapshots +=
        NodeSnapshot(
          tag = tag,
          bounds = boundsOf(node),
          visible = isVisible(node, rootBounds),
          text = textValue
        )
    }
    node.children.forEach { child ->
      appendSnapshots(child, snapshots, rootBounds)
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

  private fun isVisible(node: SemanticsNode, rootBounds: androidx.compose.ui.geometry.Rect? = null): Boolean {
    val bounds = node.boundsInRoot
    val hasArea = bounds.width > 0f && bounds.height > 0f
    if (!hasArea || !node.layoutInfo.isPlaced) return false

    return if (rootBounds != null) {
      val centerX = (bounds.left + bounds.right) / 2f
      val centerY = (bounds.top + bounds.bottom) / 2f
      centerX >= rootBounds.left && centerX <= rootBounds.right &&
        centerY >= rootBounds.top && centerY <= rootBounds.bottom
    } else {
      true
    }
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
    directTextOf(node) ?: descendantTextsOf(node).takeIf { it.isNotEmpty() }?.joinToString("")

  private fun directTextOf(node: SemanticsNode): String? {
    node.config.getOrNull(SemanticsProperties.EditableText)?.text
      ?.takeIf { it.isNotBlank() }
      ?.let { return it }

    val values = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
    if (values.isNotEmpty()) {
      values.joinToString("") { it.text }.takeIf { it.isNotBlank() }?.let { return it }
    }

    val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    if (contentDescription.isNotEmpty()) {
      return contentDescription.joinToString("").takeIf { it.isNotBlank() }
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
          .onRoot(useUnmergedTree = false)
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
