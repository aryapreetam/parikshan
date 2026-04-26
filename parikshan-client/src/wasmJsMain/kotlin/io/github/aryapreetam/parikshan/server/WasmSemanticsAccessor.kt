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
    return findAllNodes().find { node ->
      node.config.getOrNull(SemanticsProperties.TestTag) == tag
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
    val text = textList?.joinToString(" ") { it.text } 
      ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
    
    var bounds = node.boundsInWindow
    if (bounds.width <= 0 || bounds.height <= 0) {
        bounds = node.boundsInRoot
    }

    val hasArea = bounds.width > 0 && bounds.height > 0

    return NodeSnapshot(
      tag = tag,
      text = text,
      visible = hasArea,
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
    val x = if (direction == ScrollDirection.Left) -500f else if (direction == ScrollDirection.Right) 500f else 0f
    val y = if (direction == ScrollDirection.Up) -500f else if (direction == ScrollDirection.Down) 500f else 0f
    return action.action?.invoke(x, y) ?: false
  }
}
