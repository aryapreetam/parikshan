@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.scene.ComposeScene
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.resolveNode
import androidx.compose.ui.text.AnnotatedString
import kotlinx.cinterop.useContents

internal object IosSemanticsAccessor {

  var currentScene: ComposeScene? = null

  fun setup() {
      ComposeRootRegistry.clear()
  }

  fun findAllNodes(): List<Pair<SemanticsNode, Int>> {
    val roots = ComposeRootRegistry.roots
    val result = mutableListOf<Pair<SemanticsNode, Int>>()
    for ((index, root) in roots.withIndex()) {
      // Force layout to ensure bounds are up to date
      try {
          (root as? androidx.compose.ui.node.RootNodeOwner)?.measureAndLayout()
      } catch (e: Throwable) {
          // Ignore if cast or call fails
      }
      val owner = root.semanticsOwner
      val merged = owner.getAllSemanticsNodes(mergingEnabled = true)
      val unmerged = owner.getAllSemanticsNodes(mergingEnabled = false)
      val all = (merged + unmerged).distinctBy { it.id }
      for (node in all) {
        result.add(node to index)
      }
    }
    return result
  }

  fun findNodeByTag(tag: String): SemanticsNode? {
    if (tag.isBlank()) return null
    return findBySelector(Selector.Auto(tag))
  }

  fun snapshotNode(tag: String): NodeSnapshot? {
    val node = findNodeByTag(tag) ?: return null
    return toNodeSnapshot(node, 0)
  }

  fun snapshotNode(node: SemanticsNode): NodeSnapshot {
    return toNodeSnapshot(node, 0)
  }

  fun snapshotTree(): List<NodeSnapshot> {
    return findAllNodes().map { toNodeSnapshot(it.first, it.second) }
  }

  private fun SemanticsNode.hasAction(name: String): Boolean {
    return config.any { it.key.name == name }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> SemanticsNode.getAction(name: String): T? {
    val entry = config.firstOrNull { it.key.name == name } ?: return null
    val accessibilityAction = entry.value as? androidx.compose.ui.semantics.AccessibilityAction<*> ?: return null
    return accessibilityAction.action as? T
  }

  private fun SemanticsNode.getTestTag(): String? {
    val entry = config.firstOrNull { it.key.name == "TestTag" } ?: return null
    return entry.value as? String
  }

  private fun SemanticsNode.getEditableText(): String? {
    val entry = config.firstOrNull { it.key.name == "EditableText" } ?: return null
    return (entry.value as? AnnotatedString)?.text
  }

  @Suppress("UNCHECKED_CAST")
  private fun SemanticsNode.getTextList(): List<AnnotatedString>? {
    val entry = config.firstOrNull { it.key.name == "Text" } ?: return null
    return entry.value as? List<AnnotatedString>
  }

  @Suppress("UNCHECKED_CAST")
  private fun SemanticsNode.getContentDescription(): List<String>? {
    val entry = config.firstOrNull { it.key.name == "ContentDescription" } ?: return null
    return entry.value as? List<String>
  }

  private fun directTextOf(node: SemanticsNode): String? {
    // Priority 1: Editable text (actual input value)
    val editable = node.getEditableText()
    if (editable != null) return editable

    // Priority 2: Combined Text and Content Description for other nodes
    val result = mutableListOf<String>()
    val values = node.getTextList().orEmpty()
    for (v in values) {
        if (v.isNotBlank()) result.add(v.text)
    }

    val contentDescription = node.getContentDescription().orEmpty()
    for (cd in contentDescription) {
        if (cd.isNotBlank()) result.add(cd)
    }

    return if (result.isEmpty()) null else result.joinToString(" ")
  }

  private fun toNodeSnapshot(node: SemanticsNode, zOrder: Int): NodeSnapshot {
    val tag = node.getTestTag() ?: ""
    val text = directTextOf(node)
    
    // Try boundsInWindow first, then fallback to boundsInRoot
    var bounds = node.boundsInWindow
    if (bounds.width <= 0f || bounds.height <= 0f) {
        bounds = node.boundsInRoot
    }
    
    val hasArea = bounds.width > 0f && bounds.height > 0f
    val isPlaced = node.layoutInfo.isPlaced
    
    val screenBounds = platform.UIKit.UIScreen.mainScreen.bounds
    val scale = platform.UIKit.UIScreen.mainScreen.scale.toFloat()
    val physicalScreenWidth = screenBounds.useContents<platform.CoreGraphics.CGRect, Double> { size.width }.toFloat() * scale
    val physicalScreenHeight = screenBounds.useContents<platform.CoreGraphics.CGRect, Double> { size.height }.toFloat() * scale

    // Lenient physical visibility: allow nodes partially off-screen
    val isPhysicallyVisible = hasArea && isPlaced &&
      bounds.right > 0f && bounds.left < physicalScreenWidth &&
      bounds.bottom > 0f && bounds.top < physicalScreenHeight

    return NodeSnapshot(
      tag = tag,
      text = text,
      visible = isPhysicallyVisible,
      bounds = Bounds(
        left = bounds.left.toDouble(),
        top = bounds.top.toDouble(),
        right = bounds.right.toDouble(),
        bottom = bounds.bottom.toDouble()
      ),
      zOrder = zOrder
    )
  }

  fun findBySelector(selector: Selector): SemanticsNode? {
    val nodesWithZOrder = findAllNodes()
    if (nodesWithZOrder.isEmpty()) return null

    val snapshots = nodesWithZOrder.map { toNodeSnapshot(it.first, it.second) }
    
    val resolved = try {
      selector.resolveNode(snapshots, requireVisible = false)
    } catch (e: Throwable) {
      return null
    }

    val matchedSnapshot = resolved.node
    val matchedIndex = snapshots.indexOf(matchedSnapshot)
    if (matchedIndex >= 0 && matchedIndex < nodesWithZOrder.size) {
        return nodesWithZOrder[matchedIndex].first
    }
    return null
  }

  fun findNode(tag: String, selector: Selector?): SemanticsNode? {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) }
    return activeSelector?.let { findBySelector(it) }
  }

  private fun clickTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.hasAction("OnClick")) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun inputTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.hasAction("SetText")) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun scrollTargetFor(node: SemanticsNode): SemanticsNode? {
    // 1. Check the node itself
    if (node.hasAction("ScrollBy")) return node

    // 2. Check immediate children (shallow search for internal scrollable components like ScrollableTabRow)
    val children = node.children
    for (child in children) {
        if (child.hasAction("ScrollBy")) return child
    }

    // 3. Walk up the tree to find a scrollable container
    var current: SemanticsNode? = node.parent
    while (current != null) {
      if (current.hasAction("ScrollBy")) {
        return current
      }
      current = current.parent
    }
    return null
  }

  fun formatNodeDiagnostics(selector: Selector): String {
    val all = findAllNodes()
    val sb = StringBuilder()
    sb.append("Node not found for selector: ").append(selector).append("\n")
    sb.append("Total nodes in tree: ").append(all.size).append("\n")
    for (i in 0 until all.size) {
      val pair = all[i]
      val node = pair.first
      val tag = node.getTestTag()
      val text = directTextOf(node)
      val bounds = node.boundsInWindow
      val placed = node.layoutInfo.isPlaced
      val actions = node.config.map { it.key.name }.joinToString(",")
      sb.append("  [").append(i).append("] id=").append(node.id)
        .append(" tag='").append(tag).append("'")
        .append(" text='").append(text).append("'")
        .append(" actions=[").append(actions).append("]")
        .append(" bounds=(L:").append(bounds.left).append(", T:").append(bounds.top)
        .append(", R:").append(bounds.right).append(", B:").append(bounds.bottom).append(")")
        .append(" zOrder=").append(pair.second).append(" placed=").append(placed).append("\n")
    }
    return sb.toString()
  }

  fun performClickResult(tag: String, selector: Selector?): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    val node = findNode(tag, selector) ?: return formatNodeDiagnostics(activeSelector)
    val target = clickTargetFor(node) ?: return "Click target not found for selector $activeSelector. ${formatNodeDiagnostics(activeSelector)}"
    val action = target.getAction<() -> Boolean>("OnClick") ?: return "OnClick action not found on target. ${formatNodeDiagnostics(activeSelector)}"
    val success = action.invoke()
    return if (success) "OK" else "OnClick action invoke returned false"
  }

  fun performInputResult(tag: String, selector: Selector?, text: String): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    val node = findNode(tag, selector) ?: return formatNodeDiagnostics(activeSelector)
    val target = inputTargetFor(node) ?: return "Input target not found"
    val action = target.getAction<(AnnotatedString) -> Boolean>("SetText") ?: return "SetText action not found on target"
    val success = action.invoke(AnnotatedString(text))
    return if (success) "OK" else "SetText action invoke returned false"
  }

  fun performScrollResult(tag: String, selector: Selector?, direction: ScrollDirection): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    val node = findNode(tag, selector) ?: return formatNodeDiagnostics(activeSelector)
    val target = scrollTargetFor(node) ?: return "Scroll target not found"
    val action = target.getAction<(Float, Float) -> Boolean>("ScrollBy") ?: return "ScrollBy action not found"
    val x = if (direction == ScrollDirection.Left) -800f else if (direction == ScrollDirection.Right) 800f else 0f
    val y = if (direction == ScrollDirection.Up) -800f else if (direction == ScrollDirection.Down) 800f else 0f
    val success = action.invoke(x, y)
    return if (success) "OK" else "ScrollBy action invoke returned false"
  }

  fun performClick(tag: String, selector: Selector?): Boolean {
    return performClickResult(tag, selector) == "OK"
  }

  fun performInput(tag: String, selector: Selector?, text: String): Boolean {
    return performInputResult(tag, selector, text) == "OK"
  }

  fun performScroll(tag: String, selector: Selector?, direction: ScrollDirection): Boolean {
    return performScrollResult(tag, selector, direction) == "OK"
  }

  fun performDrag(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMs: Long): String {
      val scene = currentScene ?: return "Drag failed: No ComposeScene found"
      val from = Offset(fromX.toFloat(), fromY.toFloat())
      val to = Offset(toX.toFloat(), toY.toFloat())
      val id = androidx.compose.ui.input.pointer.PointerId(999L)
      
      scene.sendPointerEvent(
          eventType = PointerEventType.Press,
          pointers = listOf(androidx.compose.ui.scene.ComposeScenePointer(id, from, true, PointerType.Touch))
      )
      val steps = (durationMs / 16).coerceAtLeast(1).toInt()
      for (i in 1..steps) {
          val fraction = i.toFloat() / steps
          val curX = from.x + (to.x - from.x) * fraction
          val curY = from.y + (to.y - from.y) * fraction
          scene.sendPointerEvent(
              eventType = PointerEventType.Move,
              pointers = listOf(androidx.compose.ui.scene.ComposeScenePointer(id, Offset(curX, curY), true, PointerType.Touch))
          )
      }
      scene.sendPointerEvent(
          eventType = PointerEventType.Release,
          pointers = listOf(androidx.compose.ui.scene.ComposeScenePointer(id, to, false, PointerType.Touch))
      )
      return "OK"
  }
}
