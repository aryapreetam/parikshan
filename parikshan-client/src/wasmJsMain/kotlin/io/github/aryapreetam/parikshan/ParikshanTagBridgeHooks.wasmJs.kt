package io.github.aryapreetam.parikshan

import androidx.compose.foundation.ScrollState
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.js.JsName
import kotlinx.serialization.builtins.ListSerializer

private data class TrackedNode(
  var left: Double = 0.0,
  var top: Double = 0.0,
  var right: Double = 0.0,
  var bottom: Double = 0.0,
  var visible: Boolean = true,
  var text: String? = null,
  var onClick: (() -> Unit)? = null,
  var onInput: ((String) -> Unit)? = null,
  var onScroll: ((ScrollDirection) -> Unit)? = null,
  var scrollState: ScrollState? = null
) {
  fun toSnapshot(tag: String): NodeSnapshot =
    NodeSnapshot(
      tag = tag,
      bounds =
        Bounds(
          left = left,
          top = top,
          right = right,
          bottom = bottom
        ),
      visible = visible,
      text = text
    )
}

internal actual object ParikshanTagBridgeHooks {
  private val nodes = linkedMapOf<String, TrackedNode>()
  private var bridgeInstalled = false

  actual fun onTagMetadata(
    tag: String,
    text: String?,
    visible: Boolean
  ) {
    ensureBridgeInstalled()
    val node = nodes.getOrPut(tag) { TrackedNode() }
    node.visible = visible
    node.text = text
  }

  actual fun onTagBounds(
    tag: String,
    left: Double,
    top: Double,
    right: Double,
    bottom: Double
  ) {
    ensureBridgeInstalled()
    val node = nodes.getOrPut(tag) { TrackedNode() }
    node.left = left
    node.top = top
    node.right = right
    node.bottom = bottom
  }

  actual fun onTagRemoved(tag: String) {
    nodes.remove(tag)
  }

  actual fun onTagActions(
    tag: String,
    onClick: (() -> Unit)?,
    onInput: ((String) -> Unit)?,
    onScroll: ((ScrollDirection) -> Unit)?,
    scrollState: ScrollState?
  ) {
    ensureBridgeInstalled()
    val node = nodes.getOrPut(tag) { TrackedNode() }
    node.onClick = onClick
    node.onInput = onInput
    node.onScroll = onScroll
    node.scrollState = scrollState
  }

  private fun performClick(tag: String): Boolean {
    val node = nodes[tag] ?: return false
    val action = node.onClick ?: return false
    action()
    return true
  }

  private fun performInput(
    tag: String,
    text: String
  ): Boolean {
    val node = nodes[tag] ?: return false
    val action = node.onInput ?: return false
    action(text)
    return true
  }

  private fun performScroll(
    tag: String,
    direction: ScrollDirection
  ): Boolean {
    val node = nodes[tag] ?: return false
    val action = node.onScroll ?: return false
    action(direction)
    return true
  }

  fun ensureBridgeInstalled() {
    if (bridgeInstalled) {
      return
    }
    bridgeInstalled = true

    GlobalThis.getNodeJson = { tag: String ->
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.snapshotNode(tag)?.let { snapshot ->
        ProtocolJson.instance.encodeToString(NodeSnapshot.serializer(), snapshot)
      } ?: nodes[tag]?.let { node ->
        ProtocolJson.instance.encodeToString(NodeSnapshot.serializer(), node.toSnapshot(tag))
      }
    }
    GlobalThis.getTreeJson = {
      val semanticsTree = io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.snapshotTree()
      val trackedTree = nodes.entries.map { (tag, node) -> node.toSnapshot(tag) }
      val combined = (semanticsTree + trackedTree).distinctBy { it.tag }
      ProtocolJson.instance.encodeToString(ListSerializer(NodeSnapshot.serializer()), combined)
    }
    GlobalThis.performClick = { tag -> 
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performClick(io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag)) || performClick(tag) 
    }
    GlobalThis.performInput = { tag, text -> 
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performInput(io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag), text) || performInput(tag, text) 
    }
    GlobalThis.performScroll = { tag, directionName ->
      val direction = ScrollDirection.entries.firstOrNull { it.name == directionName }
      if (direction != null) {
        io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performScroll(io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag), direction) || performScroll(tag, direction)
      } else false
    }
  }
}

@JsName("globalThis")
private external object GlobalThis {
  @JsName("__parikshan_getNodeJson")
  var getNodeJson: (String) -> String?

  @JsName("__parikshan_getTreeJson")
  var getTreeJson: () -> String

  @JsName("__parikshan_click")
  var performClick: (String) -> Boolean

  @JsName("__parikshan_input")
  var performInput: (String, String) -> Boolean

  @JsName("__parikshan_scroll")
  var performScroll: (String, String) -> Boolean
}
