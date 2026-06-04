@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
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
import kotlinx.browser.window
import androidx.compose.ui.text.AnnotatedString

internal object WasmSemanticsAccessor {
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

  fun findNodeByTag(tag: String): SemanticsNode? {
    val all = findAllNodes()
    all.find { it.config.getOrNull(SemanticsProperties.TestTag) == tag }?.let { return it }
    return all.find { node ->
      directTextOf(node)?.contains(tag, ignoreCase = true) == true
    }
  }

  fun snapshotNode(tag: String): NodeSnapshot? {
    val node = findNodeByTag(tag) ?: return null
    return toNodeSnapshot(node)
  }

  fun snapshotTree(): List<NodeSnapshot> {
    return findAllNodes().map { toNodeSnapshot(it) }
  }

  private fun toNodeSnapshot(node: SemanticsNode): NodeSnapshot {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
    val text = directTextOf(node)
    
    val bounds = node.boundsInWindow
    val hasArea = bounds.width > 0f && bounds.height > 0f

    // In WASM, we use the actual browser viewport dimensions to verify physical visibility.
    // Compose's boundsInWindow in WASM are relative to the browser window.
    val viewportWidth = kotlinx.browser.window.innerWidth
    val viewportHeight = kotlinx.browser.window.innerHeight
    
    val centerX = bounds.left + (bounds.width / 2f)
    val centerY = bounds.top + (bounds.height / 2f)
    val isPhysicallyVisible = hasArea &&
      centerX >= 0 && centerX <= viewportWidth &&
      centerY >= 0 && centerY <= viewportHeight

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

  fun findBySelector(selector: io.github.aryapreetam.parikshan.protocol.Selector): SemanticsNode? {
    val all = findAllNodes()
    val candidates = when (selector) {
      is io.github.aryapreetam.parikshan.protocol.Selector.Tag -> all.filter {
        it.config.getOrNull(SemanticsProperties.TestTag) == selector.value
      }
      is io.github.aryapreetam.parikshan.protocol.Selector.Text -> all.filter { node ->
        directTextOf(node)?.contains(selector.value, ignoreCase = true) == true
      }
      is io.github.aryapreetam.parikshan.protocol.Selector.Auto -> {
        val tagMatches = all.filter { it.config.getOrNull(SemanticsProperties.TestTag) == selector.raw }
        if (tagMatches.isNotEmpty()) tagMatches else all.filter { node ->
          directTextOf(node)?.contains(selector.raw, ignoreCase = true) == true
        }
      }
    }

    if (candidates.isEmpty()) return null
    val targetIndex = when {
      selector.index != null && selector.index!! >= 0 -> selector.index!!
      selector.index != null && selector.index!! < 0 -> candidates.size + selector.index!!
      else -> 0
    }
    return candidates.getOrNull(targetIndex)
  }

  private fun clickTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.OnClick) != null) return current
      current = current.parent
    }
    return null
  }

  private fun inputTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.SetText) != null) return current
      current = current.parent
    }
    return null
  }

  private fun scrollTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.ScrollBy) != null) return current
      current = current.parent
    }
    return null
  }

  fun performClick(selector: io.github.aryapreetam.parikshan.protocol.Selector): Boolean {
    val node = findBySelector(selector) ?: return false
    val target = clickTargetFor(node) ?: return false
    val action = target.config.getOrNull(SemanticsActions.OnClick) ?: return false
    return action.action?.invoke() ?: false
  }

  fun performInput(selector: io.github.aryapreetam.parikshan.protocol.Selector, text: String): Boolean {
    val node = findBySelector(selector) ?: return false
    val target = inputTargetFor(node) ?: return false
    val action = target.config.getOrNull(SemanticsActions.SetText) ?: return false
    return action.action?.invoke(AnnotatedString(text)) ?: false
  }

  fun performScroll(selector: io.github.aryapreetam.parikshan.protocol.Selector, direction: ScrollDirection): Boolean {
    val node = findBySelector(selector) ?: return false
    val target = scrollTargetFor(node) ?: node
    val action = target.config.getOrNull(SemanticsActions.ScrollBy) ?: return false
    val x = if (direction == ScrollDirection.Left) -1000f else if (direction == ScrollDirection.Right) 1000f else 0f
    val y = if (direction == ScrollDirection.Up) -1000f else if (direction == ScrollDirection.Down) 1000f else 0f
    return action.action?.invoke(x, y) ?: false
  }
}
