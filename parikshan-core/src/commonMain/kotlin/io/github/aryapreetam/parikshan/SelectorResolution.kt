package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.Bounds

private const val MIN_VISIBLE_SIZE_PX = 0.1
private const val BOUNDS_OVERLAP_TOLERANCE_PX = 0.5
private const val SPATIAL_GRID_SNAP_PX = 10.0

/**
 * @suppress
 */
@InternalParikshanApi
data class ResolvedSelector(
  val selector: Selector,
  val matchType: MatchType,
  val node: NodeSnapshot,
  val allMatches: List<NodeSnapshot> = listOf(node)
) {
  val tag: String = node.tag.ifEmpty { node.text ?: selector.raw }
  enum class MatchType { Tag, Text }
}

/**
 * @suppress
 */
@InternalParikshanApi
fun Selector.resolveNode(nodes: List<NodeSnapshot>, requireVisible: Boolean = true): ResolvedSelector {
  return when (this) {
    is Selector.Auto -> resolveAuto(nodes = nodes, requireVisible = requireVisible)
    is Selector.Tag -> resolveTag(nodes = nodes, requireVisible = requireVisible)
    is Selector.Text -> resolveText(nodes = nodes, requireVisible = requireVisible)
  }
}

internal fun String.asAutoSelector(): Selector = Selector.Auto(this)

/**
 * @suppress
 */
@InternalParikshanApi
fun Selector.ambiguousTextMessage(matches: List<NodeSnapshot>): String {
  val matchSummary = matches.joinToString(separator = "\n") { node ->
      "  - node[tag='${node.tag}', text='${node.text}', visible=${node.visible}, bounds=${node.bounds}]"
  }
  return "Selector ${describe()} matched multiple visible nodes:\n$matchSummary\nUse a stable tag or an explicit index (e.g. .atIndex(0))."
}

internal fun Selector.Auto.resolveAuto(nodes: List<NodeSnapshot>, requireVisible: Boolean): ResolvedSelector {
  val tagMatches = matchingTagNodes(nodes, requireVisible)
  if (tagMatches.isNotEmpty()) return resolveSingleTagMatch(tagMatches, requireVisible, this)
  return resolveByText(this, nodes, requireVisible)
}

internal fun Selector.Tag.resolveTag(nodes: List<NodeSnapshot>, requireVisible: Boolean): ResolvedSelector =
  resolveSingleTagMatch(matchingTagNodes(nodes, requireVisible), requireVisible, this)

internal fun Selector.Text.resolveText(nodes: List<NodeSnapshot>, requireVisible: Boolean): ResolvedSelector =
  resolveByText(this, nodes, requireVisible)

private fun Selector.matchingTagNodes(nodes: List<NodeSnapshot>, requireVisible: Boolean): List<NodeSnapshot> {
  val normalized = raw.trim()
  return nodes.filter { node ->
    node.tag == normalized && (!requireVisible || (node.visible && node.width > MIN_VISIBLE_SIZE_PX && node.height > MIN_VISIBLE_SIZE_PX))
  }
}

private fun matchingTextNodes(raw: String, nodes: List<NodeSnapshot>, requireVisible: Boolean): List<NodeSnapshot> {
  val normalized = raw.trim()
  val allMatches = nodes.filter { node ->
    val isSane = !requireVisible || (node.visible && node.width > MIN_VISIBLE_SIZE_PX && node.height > MIN_VISIBLE_SIZE_PX)
    isSane && node.normalizedText()?.contains(normalized, ignoreCase = true) == true
  }
  if (allMatches.isEmpty()) return emptyList()
  val exactMatches = allMatches.filter { it.normalizedText().equals(normalized, ignoreCase = true) }
  val startsWithMatches = allMatches.filter { it.normalizedText()?.startsWith(normalized, ignoreCase = true) == true }
  val matchesToUse = when {
    exactMatches.isNotEmpty() -> exactMatches
    startsWithMatches.isNotEmpty() -> startsWithMatches
    else -> allMatches
  }
  val tagged = matchesToUse.filter { it.tag.isNotEmpty() }
  val filtered = if (tagged.isNotEmpty() && tagged.size < matchesToUse.size) tagged else matchesToUse
  val sortedByArea = filtered.sortedBy { it.area }
  val deduplicated = mutableListOf<NodeSnapshot>()
  for (node in sortedByArea) {
    var isDuplicate = false
    val iterator = deduplicated.iterator()
    while (iterator.hasNext()) {
      val existing = iterator.next()
      if (isSameSemanticControl(node, existing)) {
        if (shouldReplaceExisting(newNode = node, existingNode = existing)) {
          iterator.remove()
        } else {
          isDuplicate = true
          break
        }
      }
    }
    if (!isDuplicate) deduplicated.add(node)
  }
  return deduplicated.sortedWith(compareBy<NodeSnapshot>{ (it.bounds.top / SPATIAL_GRID_SNAP_PX).toInt() }.thenBy{ (it.bounds.left / SPATIAL_GRID_SNAP_PX).toInt() })
}

private fun isSameSemanticControl(a: NodeSnapshot, b: NodeSnapshot): Boolean {
  val textA = a.normalizedText()
  val textB = b.normalizedText()
  val sameText = (textA == null && textB == null) ||
      (textA != null && textB != null && textA.equals(textB, ignoreCase = true))
  if (!sameText) return false

  return a.bounds.containsWithTolerance(b.bounds, BOUNDS_OVERLAP_TOLERANCE_PX) ||
         b.bounds.containsWithTolerance(a.bounds, BOUNDS_OVERLAP_TOLERANCE_PX) ||
         a.bounds.overlapsSignificantlyWith(b.bounds)
}

private fun Bounds.containsWithTolerance(other: Bounds, tolerance: Double): Boolean =
  left <= other.left + tolerance &&
  top <= other.top + tolerance &&
  right >= other.right - tolerance &&
  bottom >= other.bottom - tolerance

private fun Bounds.overlapsSignificantlyWith(other: Bounds): Boolean {
  val overlapLeft = maxOf(left, other.left)
  val overlapTop = maxOf(top, other.top)
  val overlapRight = minOf(right, other.right)
  val overlapBottom = minOf(bottom, other.bottom)
  if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) return false
  val overlapArea = (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
  val smallerArea = minOf((right - left) * (bottom - top), (other.right - other.left) * (other.bottom - other.top))
  return smallerArea > 0.0 && (overlapArea / smallerArea) > 0.5
}

private fun shouldReplaceExisting(newNode: NodeSnapshot, existingNode: NodeSnapshot): Boolean {
  // Tagged node always replaces untagged (more specific semantic identity)
  if (newNode.tag.isNotEmpty() && existingNode.tag.isEmpty()) return true
  // When both are untagged, prefer the container (larger area) over the leaf.
  // In Compose, the outer interactive control (e.g. IconButton) holds the OnClick action,
  // while the inner leaf (e.g. Icon) carries only the content description.
  if (newNode.tag.isEmpty() && existingNode.tag.isEmpty() && newNode.area > existingNode.area) return true
  return false
}

private fun resolveByText(selector: Selector, nodes: List<NodeSnapshot>, requireVisible: Boolean): ResolvedSelector {
  val matches = matchingTextNodes(selector.raw, nodes, requireVisible)
  if (matches.isEmpty()) throw SelectorResolutionException(selector.textNotFoundMessage())
  val targetIndex = when {
    selector.index != null && selector.index!! >= 0 -> selector.index!!
    selector.index != null && selector.index!! < 0 -> matches.size + selector.index!!
    else -> 0
  }
  val targetNode = matches.getOrNull(targetIndex)
    ?: throw SelectorResolutionException("Selector ${selector.describe()} index ${selector.index} out of bounds (${matches.size} matches).")
  return ResolvedSelector(selector, ResolvedSelector.MatchType.Text, targetNode, matches)
}

private fun Selector.resolveSingleTagMatch(tagMatches: List<NodeSnapshot>, requireVisible: Boolean, selector: Selector): ResolvedSelector {
  if (tagMatches.isEmpty()) throw SelectorResolutionException(tagNotFoundMessage(selector))
  val sortedMatches = tagMatches.sortedWith(compareByDescending<NodeSnapshot>{ it.zOrder }.thenBy{ (it.bounds.top / SPATIAL_GRID_SNAP_PX).toInt() }.thenBy{ (it.bounds.left / SPATIAL_GRID_SNAP_PX).toInt() })
  val targetIndex = when {
    selector.index != null && selector.index!! >= 0 -> selector.index!!
    selector.index != null && selector.index!! < 0 -> sortedMatches.size + selector.index!!
    else -> 0
  }
  val targetNode = sortedMatches.getOrNull(targetIndex)
    ?: throw SelectorResolutionException("Selector ${selector.describe()} index ${selector.index} out of bounds (${sortedMatches.size} matches).")
  return ResolvedSelector(selector, ResolvedSelector.MatchType.Tag, targetNode, sortedMatches)
}

private fun Selector.textNotFoundMessage(): String = when (this) {
    is Selector.Auto -> "No node matched selector ${describe()} (checked exact tag first, then visible text substring)."
    is Selector.Tag -> tagNotFoundMessage(this)
    is Selector.Text -> "No visible node matched text substring '${normalizedRaw()}'."
}
private fun tagNotFoundMessage(selector: Selector): String = "No node matched exact tag '${selector.normalizedRaw()}'."
private fun tagNotVisibleMessage(selector: Selector, tagMatches: List<NodeSnapshot>): String {
  val matchDetails = tagMatches.joinToString { "node[tag='${it.tag}', text='${it.text}', visible=${it.visible}]" }
  return "Selector ${selector.describe()} matched tag '${selector.normalizedRaw()}', but the node is not visible. Matched nodes: $matchDetails"
}
private fun Selector.describe(): String = when (this) {
    is Selector.Auto -> "Auto('${normalizedRaw()}')"
    is Selector.Tag -> "Tag('${normalizedRaw()}')"
    is Selector.Text -> "Text('${normalizedRaw()}')"
}
private fun Selector.normalizedRaw(): String = raw.trim()
private fun NodeSnapshot.normalizedText(): String? = text?.trim()
/**
 * @suppress
 */
@InternalParikshanApi
class SelectorResolutionException(message: String) : IllegalArgumentException(message)
