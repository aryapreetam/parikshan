package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.js.JsName
import kotlinx.serialization.builtins.ListSerializer

internal object ParikshanTagBridgeHooks {
  private var bridgeInstalled = false

  fun ensureBridgeInstalled() {
    if (bridgeInstalled) {
      return
    }
    bridgeInstalled = true

    GlobalThis.getNodeJson = { tag: String ->
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.snapshotNode(tag)?.let { snapshot ->
        ProtocolJson.instance.encodeToString(NodeSnapshot.serializer(), snapshot)
      }
    }
    GlobalThis.getTreeJson = {
      val semanticsTree = io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.snapshotTree()
      ProtocolJson.instance.encodeToString(ListSerializer(NodeSnapshot.serializer()), semanticsTree)
    }
    GlobalThis.performClick = { tag ->
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performClick(io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag))
    }
    GlobalThis.performInput = { tag, text ->
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performInput(io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag), text)
    }
    GlobalThis.performScroll = { tag, directionName ->
      val direction = ScrollDirection.entries.firstOrNull { it.name == directionName }
      if (direction != null) {
        io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performScroll(io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag), direction)
      } else false
    }
    GlobalThis.performClickIndexed = { tag, index ->
      val selector = io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag, index = index)
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performClick(selector)
    }
    GlobalThis.performInputIndexed = { tag, text, index ->
      val selector = io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag, index = index)
      io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performInput(selector, text)
    }
    GlobalThis.performScrollIndexed = { tag, directionName, index ->
      val direction = ScrollDirection.entries.firstOrNull { it.name == directionName }
      if (direction != null) {
        val selector = io.github.aryapreetam.parikshan.protocol.Selector.Auto(tag, index = index)
        io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor.performScroll(selector, direction)
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

  @JsName("__parikshan_click_indexed")
  var performClickIndexed: (String, Int) -> Boolean

  @JsName("__parikshan_input_indexed")
  var performInputIndexed: (String, String, Int) -> Boolean

  @JsName("__parikshan_scroll_indexed")
  var performScrollIndexed: (String, String, Int) -> Boolean
}
