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
      findNodeByTagRaw(tag)?.toDesktopNode()
    }

  fun performClick(tag: String): Boolean =
    onEdt {
      val node = findNodeByTagRaw(tag) ?: return@onEdt false
      val action = node.config.getOrNull(SemanticsActions.OnClick)?.action ?: return@onEdt false
      action.invoke()
    }

  fun performSetText(
    tag: String,
    text: String
  ): Boolean =
    onEdt {
      val node = findNodeByTagRaw(tag) ?: return@onEdt false
      val action = node.config.getOrNull(SemanticsActions.SetText)?.action ?: return@onEdt false
      action.invoke(AnnotatedString(text))
    }

  fun performScrollBy(
    tag: String,
    direction: ScrollDirection,
    amountPx: Float = 260f
  ): Boolean =
    onEdt {
      val node = findNodeByTagRaw(tag) ?: return@onEdt false
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
      val scaleX = runCatching { window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0 }.getOrDefault(1.0)
      val scaleY = runCatching { window.graphicsConfiguration?.defaultTransform?.scaleY ?: 1.0 }.getOrDefault(1.0)
      val location = window.locationOnScreen
      Rectangle((location.x * scaleX).toInt(), (location.y * scaleY).toInt(), (window.width * scaleX).toInt(), (window.height * scaleY).toInt())
    }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun allNodes(): List<SemanticsNode> {
    return window.semanticsOwners
      .flatMap { owner ->
        owner.getAllSemanticsNodes(mergingEnabled = true)
      }
  }

  private fun findNodeByTagRaw(tag: String): SemanticsNode? {
    return allNodes().firstOrNull { node ->
      val nodeTag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@firstOrNull false
      nodeTag == tag
    }
  }

  private fun SemanticsNode.toDesktopNode(): DesktopNode? {
    val tag = config.getOrNull(SemanticsProperties.TestTag) ?: ""
    val scaleX = runCatching { window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0 }.getOrDefault(1.0)
    val scaleY = runCatching { window.graphicsConfiguration?.defaultTransform?.scaleY ?: 1.0 }.getOrDefault(1.0)
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

    val locationX = location.x * scaleX
    val locationY = location.y * scaleY

    return DesktopNode(
      tag = tag,
      bounds =
        Bounds(
          left = locationX + nodeBounds.left.toDouble() * scaleX,
          top = locationY + nodeBounds.top.toDouble() * scaleY,
          right = locationX + nodeBounds.right.toDouble() * scaleX,
          bottom = locationY + nodeBounds.bottom.toDouble() * scaleY
        ),
      visible = !invisible,
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
