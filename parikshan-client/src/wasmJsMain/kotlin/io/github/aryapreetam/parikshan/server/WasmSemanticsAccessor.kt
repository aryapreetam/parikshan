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

  fun findNodeByTag(tag: String): SemanticsNode? {
    val all = findAllNodes()
    all.find { it.config.getOrNull(SemanticsProperties.TestTag) == tag }?.let { return it }
    return all.find { node ->
      val textList = node.config.getOrNull(SemanticsProperties.Text)
      val text = textList?.joinToString("") { it.text } 
        ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
      text?.contains(tag, ignoreCase = true) == true
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
    val textList = node.config.getOrNull(SemanticsProperties.Text)
    val text = textList?.joinToString("") { it.text } 
      ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
    
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

  fun performClick(tag: String): Boolean {
    val node = findNodeByTag(tag) ?: return false
    val action = node.config.getOrNull(SemanticsActions.OnClick) ?: return false
    return action.action?.invoke() ?: false
  }

  fun performInput(tag: String, text: String): Boolean {
    val node = findNodeByTag(tag) ?: return false
    val action = node.config.getOrNull(SemanticsActions.SetText) ?: return false
    return action.action?.invoke(androidx.compose.ui.text.AnnotatedString(text)) ?: false
  }

  fun performScroll(tag: String, direction: ScrollDirection): Boolean {
    val node = findNodeByTag(tag) ?: return false
    val action = node.config.getOrNull(SemanticsActions.ScrollBy) ?: return false
    val x = if (direction == ScrollDirection.Left) -1000f else if (direction == ScrollDirection.Right) 1000f else 0f
    val y = if (direction == ScrollDirection.Up) -1000f else if (direction == ScrollDirection.Down) 1000f else 0f
    return action.action?.invoke(x, y) ?: false
  }
}
