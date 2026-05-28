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

  if (allMatches.isEmpty()) return emptyList()

  // 1. Exact matches take priority
  val exactMatches = allMatches.filter { it.normalizedText().equals(normalized, ignoreCase = true) }

  // 2. Starts With matches take priority
  val startsWithMatches = allMatches.filter { it.normalizedText()?.startsWith(normalized, ignoreCase = true) == true }

  val matchesToUse = when {
    exactMatches.isNotEmpty() -> exactMatches
    startsWithMatches.isNotEmpty() -> startsWithMatches
    else -> allMatches
  }

  // 3. Tagged nodes take priority (user-provided identifiers)
  val tagged = matchesToUse.filter { it.tag.isNotEmpty() }
  val filtered = if (tagged.isNotEmpty() && tagged.size < matchesToUse.size) tagged else matchesToUse

  // 3. Leaf Preference: Sort by area (smallest first) and deduplicate by containment.
  // This ensures that if a Card and its Title both match the text (via contentDescription),
  // we prefer the Title (the smaller, more specific node).
  val sortedByArea = filtered.sortedBy { it.area() }
  val deduplicated = mutableListOf<NodeSnapshot>()

  for (node in sortedByArea) {
    var isDuplicate = false
    val iterator = deduplicated.iterator()
    while (iterator.hasNext()) {
      val existing = iterator.next()
      if (node.text == existing.text) {
        // Since we sorted by area, 'existing' is smaller than or equal to 'node'.
        // If 'node' (the larger one) contains 'existing', we skip 'node'.
        if (node.bounds.left <= existing.bounds.left + 0.5 &&
          node.bounds.top <= existing.bounds.top + 0.5 &&
          node.bounds.right >= existing.bounds.right - 0.5 &&
          node.bounds.bottom >= existing.bounds.bottom - 0.5
        ) {
          isDuplicate = true
          break
        } else if (existing.bounds.left <= node.bounds.left + 0.5 &&
          existing.bounds.top <= node.bounds.top + 0.5 &&
          existing.bounds.right >= node.bounds.right - 0.5 &&
          existing.bounds.bottom >= node.bounds.bottom - 0.5
        ) {
          // If 'existing' contains 'node' (can happen if areas are identical),
          // remove 'existing' and prefer 'node'.
          iterator.remove()
        }
      }
    }
    if (!isDuplicate) deduplicated.add(node)
  }

  return deduplicated
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

  val targetIndex = when {
    selector.index != null && selector.index!! >= 0 -> selector.index!!
    selector.index != null && selector.index!! < 0 -> matches.size + selector.index!!
    else -> 0
  }

  val targetNode = matches.getOrNull(targetIndex)
    ?: throw SelectorResolutionException("Selector ${selector.describe()} index ${selector.index} is out of bounds (found ${matches.size} matches).")

  return ResolvedSelector(
    selector = selector,
    matchType = ResolvedSelector.MatchType.Text,
    node = targetNode,
    allMatches = matches
  )
}

private fun Selector.resolveSingleTagMatch(
  tagMatches: List<NodeSnapshot>,
  requireVisible: Boolean,
  selector: Selector
): ResolvedSelector {
  if (tagMatches.isEmpty()) throw SelectorResolutionException(tagNotFoundMessage(selector))
  
  val visibleMatches = if (requireVisible) tagMatches.filter { it.visible } else tagMatches
  if (requireVisible && visibleMatches.isEmpty()) {
    throw SelectorResolutionException(tagNotVisibleMessage(selector))
  }

  val matchesToUse = if (requireVisible) visibleMatches else tagMatches

  val targetIndex = when {
    selector.index != null && selector.index!! >= 0 -> selector.index!!
    selector.index != null && selector.index!! < 0 -> matchesToUse.size + selector.index!!
    else -> 0
  }

  val targetNode = matchesToUse.getOrNull(targetIndex)
    ?: throw SelectorResolutionException("Selector ${selector.describe()} index ${selector.index} is out of bounds (found ${matchesToUse.size} matches).")

  return ResolvedSelector(
    selector = selector,
    matchType = ResolvedSelector.MatchType.Tag,
    node = targetNode,
    allMatches = matchesToUse
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

private fun NodeSnapshot.area(): Double =
  (bounds.right - bounds.left) * (bounds.bottom - bounds.top)

private fun NodeSnapshot.normalizedText(): String? = text?.trim()

class SelectorResolutionException(
  message: String
) : IllegalArgumentException(message)
