package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import kotlin.js.JsName
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encodeToString

private data class TrackedNode(
  var left: Double = 0.0,
  var top: Double = 0.0,
  var right: Double = 0.0,
  var bottom: Double = 0.0,
  var visible: Boolean = true,
  var text: String? = null
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

  private fun ensureBridgeInstalled() {
    if (bridgeInstalled) {
      return
    }
    bridgeInstalled = true

    GlobalThis.getNodeJson = { tag: String ->
      nodes[tag]?.let { node ->
        ProtocolJson.instance.encodeToString(NodeSnapshot.serializer(), node.toSnapshot(tag))
      }
    }
    GlobalThis.getTreeJson = {
      val tree = nodes.entries.map { (tag, node) -> node.toSnapshot(tag) }
      ProtocolJson.instance.encodeToString(ListSerializer(NodeSnapshot.serializer()), tree)
    }
  }
}

@JsName("globalThis")
private external object GlobalThis {
  @JsName("__parikshan_getNodeJson")
  var getNodeJson: (String) -> String?

  @JsName("__parikshan_getTreeJson")
  var getTreeJson: () -> String
}
