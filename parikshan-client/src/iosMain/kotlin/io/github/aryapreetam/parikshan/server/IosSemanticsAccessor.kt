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
import io.github.aryapreetam.parikshan.protocol.Selector
import androidx.compose.ui.text.AnnotatedString

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

  private fun toNodeSnapshot(node: SemanticsNode): NodeSnapshot {
    val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
    val textList = node.config.getOrNull(SemanticsProperties.Text)
    val text = textList?.joinToString("") { it.text } 
      ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
    
    val bounds = node.boundsInWindow
    val hasArea = bounds.width > 0f && bounds.height > 0f

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

  private data class SelectorCandidate(
    val node: SemanticsNode,
    val score: Int,
    val area: Float,
    val depth: Int
  )

  fun findBySelector(selector: Selector): SemanticsNode? =
    selectorCandidates(selector).firstOrNull()?.node

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
    val owner = globalSemanticsOwner ?: return emptyList()
    val unmerged = owner.getAllSemanticsNodes(mergingEnabled = false)
    val merged = owner.getAllSemanticsNodes(mergingEnabled = true)
    return (unmerged + merged).distinctBy { it.id }
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

  private fun directTextOf(node: SemanticsNode): String? {
    node.config.getOrNull(SemanticsProperties.EditableText)?.text
      ?.takeIf { it.isNotBlank() }
      ?.let { return it }

    val values = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
    if (values.isNotEmpty()) {
      return values.joinToString("") { it.text }.takeIf { it.isNotBlank() }
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
      if (current.config.getOrNull(SemanticsActions.OnClick) != null) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun inputTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.SetText) != null) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun scrollTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.ScrollBy) != null) {
        return current
      }
      current = current.parent
    }
    return null
  }

  fun performClick(tag: String, selector: Selector?): Boolean {
    val node = findNode(tag, selector) ?: return false
    val target = clickTargetFor(node) ?: return false
    val action = target.config.getOrNull(SemanticsActions.OnClick) ?: return false
    return action.action?.invoke() ?: false
  }

  fun performInput(tag: String, selector: Selector?, text: String): Boolean {
    val node = findNode(tag, selector) ?: return false
    val target = inputTargetFor(node) ?: return false
    val action = target.config.getOrNull(SemanticsActions.SetText) ?: return false
    return action.action?.invoke(AnnotatedString(text)) ?: false
  }

  fun performScroll(tag: String, selector: Selector?, direction: ScrollDirection): Boolean {
    val node = findNode(tag, selector) ?: return false
    val target = scrollTargetFor(node) ?: return false
    val action = target.config.getOrNull(SemanticsActions.ScrollBy) ?: return false
    val x = if (direction == ScrollDirection.Left) -200f else if (direction == ScrollDirection.Right) 200f else 0f
    val y = if (direction == ScrollDirection.Up) -200f else if (direction == ScrollDirection.Down) 200f else 0f
    return action.action?.invoke(x, y) ?: false
  }
}
