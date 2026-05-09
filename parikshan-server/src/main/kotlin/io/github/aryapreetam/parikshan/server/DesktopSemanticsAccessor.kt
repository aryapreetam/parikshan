package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Selector
import java.awt.Rectangle
import javax.swing.SwingUtilities

internal data class DesktopNode(
  val tag: String,
  val bounds: Bounds,
  val visible: Boolean,
  val text: String?
)

internal class DesktopSemanticsAccessor(
  private val window: ComposeWindow
) {
  fun findByTag(tag: String): DesktopNode? =
    onEdt {
      val raw = findNodeByTagRaw(tag)
      raw?.toDesktopNode()
    }

  fun findBySelector(selector: Selector): DesktopNode? = onEdt {
    when (selector) {
      is Selector.Tag -> findNodeByTagOnly(selector.value)?.toDesktopNode()
      is Selector.Text -> findNodeByText(selector.value)?.toDesktopNode()
      is Selector.Auto -> {
        findNodeByTagOnly(selector.raw)?.toDesktopNode()
          ?: findNodeByText(selector.raw)?.toDesktopNode()
      }
    }
  }

  private fun findNodeByTagOnly(tag: String): SemanticsNode? {
    return allNodes().firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }
  }

  private fun findNodeByText(textValue: String): SemanticsNode? {
    return allNodes().firstOrNull { node ->
      val textList = node.config.getOrNull(SemanticsProperties.Text)
      val text = textList?.joinToString("") { it.text } 
        ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
      text?.contains(textValue, ignoreCase = true) == true
    }
  }

  fun performClick(selector: Selector): Boolean {
    val desktopNode = findBySelector(selector) ?: return false

    val semanticSuccess = onEdt {
      var node = findSemanticsNodeBySelector(selector) ?: return@onEdt false
      var action = node.config.getOrNull(SemanticsActions.OnClick)?.action
      while (action == null && node.parent != null) {
          node = node.parent!!
          action = node.config.getOrNull(SemanticsActions.OnClick)?.action
      }
      if (action == null) return@onEdt false

      try {
        action.invoke()
      } catch (e: Exception) {
        false
      }
    }

    if (semanticSuccess) return true

    // Fallback: Native AWT Robot Click
    return try {
      val robot = java.awt.Robot()
      val x = desktopNode.bounds.centerX.toInt()
      val y = desktopNode.bounds.centerY.toInt()

      robot.mouseMove(x, y)
      robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
      Thread.sleep(50) // Brief delay to simulate human click
      robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
      true
    } catch (e: Exception) {
      false
    }
  }

  private fun findSemanticsNodeBySelector(selector: Selector): SemanticsNode? {
    return when (selector) {
      is Selector.Tag -> findNodeByTagOnly(selector.value)
      is Selector.Text -> findNodeByText(selector.value)
      is Selector.Auto -> findNodeByTagOnly(selector.raw) ?: findNodeByText(selector.raw)
    }
  }

  fun performSetText(
    selector: Selector,
    text: String
  ): Boolean =
    onEdt {
      val node = findSemanticsNodeBySelector(selector) ?: return@onEdt false
      val action = node.config.getOrNull(SemanticsActions.SetText)?.action ?: return@onEdt false
      action.invoke(AnnotatedString(text))
    }

  fun performScrollBy(
    selector: Selector,
    direction: ScrollDirection,
    amountPx: Float = 200f
  ): Boolean =
    onEdt {
      val node = findSemanticsNodeBySelector(selector) ?: return@onEdt false
      val action = node.config.getOrNull(SemanticsActions.ScrollBy)?.action ?: return@onEdt false

      val (deltaX, deltaY) =
        when (direction) {
          ScrollDirection.Up -> 0f to -amountPx
          ScrollDirection.Down -> 0f to amountPx
          ScrollDirection.Left -> -amountPx to 0f
          ScrollDirection.Right -> amountPx to 0f
        }

      action.invoke(deltaX, deltaY)
    }

  fun snapshotTree(): List<NodeSnapshot> =
    onEdt {
      allNodes()
        .asSequence()
        .mapNotNull { it.toDesktopNode() }
        .map { node ->
          NodeSnapshot(
            tag = node.tag,
            bounds = node.bounds,
            visible = node.visible,
            text = node.text
          )
        }
        .toList()
    }

  fun windowBoundsOnScreen(): Rectangle =
    onEdt {
      val location = window.locationOnScreen
      Rectangle(location.x, location.y, window.width, window.height)
    }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun allNodes(): List<SemanticsNode> {
    return window.semanticsOwners
      .flatMap { owner ->
        val merged = owner.getAllSemanticsNodes(mergingEnabled = true)
        val unmerged = owner.getAllSemanticsNodes(mergingEnabled = false)
        (merged + unmerged).distinctBy { it.id }
      }
  }

  private fun findNodeByTagRaw(tag: String): SemanticsNode? {
    val all = allNodes()
    all.firstOrNull { it.config.getOrNull(SemanticsProperties.TestTag) == tag }?.let { return it }
    return all.firstOrNull { node ->
      val textList = node.config.getOrNull(SemanticsProperties.Text)
      val text = textList?.joinToString("") { it.text } 
        ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
      text?.contains(tag, ignoreCase = true) == true
    }
  }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun SemanticsNode.toDesktopNode(): DesktopNode? {
    val tag = config.getOrNull(SemanticsProperties.TestTag) ?: ""
    val location = window.locationOnScreen
    val nodeBounds = boundsInWindow
    val editableText = config.getOrNull(SemanticsProperties.EditableText)?.text
    val spokenText =
      config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(separator = "") { it.text }
        .orEmpty()
    val invisible = config.getOrNull(SemanticsProperties.InvisibleToUser) != null
    val textValue = editableText?.takeIf { it.isNotBlank() } ?: spokenText.ifBlank { null }

    // Include nodes that have either a testTag or text content
    if (tag.isBlank() && textValue == null) return null

    // Determine the actual visible viewport of the Compose content area.
    // We convert Compose bounds (which are in density-dependent pixels) to 
    // logical coordinates to match the AWT content pane.
    val density = window.graphicsConfiguration.defaultTransform.scaleX.toFloat()
    val contentBounds = window.contentPane.bounds
    val isPhysicallyVisible = 
      (nodeBounds.left / density) < contentBounds.width &&
      (nodeBounds.right / density) > 0 &&
      (nodeBounds.top / density) < contentBounds.height &&
      (nodeBounds.bottom / density) > 0

    return DesktopNode(
      tag = tag,
      bounds =
        Bounds(
          left = location.x + nodeBounds.left.toDouble(),
          top = location.y + nodeBounds.top.toDouble(),
          right = location.x + nodeBounds.right.toDouble(),
          bottom = location.y + nodeBounds.bottom.toDouble()
        ),
      visible = !invisible && isPhysicallyVisible,
      text = textValue
    )
  }

  private fun <T> onEdt(action: () -> T): T {
    if (SwingUtilities.isEventDispatchThread()) {
      return action()
    }

    var result: T? = null
    var error: Throwable? = null
    SwingUtilities.invokeAndWait {
      try {
        result = action()
      } catch (throwable: Throwable) {
        error = throwable
      }
    }
    error?.let { throw it }
    @Suppress("UNCHECKED_CAST")
    return result as T
  }
}
