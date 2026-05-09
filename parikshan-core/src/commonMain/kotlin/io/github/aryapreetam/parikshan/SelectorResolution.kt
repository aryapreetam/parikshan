package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Selector

data class ResolvedSelector(
  val selector: Selector,
  val matchType: MatchType,
  val node: NodeSnapshot,
  val allMatches: List<NodeSnapshot> = listOf(node)
) {
  val tag: String = node.tag.ifEmpty { node.text ?: selector.raw }

  enum class MatchType {
    Tag,
    Text
  }
}

fun Selector.resolveNode(
  nodes: List<NodeSnapshot>,
  requireVisible: Boolean = true
): ResolvedSelector {
  return when (this) {
    is Selector.Auto -> resolveAuto(nodes = nodes, requireVisible = requireVisible)
    is Selector.Tag -> resolveTag(nodes = nodes, requireVisible = requireVisible)
    is Selector.Text -> resolveText(nodes = nodes, requireVisible = requireVisible)
  }
}

internal fun String.asAutoSelector(): Selector = Selector.Auto(this)

fun Selector.ambiguousTextMessage(matches: List<NodeSnapshot>): String {
  val matchSummary =
    matches.joinToString(separator = ", ") { node ->
      "tag='${node.tag}', text='${node.text}', bounds=${node.bounds}"
    }
  return "Selector ${describe()} matched multiple visible text nodes: $matchSummary. Use a stable tag or an explicit selector."
}

internal fun Selector.Auto.resolveAuto(
  nodes: List<NodeSnapshot>,
  requireVisible: Boolean
): ResolvedSelector {
  val tagMatches = matchingTagNodes(nodes)
  if (tagMatches.isNotEmpty()) {
    return resolveSingleTagMatch(
      tagMatches = tagMatches,
      requireVisible = requireVisible,
      selector = this
    )
  }
  return resolveByText(
    selector = this,
    nodes = nodes,
    requireVisible = requireVisible
  )
}

internal fun Selector.Tag.resolveTag(
  nodes: List<NodeSnapshot>,
  requireVisible: Boolean
): ResolvedSelector =
  resolveSingleTagMatch(
    tagMatches = matchingTagNodes(nodes),
    requireVisible = requireVisible,
    selector = this
  )

internal fun Selector.Text.resolveText(
  nodes: List<NodeSnapshot>,
  requireVisible: Boolean
): ResolvedSelector =
  resolveByText(
    selector = this,
    nodes = nodes,
    requireVisible = requireVisible
  )

private fun Selector.matchingTagNodes(nodes: List<NodeSnapshot>): List<NodeSnapshot> {
  val normalized = normalizedRaw()
  return nodes.filter { it.tag == normalized }
}

private fun matchingTextNodes(
  raw: String,
  nodes: List<NodeSnapshot>,
  requireVisible: Boolean
): List<NodeSnapshot> {
  val normalized = raw.trim()
  val allMatches = nodes.filter { node ->
    (!requireVisible || node.visible) && node.normalizedText()?.contains(normalized, ignoreCase = true) == true
  }

  val exactMatches = allMatches.filter { it.normalizedText().equals(normalized, ignoreCase = true) }
  val matchesToUse = if (exactMatches.isNotEmpty()) exactMatches else allMatches

  val tagged = matchesToUse.filter { it.tag.isNotEmpty() }
  val filtered = if (tagged.isNotEmpty() && tagged.size < matchesToUse.size) tagged else matchesToUse

  return filtered.groupBy { it.tag }.flatMap { (tag, group) ->
    if (tag.isEmpty()) {
      val deduplicated = mutableListOf<NodeSnapshot>()
      for (node in group) {
        var isDuplicate = false
        val iterator = deduplicated.iterator()
        while (iterator.hasNext()) {
          val existing = iterator.next()
          if (node.text == existing.text) {
             if (existing.bounds.left <= node.bounds.left + 1.0 && existing.bounds.top <= node.bounds.top + 1.0 && existing.bounds.right + 1.0 >= node.bounds.right && existing.bounds.bottom + 1.0 >= node.bounds.bottom) {
                 isDuplicate = true
                 break
             } else if (node.bounds.left <= existing.bounds.left + 1.0 && node.bounds.top <= existing.bounds.top + 1.0 && node.bounds.right + 1.0 >= existing.bounds.right && node.bounds.bottom + 1.0 >= existing.bounds.bottom) {
                 iterator.remove()
             }
          }
        }
        if (!isDuplicate) deduplicated.add(node)
      }
      deduplicated
    } else {
      listOf(group.first())
    }
  }
}

private fun resolveByText(
  selector: Selector,
  nodes: List<NodeSnapshot>,
  requireVisible: Boolean
): ResolvedSelector {
  val matches =
    matchingTextNodes(
      raw = selector.raw,
      nodes = nodes,
      requireVisible = requireVisible
    )
  if (matches.isEmpty()) throw SelectorResolutionException(selector.textNotFoundMessage())

  return ResolvedSelector(
    selector = selector,
    matchType = ResolvedSelector.MatchType.Text,
    node = matches.first(),
    allMatches = matches
  )
}

private fun Selector.resolveSingleTagMatch(
  tagMatches: List<NodeSnapshot>,
  requireVisible: Boolean,
  selector: Selector
): ResolvedSelector {
  val match =
    if (requireVisible) {
      tagMatches.firstOrNull { it.visible } ?: tagMatches.firstOrNull()
    } else {
      tagMatches.firstOrNull()
    }
      ?: throw SelectorResolutionException(tagNotFoundMessage(selector))
  if (requireVisible && !match.visible) {
    throw SelectorResolutionException(tagNotVisibleMessage(selector))
  }
  return ResolvedSelector(
    selector = selector,
    matchType = ResolvedSelector.MatchType.Tag,
    node = match,
    allMatches = tagMatches
  )
}

private fun Selector.textNotFoundMessage(): String =
  when (this) {
    is Selector.Auto ->
      "No node matched selector ${describe()} (checked exact tag first, then visible text substring)."

    is Selector.Tag -> tagNotFoundMessage(this)
    is Selector.Text -> "No visible node matched text substring '${normalizedRaw()}'."
  }

private fun tagNotFoundMessage(selector: Selector): String =
  "No node matched exact tag '${selector.normalizedRaw()}'."

private fun tagNotVisibleMessage(selector: Selector): String =
  "Selector ${selector.describe()} matched tag '${selector.normalizedRaw()}', but the node is not visible."

private fun Selector.describe(): String =
  when (this) {
    is Selector.Auto -> "Auto('${normalizedRaw()}')"
    is Selector.Tag -> "Tag('${normalizedRaw()}')"
    is Selector.Text -> "Text('${normalizedRaw()}')"
  }

private fun Selector.normalizedRaw(): String = raw.trim()

private fun NodeSnapshot.normalizedText(): String? = text?.trim()

class SelectorResolutionException(
  message: String
) : IllegalArgumentException(message)
