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
import java.awt.event.MouseEvent
import java.awt.event.InputEvent
import javax.swing.SwingUtilities
import kotlinx.coroutines.delay


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
    val semanticSuccess = onEdt {
      val windowedNode = findResolvedWindowedNode(selector)
      var currentNode: SemanticsNode? = windowedNode?.node ?: return@onEdt false
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

      val actionToInvoke = bestAction ?: textFieldAction
      if (actionToInvoke != null) {
          System.err.println("Parikshan: performClick - Found semantic OnClick for '${selector.raw}'. Invoking...")
          try {
            val result = actionToInvoke.invoke()
            System.err.println("Parikshan: performClick - Semantic invocation result: $result")
            result
          } catch (e: Exception) {
            System.err.println("Parikshan: performClick - Semantic OnClick FAILED: ${e.message}")
            false
          }
      } else {
          System.err.println("Parikshan: performClick - No semantic OnClick found for '${selector.raw}'. Falling back to Robot.")
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

  private fun findInputTarget(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.SetText) != null) {
        return current
      }
      current = current.parent
    }

    current = node.parent
    while (current != null) {
      val target = findInputTargetInSubtree(current, exclude = node)
      if (target != null) {
        return target
      }
      current = current.parent
    }
    return null
  }

  private fun findInputTargetInSubtree(node: SemanticsNode, exclude: SemanticsNode? = null): SemanticsNode? {
    if (node === exclude) return null
    if (node.config.getOrNull(SemanticsActions.SetText) != null) {
      return node
    }
    for (child in node.children) {
      val target = findInputTargetInSubtree(child, exclude)
      if (target != null) {
        return target
      }
    }
    return null
  }

  fun performSetText(
    selector: Selector,
    text: String
  ): Boolean =
    onEdt {
      val node = findResolvedWindowedNode(selector)?.node ?: return@onEdt false
      val target = findInputTarget(node) ?: return@onEdt false
      val action = target.config.getOrNull(SemanticsActions.SetText)?.action ?: return@onEdt false
      action.invoke(AnnotatedString(text))
    }

  fun performScrollBy(
    selector: Selector,
    direction: ScrollDirection,
    amountPx: Float = 200f
  ): Boolean {
    var success = false
    System.err.println("Parikshan Server: performScrollBy start for selector='${selector.raw}' amountPx=$amountPx")
    
    onEdt {
      val resolvedNode = findResolvedWindowedNode(selector)
      if (resolvedNode == null) {
        System.err.println("Parikshan Server: performScrollBy - resolvedNode is null for selector='${selector.raw}'")
        return@onEdt
      }
      
      val action = findScrollAction(resolvedNode.node)
      if (action != null) {
        val (deltaX, deltaY) = when (direction) {
          ScrollDirection.Up -> 0f to -amountPx
          ScrollDirection.Down -> 0f to amountPx
          ScrollDirection.Left -> -amountPx to 0f
          ScrollDirection.Right -> amountPx to 0f
        }
        try {
          success = action.invoke(deltaX, deltaY)
          System.err.println("Parikshan Server: performScrollBy - action.invoke returned success=$success")
        } catch (e: Exception) {
          System.err.println("Parikshan Server: performScrollBy - action.invoke failed with exception: ${e.message}")
        }
      } else {
        System.err.println("Parikshan Server: performScrollBy - No SemanticsActions.ScrollBy action found in hierarchy of selector='${selector.raw}'")
      }
    }
    
    // Yield the EDT queue sequentially to allow the launched coroutine and subsequent layout passes to complete
    if (success) {
      repeat(3) {
        onEdt {
          // yield EDT event loop cycle
        }
      }
    }
    
    System.err.println("Parikshan Server: performScrollBy end, returning success=$success")
    return success
  }

  private fun findScrollAction(node: SemanticsNode): ((Float, Float) -> Boolean)? {
    var currentNode: SemanticsNode? = node
    while (currentNode != null) {
      val action = currentNode.config.getOrNull(SemanticsActions.ScrollBy)?.action
      if (action != null) return action
      currentNode = currentNode.parent
    }

    val queue = ArrayDeque<SemanticsNode>()
    queue.addAll(node.children)
    while (queue.isNotEmpty()) {
      val child = queue.removeFirst()
      val action = child.config.getOrNull(SemanticsActions.ScrollBy)?.action
      if (action != null) return action
      queue.addAll(child.children)
    }

    return null
  }

  suspend fun performDrag(
    fromX: Double,
    fromY: Double,
    toX: Double,
    toY: Double,
    durationMs: Long
  ): Boolean {
    val targetComponent = onEdt {
      val wl = primaryWindow.locationOnScreen
      val winStartX = (fromX - wl.x).toInt()
      val winStartY = (fromY - wl.y).toInt()
      primaryWindow.findComponentAt(winStartX, winStartY) ?: primaryWindow.contentPane
    }

    val compLoc = onEdt { targetComponent.locationOnScreen }
    val startX = (fromX - compLoc.x).toInt()
    val startY = (fromY - compLoc.y).toInt()
    val endX = (toX - compLoc.x).toInt()
    val endY = (toY - compLoc.y).toInt()


    onEdt {
      targetComponent.dispatchEvent(MouseEvent(targetComponent, MouseEvent.MOUSE_ENTERED, System.currentTimeMillis(), 0, startX, startY, fromX.toInt(), fromY.toInt(), 0, false, MouseEvent.NOBUTTON))
      targetComponent.dispatchEvent(MouseEvent(targetComponent, MouseEvent.MOUSE_MOVED, System.currentTimeMillis(), 0, startX, startY, fromX.toInt(), fromY.toInt(), 0, false, MouseEvent.NOBUTTON))
      targetComponent.dispatchEvent(MouseEvent(targetComponent, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, startX, startY, fromX.toInt(), fromY.toInt(), 1, false, MouseEvent.BUTTON1))
    }

    // Small delay to ensure "drag" is registered
    delay(50)

    val steps = 30
    val stepDelay = (durationMs / steps).coerceAtLeast(1L)
    
    for (i in 1..steps) {
      val progress = i.toFloat() / steps
      val curXRel = startX + (endX - startX) * progress
      val curYRel = startY + (endY - startY) * progress
      val curXAbs = fromX + (toX - fromX) * progress
      val curYAbs = fromY + (toY - fromY) * progress
      
      onEdt {
        targetComponent.dispatchEvent(MouseEvent(targetComponent, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, curXRel.toInt(), curYRel.toInt(), curXAbs.toInt(), curYAbs.toInt(), 0, false, MouseEvent.NOBUTTON))
      }
      
      delay(stepDelay)
    }

    onEdt {
      targetComponent.dispatchEvent(MouseEvent(targetComponent, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, endX, endY, toX.toInt(), toY.toInt(), 1, false, MouseEvent.BUTTON1))
    }
    
    return true
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
    // Get all windows and sort them to simulate z-order.
    // Overlays/Dialogs should be processed last so their nodes are at the end of the list.
    val windows = Window.getWindows()
      .filter { it.isShowing && it.isDisplayable }
      .filterIsInstance<ComposeWindow>()
      .sortedWith(
          compareBy<ComposeWindow> { it === primaryWindow } // Primary first
          .thenBy { it.type == Window.Type.NORMAL }         // Then normal windows
          .thenBy { !it.isAlwaysOnTop }                    // Then non-always-on-top
      )

    return windows.flatMap { win ->
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
    
    // Calculate actual screen origin of the Compose content area (accounting for title bar and window insets).
    val canvasLoc = runCatching { window.contentPane.locationOnScreen }.getOrNull()
    val winX = canvasLoc?.x ?: (window.bounds.x + window.insets.left)
    val winY = canvasLoc?.y ?: (window.bounds.y + window.insets.top)
    val nodeBounds = node.boundsInWindow
    
    val editableText = node.config.getOrNull(SemanticsProperties.EditableText)?.text
    val spokenText =
      node.config.getOrNull(SemanticsProperties.Text)
        ?.joinToString(separator = "") { it.text }
        .orEmpty()
    val contentDescription =
      node.config.getOrNull(SemanticsProperties.ContentDescription)
        ?.joinToString(separator = " ")
        .orEmpty()
    
    // Prioritize Compose-reported visibility
    val isHidden = node.config.getOrNull(SemanticsProperties.InvisibleToUser) != null ||
                   node.config.getOrNull(SemanticsProperties.HideFromAccessibility) != null

    val textValue = editableText?.takeIf { it.isNotBlank() }
      ?: when {
        contentDescription.isNotBlank() && spokenText.isNotBlank() -> "$contentDescription $spokenText"
        contentDescription.isNotBlank() -> contentDescription
        spokenText.isNotBlank() -> spokenText
        else -> null
      }

    // Include nodes that have either a testTag or text content
    if (tag.isBlank() && textValue == null) return null

    // Determine the actual visible viewport of the Compose content area.
    val density = window.graphicsConfiguration.defaultTransform.scaleX.toFloat()
    val contentBounds = window.contentPane.bounds
    
    val left = nodeBounds.left / density
    val right = nodeBounds.right / density
    val top = nodeBounds.top / density
    val bottom = nodeBounds.bottom / density

    val isPhysicallyVisible = 
      left < contentBounds.width &&
      right > 0 &&
      top < contentBounds.height &&
      bottom > 0

    return DesktopNode(
      tag = tag,
      bounds =
        Bounds(
          left = winX + left.toDouble(),
          top = winY + top.toDouble(),
          right = winX + right.toDouble(),
          bottom = winY + bottom.toDouble()
        ),
      visible = !isHidden && isPhysicallyVisible,
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
