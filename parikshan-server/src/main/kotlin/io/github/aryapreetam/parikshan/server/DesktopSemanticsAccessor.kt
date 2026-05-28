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
import io.github.aryapreetam.parikshan.resolveNode
import java.awt.Rectangle
import java.awt.Window
import javax.swing.SwingUtilities

internal data class DesktopNode(
  val tag: String,
  val bounds: Bounds,
  val visible: Boolean,
  val text: String?
)

internal data class WindowedNode(
  val window: ComposeWindow,
  val node: SemanticsNode
)

internal class DesktopSemanticsAccessor(
  private val primaryWindow: ComposeWindow
) {
  fun findBySelector(selector: Selector): DesktopNode? = onEdt {
    findResolvedWindowedNode(selector)?.toDesktopNode()
  }

  private fun findResolvedWindowedNode(selector: Selector): WindowedNode? {
    val all = allNodes()
    val snapshotsWithIndex = all.mapIndexedNotNull { index, winNode ->
      winNode.toDesktopNode()?.let { desktop ->
        index to NodeSnapshot(
          tag = desktop.tag,
          bounds = desktop.bounds,
          visible = desktop.visible,
          text = desktop.text,
          zOrder = index
        )
      }
    }

    val snapshots = snapshotsWithIndex.map { it.second }
    val resolved = try {
      selector.resolveNode(nodes = snapshots, requireVisible = true)
    } catch (e: Exception) {
      return null
    }

    val originalIndex = resolved.node.zOrder
    return all.getOrNull(originalIndex)
  }

  fun performClick(selector: Selector): Boolean {
    val desktopNode = findBySelector(selector) ?: return false

    // Primary: Semantic OnClick walk.
    // Walk the parent chain looking for the best OnClick action to invoke.
    val semanticSuccess = onEdt {
      var currentNode: SemanticsNode? =
        findResolvedWindowedNode(selector)?.node ?: return@onEdt false
      var textFieldAction: (() -> Boolean)? = null
      var bestAction: (() -> Boolean)? = null

      while (currentNode != null) {
        val action = currentNode.config.getOrNull(SemanticsActions.OnClick)?.action
        if (action != null) {
          val isEditable =
            currentNode.config.getOrNull(SemanticsProperties.EditableText) != null
          if (!isEditable) {
            bestAction = action
            break
          }
          if (textFieldAction == null) {
            textFieldAction = action
          }
        }
        currentNode = currentNode.parent
      }

      val actionToInvoke = bestAction ?: textFieldAction ?: return@onEdt false
      try {
        actionToInvoke.invoke()
      } catch (e: Exception) {
        false
      }
    }

    if (semanticSuccess) return true

    // Fallback: Native AWT Robot click
    return try {
      val robot = java.awt.Robot()
      val x = desktopNode.bounds.centerX.toInt()
      val y = desktopNode.bounds.centerY.toInt()

      robot.mouseMove(x, y)
      robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
      Thread.sleep(50)
      robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
      true
    } catch (e: Exception) {
      false
    }
  }

  fun performSetText(
    selector: Selector,
    text: String
  ): Boolean =
    onEdt {
      val node = findResolvedWindowedNode(selector)?.node ?: return@onEdt false
      val action = node.config.getOrNull(SemanticsActions.SetText)?.action ?: return@onEdt false
      action.invoke(AnnotatedString(text))
    }

  fun performScrollBy(
    selector: Selector,
    direction: ScrollDirection,
    amountPx: Float = 200f
  ): Boolean =
    onEdt {
      val node = findResolvedWindowedNode(selector)?.node ?: return@onEdt false
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
        .mapIndexed { index, node ->
          NodeSnapshot(
            tag = node.tag,
            bounds = node.bounds,
            visible = node.visible,
            text = node.text,
            zOrder = index
          )
        }
        .toList()
    }

  fun windowBoundsOnScreen(): Rectangle =
    onEdt {
      val location = primaryWindow.locationOnScreen
      Rectangle(location.x, location.y, primaryWindow.width, primaryWindow.height)
    }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun allNodes(): List<WindowedNode> {
    // Scan all visible ComposeWindow instances so we don't miss Popups/Dialogs
    return Window.getWindows()
      .filterIsInstance<ComposeWindow>()
      .filter { it.isShowing }
      .flatMap { win ->
        win.semanticsOwners.flatMap { owner ->
          val merged = owner.getAllSemanticsNodes(mergingEnabled = true)
          val unmerged = owner.getAllSemanticsNodes(mergingEnabled = false)
          (merged + unmerged).distinctBy { it.id }.map { WindowedNode(win, it) }
        }
      }
  }

  private fun findNodeByTagRaw(tag: String): WindowedNode? {
    val all = allNodes()
    val tagMatches = all.filter { it.node.config.getOrNull(SemanticsProperties.TestTag) == tag }
    tagMatches.firstOrNull { it.toDesktopNode()?.visible == true }?.let { return it }
    tagMatches.firstOrNull()?.let { return it }

    val textMatches = all.filter { winNode ->
      val textList = winNode.node.config.getOrNull(SemanticsProperties.Text)
      val text = textList?.joinToString("") { it.text } 
        ?: winNode.node.config.getOrNull(SemanticsProperties.EditableText)?.text
      text?.contains(tag, ignoreCase = true) == true
    }
    return textMatches.firstOrNull { it.toDesktopNode()?.visible == true } ?: textMatches.firstOrNull()
  }

  @OptIn(ExperimentalComposeUiApi::class)
  private fun WindowedNode.toDesktopNode(): DesktopNode? {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
    val location = window.locationOnScreen
    val nodeBounds = node.boundsInWindow
    val editableText = node.config.getOrNull(SemanticsProperties.EditableText)?.text
    val spokenText =
      node.config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(separator = "") { it.text }
        .orEmpty()
    val invisible = node.config.getOrNull(SemanticsProperties.InvisibleToUser) != null
    val textValue = editableText?.takeIf { it.isNotBlank() } ?: spokenText.ifBlank { null }

    // Include nodes that have either a testTag or text content
    if (tag.isBlank() && textValue == null) return null

    // Determine the actual visible viewport of the Compose content area.
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
