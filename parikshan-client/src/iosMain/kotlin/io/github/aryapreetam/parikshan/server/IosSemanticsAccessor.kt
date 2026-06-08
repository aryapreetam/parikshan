@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Selector
import androidx.compose.ui.text.AnnotatedString
import kotlinx.cinterop.useContents

internal object IosSemanticsAccessor {
  var globalSemanticsOwner: SemanticsOwner? = null

  internal fun injectOwner(owner: Owner) {
    globalSemanticsOwner = owner.semanticsOwner
  }

  fun findAllNodes(): List<SemanticsNode> {
    val owner = globalSemanticsOwner ?: return emptyList()
    val merged = owner.getAllSemanticsNodes(mergingEnabled = true)
    val unmerged = owner.getAllSemanticsNodes(mergingEnabled = false)
    return (merged + unmerged).distinctBy { it.id }
  }

  fun findNodeByTag(tag: String): SemanticsNode? {
    if (tag.isBlank()) return null
    return findBySelector(Selector.Auto(tag))
  }

  fun snapshotNode(tag: String): NodeSnapshot? {
    val node = findNodeByTag(tag) ?: return null
    return toNodeSnapshot(node)
  }

  fun snapshotNode(node: SemanticsNode): NodeSnapshot {
    return toNodeSnapshot(node)
  }

  fun snapshotTree(): List<NodeSnapshot> {
    return findAllNodes().map { toNodeSnapshot(it) }
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

  private fun toNodeSnapshot(node: SemanticsNode): NodeSnapshot {
    val tag = node.getTestTag() ?: ""
    val text = directTextOf(node)
    
    val bounds = node.boundsInWindow
    val hasArea = bounds.width > 0f && bounds.height > 0f

    val screenBounds = platform.UIKit.UIScreen.mainScreen.bounds
    val scale = platform.UIKit.UIScreen.mainScreen.scale.toFloat()
    val physicalScreenWidth = screenBounds.useContents { size.width }.toFloat() * scale
    val physicalScreenHeight = screenBounds.useContents { size.height }.toFloat() * scale

    val centerX = bounds.left + (bounds.width / 2f)
    val centerY = bounds.top + (bounds.height / 2f)

    val isPhysicallyVisible = hasArea && node.layoutInfo.isPlaced &&
      centerX >= 0f && centerX <= physicalScreenWidth &&
      centerY >= 0f && centerY <= physicalScreenHeight

    return NodeSnapshot(
      tag = tag,
      text = text,
      visible = isPhysicallyVisible,
      bounds = Bounds(
        left = bounds.left.toDouble(),
        top = bounds.top.toDouble(),
        right = bounds.right.toDouble(),
        bottom = bounds.bottom.toDouble()
      )
    )
  }

  private data class SelectorCandidate(
    val node: SemanticsNode,
    val score: Int,
    val area: Float,
    val depth: Int
  )

  fun findBySelector(selector: Selector): SemanticsNode? {
    val candidates = selectorCandidates(selector)
    if (candidates.isEmpty()) return null
    
    val targetIndex = when {
      selector.index != null && selector.index!! >= 0 -> selector.index!!
      selector.index != null && selector.index!! < 0 -> candidates.size + selector.index!!
      else -> 0
    }
    return candidates.getOrNull(targetIndex)?.node
  }

  fun findNode(tag: String, selector: Selector?): SemanticsNode? {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) }
    return activeSelector?.let { findBySelector(it) }
  }

  private fun selectorCandidates(selector: Selector): List<SelectorCandidate> {
    if (selector.raw.isBlank()) return emptyList()

    return selectorSearchNodes()
      .asSequence()
      .filter { hasArea(it) }
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

  private fun selectorSearchNodes(): List<SemanticsNode> {
    return findAllNodes()
  }

  private fun selectorScore(
    node: SemanticsNode,
    selector: Selector
  ): Int? {
    val raw = selector.raw.trim()
    if (raw.isEmpty()) return null

    val tag = node.getTestTag()?.trim()
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

  private fun directTextOf(node: SemanticsNode): String? {
    node.getEditableText()
      ?.takeIf { it.isNotBlank() }
      ?.let { return it }

    val values = node.getTextList().orEmpty()
    if (values.isNotEmpty()) {
      return values.joinToString("") { it.text }.takeIf { it.isNotBlank() }
    }

    val contentDescription = node.getContentDescription().orEmpty()
    if (contentDescription.isNotEmpty()) {
      return contentDescription.joinToString("").takeIf { it.isNotBlank() }
    }
    return null
  }

  private fun hasArea(node: SemanticsNode): Boolean {
    val bounds = node.boundsInWindow
    return bounds.width > 0f && bounds.height > 0f
  }

  private fun nodeArea(node: SemanticsNode): Float {
    val bounds = node.boundsInWindow
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
    var current: SemanticsNode? = node
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
      val node = all[i]
      val tag = node.getTestTag()
      val text = directTextOf(node)
      val bounds = node.boundsInWindow
      val area = nodeArea(node)
      val depth = nodeDepth(node)
      val score = selectorScore(node, selector) ?: -1
      val placed = node.layoutInfo.isPlaced
      sb.append("  [").append(i).append("] id=").append(node.id)
        .append(" tag='").append(tag).append("'")
        .append(" text='").append(text).append("'")
        .append(" bounds=(L:").append(bounds.left).append(", T:").append(bounds.top)
        .append(", R:").append(bounds.right).append(", B:").append(bounds.bottom).append(")")
        .append(" area=").append(area).append(" depth=").append(depth)
        .append(" score=").append(score).append(" placed=").append(placed).append("\n")
    }
    return sb.toString()
  }

  fun performClickResult(tag: String, selector: Selector?): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    val node = findNode(tag, selector) ?: return formatNodeDiagnostics(activeSelector)
    val target = clickTargetFor(node) ?: return "Click target not found"
    val action = target.getAction<() -> Boolean>("OnClick") ?: return "OnClick action not found on target"
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
    
    val bounds = target.boundsInWindow
    val width = bounds.right - bounds.left
    val height = bounds.bottom - bounds.top
    val safeWidth = if (width > 0) width else 400f
    val safeHeight = if (height > 0) height else 400f
    
    val deltaX = safeWidth * 0.5f
    val deltaY = safeHeight * 0.5f
    
    val x = if (direction == ScrollDirection.Left) -deltaX else if (direction == ScrollDirection.Right) deltaX else 0f
    val y = if (direction == ScrollDirection.Up) -deltaY else if (direction == ScrollDirection.Down) deltaY else 0f
    
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
}
