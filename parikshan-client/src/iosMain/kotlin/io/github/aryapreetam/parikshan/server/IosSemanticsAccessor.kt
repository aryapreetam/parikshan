@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.InternalComposeUiApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class, kotlin.experimental.ExperimentalNativeApi::class)
package io.github.aryapreetam.parikshan.server

import platform.UIKit.*
import platform.Foundation.*
import platform.CoreGraphics.*
import kotlinx.cinterop.*
import kotlinx.cinterop.staticCFunction
import platform.objc.*
import platform.darwin.NSObject
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.resolveNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes

@kotlin.native.concurrent.ThreadLocal
private var activeSimulatedTouches: Set<*>? = null

@kotlin.native.concurrent.ThreadLocal
private var originalAllTouches: kotlinx.cinterop.COpaquePointer? = null

@kotlin.native.concurrent.ThreadLocal
private var originalTouchesForWindow: kotlinx.cinterop.COpaquePointer? = null

@kotlin.native.concurrent.ThreadLocal
private var originalTouchesForView: kotlinx.cinterop.COpaquePointer? = null

@kotlin.native.concurrent.ThreadLocal
private var originalTouchesForGestureRecognizer: kotlinx.cinterop.COpaquePointer? = null

@kotlin.native.concurrent.ThreadLocal
private var originalType: kotlinx.cinterop.COpaquePointer? = null

@kotlin.native.concurrent.ThreadLocal
private var originalSubtype: kotlinx.cinterop.COpaquePointer? = null

@kotlin.native.concurrent.ThreadLocal
private var originalTimestamp: kotlinx.cinterop.COpaquePointer? = null

@ExportObjCClass
private class SimulatedTouch(
    private var _location: CValue<CGPoint>,
    phase: UITouchPhase,
    view: UIView? = null
) : UITouch() {
    private var _previousLocation: CValue<CGPoint> = _location
    private var _phase: UITouchPhase = phase
    private var _view: UIView? = view
    private var _window: UIWindow? = view as? UIWindow ?: view?.window

    init {
        IosSemanticsAccessor.logDebug("SimulatedTouch init: phase=${_phase.value}, view=$_view, window=$_window")
    }

    override fun phase(): UITouchPhase = _phase

    override fun view(): UIView? = _view

    override fun window(): UIWindow? = _window

    override fun timestamp(): platform.Foundation.NSTimeInterval {
        return platform.Foundation.NSProcessInfo.processInfo.systemUptime
    }

    override fun tapCount(): platform.darwin.NSUInteger {
        return 1.convert()
    }

    override fun type(): platform.UIKit.UITouchType {
        return 0.convert()
    }

    override fun locationInView(view: UIView?): CValue<CGPoint> {
        if (view == null) return _location
        val w = view.window ?: this.window ?: return _location
        val (px, py) = _location.useContents { x to y }
        val converted = view.convertPoint(cValue { x = px; y = py }, fromView = w)
        val (cx, cy) = converted.useContents { x to y }
        IosSemanticsAccessor.logDebug("locationInView: view=${view::class.simpleName}, view.window=${view.window}, input=($px, $py), converted=($cx, $cy)")
        return converted
    }

    override fun previousLocationInView(view: UIView?): CValue<CGPoint> {
        if (view == null) return _previousLocation
        val w = view.window ?: this.window ?: return _previousLocation
        val (px, py) = _previousLocation.useContents { x to y }
        val converted = view.convertPoint(cValue { x = px; y = py }, fromView = w)
        val (cx, cy) = converted.useContents { x to y }
        IosSemanticsAccessor.logDebug("previousLocationInView: view=${view::class.simpleName}, view.window=${view.window}, input=($px, $py), converted=($cx, $cy)")
        return converted
    }
    
    fun setLocation(loc: CValue<CGPoint>) {
        _previousLocation = _location
        _location = loc
    }

    fun setView(view: UIView?) {
        _view = view
        _window = view as? UIWindow ?: view?.window
    }

    fun setWindow(window: UIWindow?) {
        _window = window
    }

    fun setPhase(p: UITouchPhase) {
        _phase = p
    }
}


internal object IosSemanticsAccessor {

  private var accessibilitySwizzled = false

  internal fun logDebug(message: String) {
    try {
      println("[Parikshan Accessor] $message")
    } catch (_: Throwable) {}
  }

  fun setup() {
    logDebug("=== setup called ===")
    initializeAccessibilityBridge()
  }

  private fun initializeAccessibilityBridge() {
    if (accessibilitySwizzled) return
    accessibilitySwizzled = true
    
    try {
      val cls = objc_getClass("UIAccessibility") as? ObjCClass
      if (cls != null) {
        val method = class_getClassMethod(cls, sel_registerName("isVoiceOverRunning"))
        if (method != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, Boolean> { _, _ ->
            true
          }
          method_setImplementation(method, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled isVoiceOverRunning class method successfully")
        }
      }
    } catch (e: Throwable) {
      logDebug("[Parikshan] Failed to swizzle isVoiceOverRunning: ${e.message}")
    }

    try {
      val eventCls = objc_getClass("UIEvent") as? ObjCClass
      if (eventCls != null) {
        val allTouchesMethod = class_getInstanceMethod(eventCls, sel_registerName("allTouches"))
        if (allTouchesMethod != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, COpaquePointer?> { self, _ ->
            val simulated = activeSimulatedTouches
            if (simulated != null) {
              val nsSet = platform.Foundation.NSSet.setWithArray(simulated.toList())
              interpretCPointer<CPointed>(nsSet.objcPtr())
            } else {
              val orig = originalAllTouches
              if (orig == null) {
                null
              } else {
                val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>(orig.rawValue)
                call?.invoke(self, sel_registerName("allTouches"))
              }
            }
          }
          originalAllTouches = method_setImplementation(allTouchesMethod, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled UIEvent allTouches successfully")
        }

        val touchesForWindowMethod = class_getInstanceMethod(eventCls, sel_registerName("touchesForWindow:"))
        if (touchesForWindowMethod != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?> { self, _, _ ->
            val simulated = activeSimulatedTouches
            if (simulated != null) {
              val nsSet = platform.Foundation.NSSet.setWithArray(simulated.toList())
              interpretCPointer<CPointed>(nsSet.objcPtr())
            } else {
              val orig = originalTouchesForWindow
              if (orig == null) {
                null
              } else {
                val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>(orig.rawValue)
                call?.invoke(self, sel_registerName("touchesForWindow:"), null)
              }
            }
          }
          originalTouchesForWindow = method_setImplementation(touchesForWindowMethod, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled UIEvent touchesForWindow successfully")
        }

        val touchesForViewMethod = class_getInstanceMethod(eventCls, sel_registerName("touchesForView:"))
        if (touchesForViewMethod != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?> { self, _, _ ->
            val simulated = activeSimulatedTouches
            if (simulated != null) {
              val nsSet = platform.Foundation.NSSet.setWithArray(simulated.toList())
              interpretCPointer<CPointed>(nsSet.objcPtr())
            } else {
              val orig = originalTouchesForView
              if (orig == null) {
                null
              } else {
                val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>(orig.rawValue)
                call?.invoke(self, sel_registerName("touchesForView:"), null)
              }
            }
          }
          originalTouchesForView = method_setImplementation(touchesForViewMethod, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled UIEvent touchesForView successfully")
        }

        val touchesForGestureMethod = class_getInstanceMethod(eventCls, sel_registerName("touchesForGestureRecognizer:"))
        if (touchesForGestureMethod != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, COpaquePointer?, COpaquePointer?> { self, _, _ ->
            val simulated = activeSimulatedTouches
            if (simulated != null) {
              val nsSet = platform.Foundation.NSSet.setWithArray(simulated.toList())
              interpretCPointer<CPointed>(nsSet.objcPtr())
            } else {
              val orig = originalTouchesForGestureRecognizer
              if (orig == null) {
                null
              } else {
                val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>(orig.rawValue)
                call?.invoke(self, sel_registerName("touchesForGestureRecognizer:"), null)
              }
            }
          }
          originalTouchesForGestureRecognizer = method_setImplementation(touchesForGestureMethod, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled UIEvent touchesForGestureRecognizer successfully")
        }

        val typeMethod = class_getInstanceMethod(eventCls, sel_registerName("type"))
        if (typeMethod != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, Long> { self, _ ->
            val simulated = activeSimulatedTouches
            if (simulated != null) {
              0L
            } else {
              val orig = originalType
              if (orig == null) {
                0L
              } else {
                val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Long>>(orig.rawValue)
                call?.invoke(self, sel_registerName("type")) ?: 0L
              }
            }
          }
          originalType = method_setImplementation(typeMethod, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled UIEvent type successfully")
        }

        val subtypeMethod = class_getInstanceMethod(eventCls, sel_registerName("subtype"))
        if (subtypeMethod != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, Long> { self, _ ->
            val simulated = activeSimulatedTouches
            if (simulated != null) {
              0L
            } else {
              val orig = originalSubtype
              if (orig == null) {
                0L
              } else {
                val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Long>>(orig.rawValue)
                call?.invoke(self, sel_registerName("subtype")) ?: 0L
              }
            }
          }
          originalSubtype = method_setImplementation(subtypeMethod, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled UIEvent subtype successfully")
        }

        val timestampMethod = class_getInstanceMethod(eventCls, sel_registerName("timestamp"))
        if (timestampMethod != null) {
          val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, Double> { self, _ ->
            val simulated = activeSimulatedTouches
            if (simulated != null) {
              NSProcessInfo.processInfo.systemUptime
            } else {
              val orig = originalTimestamp
              if (orig == null) {
                0.0
              } else {
                val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> Double>>(orig.rawValue)
                call?.invoke(self, sel_registerName("timestamp")) ?: 0.0
              }
            }
          }
          originalTimestamp = method_setImplementation(timestampMethod, newImp.reinterpret())
          logDebug("[Parikshan] Swizzled UIEvent timestamp successfully")
        }
      }
    } catch (e: Throwable) {
      logDebug("[Parikshan] Failed to swizzle UIEvent: ${e.message}")
    }

    try {
      NSNotificationCenter.defaultCenter.postNotificationName(
        aName = UIAccessibilityVoiceOverStatusDidChangeNotification,
        `object` = null
      )
      logDebug("[Parikshan] Posted UIAccessibilityVoiceOverStatusDidChangeNotification")
    } catch (e: Throwable) {
      logDebug("[Parikshan] Failed to post status change notification: ${e.message}")
    }
  }

  private fun getActiveWindows(): List<UIWindow> {
    val windows = mutableListOf<UIWindow>()
    try {
      val scenes = UIApplication.sharedApplication.connectedScenes
      for (scene in scenes) {
        val windowScene = scene as? UIWindowScene
        if (windowScene != null) {
          val list = windowScene.windows
          for (w in list) {
            (w as? UIWindow)?.let { windows.add(it) }
          }
        }
      }
    } catch (_: Throwable) {}
    
    if (windows.isEmpty()) {
      try {
        UIApplication.sharedApplication.keyWindow?.let { windows.add(it) }
      } catch (_: Throwable) {}
    }
    
    if (windows.isEmpty()) {
      try {
        val list = UIApplication.sharedApplication.windows
        for (w in list) {
          (w as? UIWindow)?.let { windows.add(it) }
        }
      } catch (_: Throwable) {}
    }
    return windows
  }

  private fun getAccessibilityIdentifier(element: Any): String? {
    return try {
      (element as? UIAccessibilityIdentificationProtocol)?.accessibilityIdentifier
    } catch (_: Throwable) {
      null
    }
  }

  private fun getAccessibilityLabel(element: Any): String? {
    return try {
      val value = when (element) {
        is UIView -> element.accessibilityValue
        is UIAccessibilityElement -> element.accessibilityValue
        else -> null
      }
      if (!value.isNullOrBlank()) return value
      val label = when (element) {
        is UIView -> element.accessibilityLabel
        is UIAccessibilityElement -> element.accessibilityLabel
        else -> null
      }
      if (!label.isNullOrBlank()) return label
      null
    } catch (_: Throwable) {
      null
    }
  }

  private fun isAccessibilityElement(element: Any): Boolean {
    return try {
      when (element) {
        is UIView -> element.isAccessibilityElement
        is UIAccessibilityElement -> element.isAccessibilityElement
        else -> false
      }
    } catch (_: Throwable) {
      false
    }
  }

  private fun getAccessibilityFrame(element: Any): CValue<CGRect> {
    return try {
      when (element) {
        is UIView -> element.accessibilityFrame
        is UIAccessibilityElement -> element.accessibilityFrame
        else -> cValue { size.width = 0.0; size.height = 0.0; origin.x = 0.0; origin.y = 0.0 }
      }
    } catch (_: Throwable) {
      cValue { size.width = 0.0; size.height = 0.0; origin.x = 0.0; origin.y = 0.0 }
    }
  }

  private fun getAccessibilityElements(element: Any): List<*>? {
    try {
      if (element is NSObject && element.respondsToSelector(sel_registerName("accessibilityElementCount"))) {
        val count = element.accessibilityElementCount().toInt()
        if (count > 0) {
          val list = mutableListOf<Any>()
          for (i in 0 until count) {
            val child = element.accessibilityElementAtIndex(i.toLong())
            if (child != null) {
              list.add(child)
            }
          }
          return list
        }
      }
      if (element is UIView) {
        val list = element.accessibilityElements
        if (list != null && list.isNotEmpty()) {
          return list
        }
      }
      if (element is UIAccessibilityElement) {
        val list = element.accessibilityElements
        if (list != null && list.isNotEmpty()) {
          return list
        }
      }
    } catch (_: Throwable) {}
    return null
  }

  private fun findAllElements(): List<Pair<Any, NodeSnapshot>> {
    pumpRunLoop(iterations = 5, intervalSeconds = 0.005)
    val result = mutableListOf<Pair<Any, NodeSnapshot>>()
    val windows = getActiveWindows()
    val visited = mutableSetOf<Any>()
    // Traverse windows in reverse order to search overlays / top-most windows first (matching Z-index hierarchy)
    for ((index, window) in windows.reversed().withIndex()) {
      traverse(window, zOrder = index * 1000, visited = visited, outElements = result)
    }
    return result
  }

  private fun traverse(
      element: Any,
      zOrder: Int,
      visited: MutableSet<Any>,
      outElements: MutableList<Pair<Any, NodeSnapshot>>
  ) {
    if (!visited.add(element)) return

    val tag = getAccessibilityIdentifier(element) ?: ""
    val text = getAccessibilityLabel(element)
    val isAcc = isAccessibilityElement(element)

    if (isAcc || tag.isNotEmpty() || !text.isNullOrBlank()) {
      val frameValue = getAccessibilityFrame(element)
      val scale = UIScreen.mainScreen.scale
      var left = 0.0
      var top = 0.0
      var right = 0.0
      var bottom = 0.0
      var width = 0.0
      var height = 0.0
      frameValue.useContents {
        left = origin.x * scale
        top = origin.y * scale
        right = (origin.x + size.width) * scale
        bottom = (origin.y + size.height) * scale
        width = size.width
        height = size.height
      }

      val screenBoundsValue = UIScreen.mainScreen.bounds
      var physicalScreenWidth = 0.0
      var physicalScreenHeight = 0.0
      screenBoundsValue.useContents {
        physicalScreenWidth = size.width * scale
        physicalScreenHeight = size.height * scale
      }

      val keyWindow = getActiveWindows().lastOrNull()
      var topInset = 0.0
      var bottomInset = 0.0
      if (keyWindow != null) {
        try {
          keyWindow.safeAreaInsets.useContents {
            topInset = this.top * scale
            bottomInset = this.bottom * scale
          }
        } catch (_: Throwable) {}
      }

      val hasArea = width > 0.0 && height > 0.0
      val isPhysicallyVisible = hasArea &&
          right > 0.0 && left < physicalScreenWidth &&
          bottom > topInset && top < (physicalScreenHeight - bottomInset)

      val snapshot = NodeSnapshot(
        tag = tag,
        text = text,
        visible = isPhysicallyVisible,
        bounds = Bounds(
          left = left,
          top = top,
          right = right,
          bottom = bottom
        ),
        zOrder = zOrder
      )
      outElements.add(element to snapshot)
    }

    // Traverse accessibilityElements if any
    val elements = getAccessibilityElements(element)
    if (elements != null) {
      for (child in elements) {
        if (child != null) {
          traverse(child, zOrder + 1, visited, outElements)
        }
      }
    }

    // Traverse subviews if view is a UIView
    if (element is UIView) {
      val subviews = element.subviews
      for (subview in subviews) {
        if (subview != null) {
          traverse(subview as Any, zOrder + 1, visited, outElements)
        }
      }
    }
  }

  fun snapshotNode(node: Any): NodeSnapshot {
    val frameValue = getAccessibilityFrame(node)
    val scale = UIScreen.mainScreen.scale
    var left = 0.0
    var top = 0.0
    var right = 0.0
    var bottom = 0.0
    var width = 0.0
    var height = 0.0
    frameValue.useContents {
      left = origin.x * scale
      top = origin.y * scale
      right = (origin.x + size.width) * scale
      bottom = (origin.y + size.height) * scale
      width = size.width
      height = size.height
    }

    val screenBoundsValue = UIScreen.mainScreen.bounds
    var physicalScreenWidth = 0.0
    var physicalScreenHeight = 0.0
    screenBoundsValue.useContents {
      physicalScreenWidth = size.width * scale
      physicalScreenHeight = size.height * scale
    }

    val hasArea = width > 0.0 && height > 0.0
    val isPhysicallyVisible = hasArea &&
        right > 0.0 && left < physicalScreenWidth &&
        bottom > 0.0 && top < physicalScreenHeight

    return NodeSnapshot(
      tag = getAccessibilityIdentifier(node) ?: "",
      text = getAccessibilityLabel(node),
      visible = isPhysicallyVisible,
      bounds = Bounds(
        left = left,
        top = top,
        right = right,
        bottom = bottom
      ),
      zOrder = 0
    )
  }

  fun snapshotTree(): List<NodeSnapshot> {
    val elements = findAllElements()
    logDebug("=== snapshotTree called. Found ${elements.size} elements ===")
    
    // Log view hierarchy details
    val sb = StringBuilder()
    sb.append("=== UIKit View Hierarchy ===\n")
    val windows = getActiveWindows()
    for ((index, window) in windows.withIndex()) {
      sb.append("Window $index:\n")
      dumpView(window, "  ", sb)
    }
    sb.append("=== End UIKit View Hierarchy ===\n")
    logDebug(sb.toString())

    for ((index, pair) in elements.withIndex()) {
      val element = pair.first
      val snapshot = pair.second
      logDebug("  [$index] class=${element::class.simpleName} tag='${snapshot.tag}' text='${snapshot.text}' bounds=${snapshot.bounds} visible=${snapshot.visible}")
    }
    
    return elements.map { it.second }
  }

  fun findNode(tag: String, selector: Selector?): Any? {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) }
    return activeSelector?.let { findBySelector(it) }
  }

  fun findBySelector(selector: Selector): Any? {
    val all = findAllElements()
    if (all.isEmpty()) return null

    val snapshots = all.map { it.second }
    val resolved = try {
      selector.resolveNode(snapshots, requireVisible = false)
    } catch (e: Throwable) {
      return null
    }

    val matchedSnapshot = resolved.node
    val matchedIndex = snapshots.indexOf(matchedSnapshot)
    if (matchedIndex >= 0 && matchedIndex < all.size) {
      return all[matchedIndex].first
    }
    return null
  }

  fun performClickAtCoordinates(x: Double, y: Double): String {
    val scale = UIScreen.mainScreen.scale
    val point = cValue<CGPoint> { this.x = x / scale; this.y = y / scale }

    val window = getActiveComposeWindow() ?: return "No active window found"
    val targetView = window.hitTest(point, withEvent = null) ?: findInputView(window) ?: window

    val touch = SimulatedTouch(point, UITouchPhase.UITouchPhaseBegan, targetView)
    val touchSet = NSSet.setWithObject(touch)
    val event = UIEvent()

    activeSimulatedTouches = touchSet
    try {
      dispatchTouchToAll(touch, UITouchPhase.UITouchPhaseBegan, window, event)
      pumpRunLoop(iterations = 2, intervalSeconds = 0.005)

      touch.setPhase(UITouchPhase.UITouchPhaseEnded)
      dispatchTouchToAll(touch, UITouchPhase.UITouchPhaseEnded, window, event)
      pumpRunLoop(iterations = 2, intervalSeconds = 0.005)
    } finally {
      activeSimulatedTouches = null
    }

    return "OK"
  }

  private fun isSemanticsNodeDisabled(node: SemanticsNode): Boolean {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)) {
        return true
      }
      current = current.parent
    }
    return false
  }

  private fun isElementDisabled(node: Any, tag: String, text: String?): Boolean {
    try {
      val traits = (node as? NSObject)?.accessibilityTraits ?: 0uL
      if ((traits and UIAccessibilityTraitNotEnabled) != 0uL) {
        return true
      }
    } catch (_: Throwable) {}

    try {
      val semanticsNode = findSemanticsNode(tag, text)
      if (semanticsNode != null && isSemanticsNodeDisabled(semanticsNode)) {
        return true
      }
    } catch (_: Throwable) {}

    return false
  }

  fun performClickResult(tag: String, selector: Selector?): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    val node = findNode(tag, selector) ?: return "Node not found for selector: $activeSelector"
    val snapshot = snapshotNode(node)

    val effectiveTag = tag.ifBlank { snapshot.tag }
    if (isElementDisabled(node, effectiveTag, snapshot.text)) {
      logDebug("Node '$activeSelector' is disabled. Rejecting click.")
      return "Node '$activeSelector' is disabled"
    }

    if (node is NSObject) {
      logDebug("Found NSObject node for selector: $activeSelector. Attempting accessibilityActivate...")
      try {
        if (node.accessibilityActivate()) {
          logDebug("accessibilityActivate succeeded for: $activeSelector")
          pumpRunLoop(iterations = 2, intervalSeconds = 0.005)
          return "OK"
        }
        logDebug("accessibilityActivate returned false for: $activeSelector. Falling back to coordinates.")
      } catch (e: Throwable) {
        logDebug("accessibilityActivate threw exception for: $activeSelector. Error: ${e.message}. Falling back to coordinates.")
      }
    }

    return performClickAtCoordinates(snapshot.bounds.centerX, snapshot.bounds.centerY)
  }

  fun performClick(tag: String, selector: Selector?): Boolean {
    return performClickResult(tag, selector) == "OK"
  }

  fun resignCurrentFirstResponder() {
    try {
      getActiveWindows().forEach { it.endEditing(true) }
      findFirstResponder()?.resignFirstResponder()
    } catch (_: Throwable) {}
  }

  private fun findFirstResponder(): UIView? {
    val windows = getActiveWindows()
    for (window in windows) {
      val responder = findFirstResponder(window)
      if (responder != null) return responder
    }
    return null
  }

  private fun findFirstResponder(view: UIView): UIView? {
    if (view.isFirstResponder) return view
    val subviews = view.subviews
    for (subview in subviews) {
      val found = (subview as? UIView)?.let { findFirstResponder(it) }
      if (found != null) return found
    }
    return null
  }

  private fun findComposeView(view: UIView): UIView? {
    val className = view::class.simpleName ?: ""
    if (className.contains("ComposeView")) return view
    val subviews = view.subviews
    for (subview in subviews) {
      val found = (subview as? UIView)?.let { findComposeView(it) }
      if (found != null) return found
    }
    return null
  }

  private fun findMetalView(view: UIView): UIView? {
    val className = view::class.simpleName ?: ""
    if (className.contains("MetalView")) return view
    val subviews = view.subviews
    for (subview in subviews) {
      val found = (subview as? UIView)?.let { findMetalView(it) }
      if (found != null) return found
    }
    return null
  }

  private fun findInputView(view: UIView): UIView? {
    // Prioritize InteractionUIView (CMP 1.12+ touch input receiver)
    val interactionInput = findInputViewWithClassName(view, "InteractionUIView")
    if (interactionInput != null) return interactionInput

    // Prioritize OverlayInputView (CMP 1.10 - 1.11 touch/key listener in Compose)
    val overlayInput = findInputViewWithClassName(view, "OverlayInputView")
    if (overlayInput != null) return overlayInput

    // Fallback to MetalView
    val metalView = findInputViewWithClassName(view, "MetalView")
    if (metalView != null) return metalView

    // Fallback to ComposeView
    val composeView = findInputViewWithClassName(view, "ComposeView")
    if (composeView != null) return composeView

    return null
  }

  private fun findInputViewWithClassName(view: UIView, targetClassName: String): UIView? {
    val className = view::class.simpleName ?: ""
    if (className.contains(targetClassName)) {
      return view
    }
    val subviews = view.subviews
    for (i in 0 until subviews.size) {
      val subview = subviews[i] as? UIView
      if (subview != null) {
        val found = findInputViewWithClassName(subview, targetClassName)
        if (found != null) return found
      }
    }
    return null
  }

  private fun getActiveComposeWindow(): UIWindow? {
    val windows = getActiveWindows()
    for (window in windows.reversed()) {
      if (findInputView(window) != null) {
        return window
      }
    }
    return windows.lastOrNull()
  }


  private fun collectInputViews(view: UIView, list: MutableList<UIView>) {
    val className = view::class.simpleName ?: ""
    if (className.contains("InteractionUIView") || className.contains("InputView") || className.contains("ComposeView") || className.contains("MetalView")) {
      list.add(view)
    }
    val subviews = view.subviews
    for (i in 0 until subviews.size) {
      val subview = subviews[i] as? UIView
      if (subview != null) {
        collectInputViews(subview, list)
      }
    }
  }


  private fun dispatchTouchToAll(touch: SimulatedTouch, phase: UITouchPhase, window: UIWindow, event: UIEvent) {
    val touchSet = NSSet.setWithObject(touch)
    val targetView = touch.view ?: window

    val viewsToNotify = mutableListOf<UIView>()
    viewsToNotify.add(targetView)

    // Ensure the active Compose input view (InteractionUIView or OverlayInputView) receives touches
    val composeInputView = findInputView(window)
    if (composeInputView != null && !viewsToNotify.contains(composeInputView)) {
      viewsToNotify.add(composeInputView)
    }

    if (targetView != window && targetView.superview != null && !viewsToNotify.contains(window)) {
      viewsToNotify.add(window)
    }

    viewsToNotify.forEach { view ->
      try {
        when (phase) {
          UITouchPhase.UITouchPhaseBegan -> view.touchesBegan(touchSet, withEvent = event)
          UITouchPhase.UITouchPhaseMoved -> view.touchesMoved(touchSet, withEvent = event)
          UITouchPhase.UITouchPhaseEnded -> view.touchesEnded(touchSet, withEvent = event)
          else -> {}
        }
      } catch (e: Throwable) {
        logDebug("Error delivering touch to view ${view::class.simpleName}: ${e.message}")
      }
    }

    val recognizersToNotify = mutableListOf<UIGestureRecognizer>()
    targetView.gestureRecognizers?.forEach { rec ->
      if (rec is UIGestureRecognizer && !recognizersToNotify.contains(rec)) {
        recognizersToNotify.add(rec)
      }
    }
    composeInputView?.gestureRecognizers?.forEach { rec ->
      if (rec is UIGestureRecognizer && !recognizersToNotify.contains(rec)) {
        recognizersToNotify.add(rec)
      }
    }

    recognizersToNotify.forEach { rec ->
      try {
        when (phase) {
          UITouchPhase.UITouchPhaseBegan -> rec.touchesBegan(touchSet, withEvent = event)
          UITouchPhase.UITouchPhaseMoved -> rec.touchesMoved(touchSet, withEvent = event)
          UITouchPhase.UITouchPhaseEnded -> rec.touchesEnded(touchSet, withEvent = event)
          else -> {}
        }
      } catch (e: Throwable) {
        logDebug("Error delivering touch to recognizer ${rec::class.simpleName}: ${e.message}")
      }
    }
  }

  private fun semanticsTextCandidatesOf(node: SemanticsNode): List<String> = buildList {
    node.config.getOrNull(SemanticsProperties.EditableText)?.text
      ?.takeIf { it.isNotBlank() }
      ?.let { add(it) }
    node.config.getOrNull(SemanticsProperties.Text)
      ?.joinToString(" ") { it.text }
      ?.takeIf { it.isNotBlank() }
      ?.let { add(it) }
    node.config.getOrNull(SemanticsProperties.ContentDescription)
      ?.joinToString(" ")
      ?.takeIf { it.isNotBlank() }
      ?.let { add(it) }
  }

  private fun semanticsMatchScore(node: SemanticsNode, matchText: String): Int? {
    val candidates = semanticsTextCandidatesOf(node)
    if (candidates.any { it.equals(matchText, ignoreCase = true) }) return 0
    if (candidates.any { it.contains(matchText, ignoreCase = true) }) return 1
    return null
  }

  private fun resolveSetTextTarget(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.SetText) != null) {
        return current
      }
      current = current.parent
    }

    val queue = ArrayDeque<SemanticsNode>()
    queue.addAll(node.children)
    while (queue.isNotEmpty()) {
      val child = queue.removeFirst()
      if (child.config.getOrNull(SemanticsActions.SetText) != null) {
        return child
      }
      queue.addAll(child.children)
    }
    return null
  }

  private fun collectAllSemanticsNodes(): List<SemanticsNode> {
    val activeOwners = io.github.aryapreetam.parikshan.IosSemanticsRegistry.getActiveOwners()
    if (activeOwners.isEmpty()) return emptyList()

    val collected = mutableListOf<SemanticsNode>()
    val seenIds = mutableSetOf<Int>()
    for (owner in activeOwners) {
      @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      val unmerged = try { owner.getAllSemanticsNodes(mergingEnabled = false) } catch (_: Throwable) { emptyList() }
      for (n in unmerged) {
        if (seenIds.add(n.id)) collected.add(n)
      }
      @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      val merged = try { owner.getAllSemanticsNodes(mergingEnabled = true) } catch (_: Throwable) { emptyList() }
      for (n in merged) {
        if (seenIds.add(n.id)) collected.add(n)
      }
    }
    return collected
  }

  private fun findSemanticsNode(tag: String, matchText: String?): SemanticsNode? {
    val allNodes = collectAllSemanticsNodes()
    logDebug("findSemanticsNode: tag='$tag', text='$matchText', totalNodes=${allNodes.size}")
    if (allNodes.isEmpty()) return null

    if (tag.isNotBlank()) {
      allNodes.find {
        it.config.getOrNull(SemanticsProperties.TestTag) == tag
      }?.let { return it }
    }

    if (!matchText.isNullOrBlank()) {
      var bestNode: SemanticsNode? = null
      var bestScore = Int.MAX_VALUE
      for (n in allNodes) {
        val score = semanticsMatchScore(n, matchText) ?: continue
        if (score < bestScore) {
          bestScore = score
          bestNode = n
          if (score == 0) break
        }
      }
      if (bestNode != null) return bestNode
    }

    return null
  }

  private fun findInputSemanticsNode(
    tag: String,
    selector: Selector?,
    snapshot: NodeSnapshot
  ): SemanticsNode? {
    val allNodes = collectAllSemanticsNodes()
    if (allNodes.isEmpty()) return null

    val effectiveTag = tag.ifBlank { snapshot.tag }
    val matchText = snapshot.text?.takeIf { it.isNotBlank() }
      ?: (selector as? Selector.Text)?.raw?.takeIf { it.isNotBlank() }
      ?: (selector as? Selector.Auto)?.raw?.takeIf { it.isNotBlank() }

    data class InputCandidate(
      val target: SemanticsNode,
      val matchScore: Int,
      val boundsDistanceSq: Double
    )

    val seenTargets = mutableSetOf<Int>()
    val candidates = mutableListOf<InputCandidate>()

    fun boundsDistanceSq(node: SemanticsNode): Double {
      val b = node.boundsInWindow
      val cx = (b.left + b.right) / 2.0
      val cy = (b.top + b.bottom) / 2.0
      val dx = cx - snapshot.bounds.centerX
      val dy = cy - snapshot.bounds.centerY
      return (dx * dx) + (dy * dy)
    }

    for (n in allNodes) {
      val score = when {
        effectiveTag.isNotBlank() && n.config.getOrNull(SemanticsProperties.TestTag) == effectiveTag -> 0
        !matchText.isNullOrBlank() -> semanticsMatchScore(n, matchText)?.plus(1)
        else -> null
      } ?: continue

      val setTextTarget = resolveSetTextTarget(n) ?: continue
      if (seenTargets.add(setTextTarget.id)) {
        candidates.add(
          InputCandidate(
            target = setTextTarget,
            matchScore = score,
            boundsDistanceSq = boundsDistanceSq(setTextTarget)
          )
        )
      }
    }

    if (candidates.isEmpty()) return null

    candidates.sortWith(
      compareBy<InputCandidate> { it.matchScore }
        .thenBy { it.boundsDistanceSq }
    )
    return candidates.first().target
  }

  private fun readLiveEditableText(textInput: UITextInputProtocol?, node: Any): String? {
    if (textInput != null) {
      try {
        val range = textInput.textRangeFromPosition(
          textInput.beginningOfDocument,
          toPosition = textInput.endOfDocument
        )
        if (range != null) {
          return textInput.textInRange(range) ?: ""
        }
      } catch (_: Throwable) {}
    }

    return try {
      when (node) {
        is UIView -> node.accessibilityValue
        is UIAccessibilityElement -> node.accessibilityValue
        else -> null
      }
    } catch (_: Throwable) {
      null
    }
  }

  private fun moveCursorToEnd(textInput: UITextInputProtocol) {
    try {
      val endPos = textInput.endOfDocument
      val endRange = textInput.textRangeFromPosition(endPos, toPosition = endPos)
      if (endRange != null) {
        textInput.selectedTextRange = endRange
      }
    } catch (_: Throwable) {}
  }

  private fun nowWallClockMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

  private fun findScrollAction(node: SemanticsNode): ((Float, Float) -> Boolean)? {
    var currentNode: SemanticsNode? = node
    while (currentNode != null) {
      val action = currentNode.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.ScrollBy)?.action
      if (action != null) return action
      currentNode = currentNode.parent
    }

    val queue = mutableListOf<SemanticsNode>()
    queue.addAll(node.children)
    while (queue.isNotEmpty()) {
      val child = queue.removeAt(0)
      val action = child.config.getOrNull(androidx.compose.ui.semantics.SemanticsActions.ScrollBy)?.action
      if (action != null) return action
      queue.addAll(child.children)
    }

    return null
  }

  fun performInputResult(tag: String, selector: Selector?, text: String): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    val node = findNode(tag, selector) ?: return "Node not found for input: $activeSelector"
    val snapshot = snapshotNode(node)

    // Tier 1: Direct Compose Semantics SetText (cursor-position independent, matches Android/Desktop/Wasm)
    try {
      val targetSemanticsNode = findInputSemanticsNode(tag, selector, snapshot)
      logDebug("findInputSemanticsNode resolved target: $targetSemanticsNode")

      if (targetSemanticsNode != null) {
        val requestFocusAction = targetSemanticsNode.config.getOrNull(SemanticsActions.RequestFocus)
        if (requestFocusAction != null) {
          logDebug("Requesting focus via semantics action.")
          requestFocusAction.action?.invoke()
        }

        val setTextAction = targetSemanticsNode.config.getOrNull(SemanticsActions.SetText)
        if (setTextAction != null) {
          logDebug("Invoking SetText semantics action with '$text'")
          val success = setTextAction.action?.invoke(androidx.compose.ui.text.AnnotatedString(text))
          logDebug("SetText action result: $success")
          if (success == true) {
            pumpRunLoop(iterations = 2, intervalSeconds = 0.005)
            return "OK"
          }
        }
      }
    } catch (e: Throwable) {
      logDebug("Failed to execute text input via semantics action: ${e.message}")
    }

    // Focus the text field via accessibilityActivate or coordinate click for UIKit fallbacks
    var focusedViaAccessibility = false
    if (node is NSObject) {
      logDebug("Attempting accessibilityActivate on text field node...")
      try {
        if (node.accessibilityActivate()) {
          logDebug("accessibilityActivate succeeded for focusing text field node.")
          focusedViaAccessibility = true
        }
      } catch (e: Throwable) {
        logDebug("accessibilityActivate threw error for focusing text field node: ${e.message}")
      }
    }

    if (!focusedViaAccessibility) {
      logDebug("Focusing text field via coordinate click: (${snapshot.bounds.centerX}, ${snapshot.bounds.centerY})")
      val clickRes = performClickAtCoordinates(snapshot.bounds.centerX, snapshot.bounds.centerY)
      if (clickRes != "OK") return "Click to focus failed: $clickRes"
    }

    // Poll briefly for active first responder
    var firstResponder = findFirstResponder()
    if (firstResponder == null) {
      for (i in 0 until 10) {
        pumpRunLoop(iterations = 1, intervalSeconds = 0.005)
        firstResponder = findFirstResponder()
        if (firstResponder != null) break
      }
    }
    logDebug("findFirstResponder returned: $firstResponder, className=${firstResponder?.let { it::class.simpleName }}, implements UIKeyInput=${firstResponder is UIKeyInputProtocol}")

    if (firstResponder == null) {
      return "Active text responder not found"
    }

    val textInput = firstResponder as? UITextInputProtocol

    // Tier 2: Cursor-independent UITextInput full-range replacement
    if (textInput != null) {
      try {
        val fullRange = textInput.textRangeFromPosition(
          textInput.beginningOfDocument,
          toPosition = textInput.endOfDocument
        )
        if (fullRange != null) {
          logDebug("Replacing full UITextInput document range with '$text'")
          textInput.replaceRange(fullRange, withText = text)
          pumpRunLoop(iterations = 5, intervalSeconds = 0.01)
          val updated = readLiveEditableText(textInput, node)
          if (updated == text) {
            return "OK"
          }
          logDebug("UITextInput replaceRange post-check mismatch: expected='$text', actual='$updated'")
        }
      } catch (e: Throwable) {
        logDebug("UITextInput replaceRange fallback error: ${e.message}")
      }

      // Position cursor at end before any backward-deletion fallback
      moveCursorToEnd(textInput)
      pumpRunLoop(iterations = 2, intervalSeconds = 0.005)
    }

    // Tier 3a: Compose responder KVC batch mutation (verified post-condition)
    val responderObj = firstResponder as? NSObject
    if (responderObj != null) {
      val input = if (responderObj.respondsToSelector(platform.Foundation.NSSelectorFromString("input"))) {
        try { responderObj.valueForKey("input") as? NSObject } catch (_: Throwable) { null }
      } else null
      if (input != null) {
        val beginSel = NSSelectorFromString("beginEditBatch")
        val deleteSel = NSSelectorFromString("deleteBackward")
        val insertSel = NSSelectorFromString("insertText:")
        val endSel = NSSelectorFromString("endEditBatch")

        if (input.respondsToSelector(beginSel) && input.respondsToSelector(endSel)) {
          val liveBefore = readLiveEditableText(textInput, node) ?: snapshot.text ?: ""
          val currentTextLength = liveBefore.length
          logDebug("Mutating Compose text input responder via KVC (clearing $currentTextLength chars).")

          input.performSelector(beginSel)
          try {
            repeat(currentTextLength) {
              if (input.respondsToSelector(deleteSel)) {
                input.performSelector(deleteSel)
              }
            }
            if (text.isNotEmpty() && input.respondsToSelector(insertSel)) {
              input.performSelector(insertSel, withObject = NSString.create(string = text))
            }
          } finally {
            input.performSelector(endSel)
          }

          pumpRunLoop(iterations = 10, intervalSeconds = 0.01)
          val liveAfter = readLiveEditableText(textInput, node)
          if (liveAfter == null || liveAfter == text) {
            return "OK"
          }
          logDebug("KVC edit batch post-check mismatch: expected='$text', actual='$liveAfter'")
        }
      }
    }

    // Tier 3b: Bounded, progress-checked UIKeyInput fallback (cannot hang main thread)
    if (firstResponder is UIKeyInputProtocol) {
      val keyInput = firstResponder as UIKeyInputProtocol
      val liveBefore = readLiveEditableText(textInput, node) ?: snapshot.text ?: ""

      val stallReason = clearBoundedUiKeyInput(
        initialLength = liveBefore.length,
        hasText = { keyInput.hasText() },
        currentLength = { readLiveEditableText(textInput, node)?.length },
        deleteBackward = { keyInput.deleteBackward() },
        afterDeleteStep = { pumpRunLoop(iterations = 2, intervalSeconds = 0.01) },
        nowMs = { nowWallClockMs() }
      )
      if (stallReason != null) {
        logDebug("Bounded UIKeyInput clear aborted: $stallReason")
        return stallReason
      }

      pumpRunLoop(iterations = 5, intervalSeconds = 0.01)

      if (text.isNotEmpty()) {
        logDebug("Inserting text character-by-character: '$text'")
        for (char in text) {
          keyInput.insertText(char.toString())
          pumpRunLoop(iterations = 3, intervalSeconds = 0.01)
        }
        pumpRunLoop(iterations = 10, intervalSeconds = 0.01)
      }

      return "OK"
    }
    return "First responder does not implement UIKeyInput"
  }

  fun performInput(tag: String, selector: Selector?, text: String): Boolean {
    return performInputResult(tag, selector, text) == "OK"
  }

  fun performScrollResult(tag: String, selector: Selector?, direction: ScrollDirection): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    logDebug("performScrollResult start: tag='$tag', selector=$selector, direction=$direction")

    // 1. Try resolving via IosSemanticsRegistry and executing SemanticsActions.ScrollBy
    try {
      val effectiveTag = if (tag.isNotBlank()) tag else (selector as? Selector.Tag)?.raw.orEmpty()
      val effectiveText = (selector as? Selector.Text)?.raw

      var targetSemanticsNode = findSemanticsNode(effectiveTag, effectiveText)

      // If not found directly, try finding the node in accessibility tree first to get its snapshot tag/text
      val a11yNode = findNode(tag, selector)
      val snapshot = a11yNode?.let { snapshotNode(it) }

      if (targetSemanticsNode == null && snapshot != null) {
        val snapTag = if (effectiveTag.isNotBlank()) effectiveTag else snapshot.tag
        val snapText = if (!effectiveText.isNullOrBlank()) effectiveText else snapshot.text
        targetSemanticsNode = findSemanticsNode(snapTag, snapText)
      }

      if (targetSemanticsNode != null) {
        logDebug("Found targetSemanticsNode for scroll: $targetSemanticsNode")
        val scrollAction = findScrollAction(targetSemanticsNode)
        if (scrollAction != null) {
          logDebug("Found ScrollBy action on semantics node. Calculating scroll deltas...")
          val safeWidth = if (targetSemanticsNode.size.width > 0) targetSemanticsNode.size.width.toFloat() else 400f
          val safeHeight = if (targetSemanticsNode.size.height > 0) targetSemanticsNode.size.height.toFloat() else 400f

          val deltaX = (safeWidth * 0.5f).coerceAtLeast(300f)
          val deltaY = (safeHeight * 0.5f).coerceAtLeast(300f)

          val x = when (direction) {
            ScrollDirection.Left -> -deltaX
            ScrollDirection.Right -> deltaX
            else -> 0f
          }
          val y = when (direction) {
            ScrollDirection.Up -> -deltaY
            ScrollDirection.Down -> deltaY
            else -> 0f
          }

          logDebug("Invoking ScrollBy with x=$x, y=$y")
          val success = scrollAction.invoke(x, y)
          logDebug("ScrollBy action result: $success")
          if (success) {
            pumpRunLoop(iterations = 2, intervalSeconds = 0.005)
            return "OK"
          }
        } else {
          logDebug("No ScrollBy action found on targetSemanticsNode or its hierarchy.")
        }
      } else {
        logDebug("No SemanticsNode found in IosSemanticsRegistry for tag='$effectiveTag', text='$effectiveText'")
      }

      // 2. Fallback: Perform coordinate drag via accessibility tree
      if (a11yNode == null || snapshot == null) {
        return "Node not found for scroll: $activeSelector"
      }

      val startX = snapshot.bounds.centerX
      val startY = snapshot.bounds.centerY

      val isHorizontal = direction == ScrollDirection.Left || direction == ScrollDirection.Right
      val distance = if (isHorizontal) {
        (snapshot.bounds.width * 0.5).coerceAtLeast(300.0)
      } else {
        (snapshot.bounds.height * 0.5).coerceAtLeast(300.0)
      }

      val endX = when (direction) {
        ScrollDirection.Left -> startX + distance
        ScrollDirection.Right -> startX - distance
        else -> startX
      }

      val endY = when (direction) {
        ScrollDirection.Up -> startY + distance
        ScrollDirection.Down -> startY - distance
        else -> startY
      }

      logDebug("Falling back to performDrag from ($startX, $startY) to ($endX, $endY)")
      return performDrag(startX, startY, endX, endY, durationMs = 300)
    } catch (e: Throwable) {
      logDebug("Exception in performScrollResult: ${e.message}")
      return "Scroll failed: ${e.message}"
    }
  }

  fun performScroll(tag: String, selector: Selector?, direction: ScrollDirection): Boolean {
    return performScrollResult(tag, selector, direction) == "OK"
  }

  fun performDrag(fromX: Double, fromY: Double, toX: Double, toY: Double, durationMs: Long): String {
    if (fromX == toX && fromY == toY) {
      return performClickAtCoordinates(fromX, fromY)
    }

    val scale = UIScreen.mainScreen.scale
    logDebug("performDrag: physical from=($fromX, $fromY), to=($toX, $toY), logical from=(${fromX/scale}, ${fromY/scale}), to=(${toX/scale}, ${toY/scale})")

    val fromPoint = cValue<CGPoint> { x = fromX / scale; y = fromY / scale }
    val toPoint = cValue<CGPoint> { x = toX / scale; y = toY / scale }

    val window = getActiveComposeWindow() ?: return "No active window found"
    val targetView = window.hitTest(fromPoint, withEvent = null) ?: findInputView(window) ?: window

    val touch = SimulatedTouch(fromPoint, UITouchPhase.UITouchPhaseBegan, targetView)
    val touchSet = NSSet.setWithObject(touch)
    val event = UIEvent()

    activeSimulatedTouches = touchSet
    try {
      dispatchTouchToAll(touch, UITouchPhase.UITouchPhaseBegan, window, event)

      val stepDelayMs = 20L
      val steps = (durationMs / stepDelayMs).coerceAtLeast(1).toInt()
      for (i in 1..steps) {
        val fraction = i.toDouble() / steps
        val curPoint = cValue<CGPoint> {
          x = (fromX + (toX - fromX) * fraction) / scale
          y = (fromY + (toY - fromY) * fraction) / scale
        }
        touch.setLocation(curPoint)
        touch.setPhase(UITouchPhase.UITouchPhaseMoved)
        dispatchTouchToAll(touch, UITouchPhase.UITouchPhaseMoved, window, event)

        pumpRunLoop(iterations = 1, intervalSeconds = stepDelayMs.toDouble() / 1000.0)
      }

      touch.setLocation(toPoint)
      touch.setPhase(UITouchPhase.UITouchPhaseEnded)
      dispatchTouchToAll(touch, UITouchPhase.UITouchPhaseEnded, window, event)
      pumpRunLoop(iterations = 2, intervalSeconds = 0.005)
    } finally {
      activeSimulatedTouches = null
    }

    return "OK"
  }

  internal fun clearBoundedUiKeyInput(
    initialLength: Int,
    hasText: () -> Boolean,
    currentLength: () -> Int?,
    deleteBackward: () -> Unit,
    afterDeleteStep: () -> Unit = {},
    nowMs: () -> Long,
    maxIterations: Int = maxOf(initialLength + 32, 128),
    deadlineMs: Long = 2_000L,
    maxConsecutiveStalls: Int = 5
  ): String? {
    var lastObservedLength = currentLength()?.coerceAtLeast(0)
    if (lastObservedLength == 0 || (lastObservedLength == null && !hasText())) {
      return null
    }

    val effectiveMaxIterations = maxIterations.coerceAtLeast(1)
    val effectiveMaxStalls = maxConsecutiveStalls.coerceAtLeast(1)
    val deadline = nowMs() + deadlineMs.coerceAtLeast(1L)
    var consecutiveStalls = 0

    for (iteration in 1..effectiveMaxIterations) {
      if (nowMs() >= deadline) {
        return "UIKeyInput deletion exceeded ${deadlineMs}ms deadline after ${iteration - 1} steps " +
          "(remaining length=${lastObservedLength ?: "unknown"})"
      }

      deleteBackward()
      afterDeleteStep()

      val observed = currentLength()?.coerceAtLeast(0)
      if (observed == 0 || (observed == null && !hasText())) {
        return null
      }

      if (observed != null && lastObservedLength != null) {
        if (observed < lastObservedLength) {
          consecutiveStalls = 0
        } else {
          consecutiveStalls += 1
          if (consecutiveStalls >= effectiveMaxStalls) {
            return "UIKeyInput deletion made no progress after $consecutiveStalls consecutive steps " +
              "(remaining length=$observed)"
          }
        }
      }
      lastObservedLength = observed
    }

    val finalLength = currentLength()?.coerceAtLeast(0)
    if (finalLength == 0 || (finalLength == null && !hasText())) {
      return null
    }
    return "UIKeyInput deletion reached max iterations ($effectiveMaxIterations) without emptying field " +
      "(remaining length=${finalLength ?: "unknown"})"
  }

  private fun dumpView(view: UIView, indent: String, sb: StringBuilder) {
    val className = view::class.simpleName ?: "unknown"
    val tag = (view as? UIAccessibilityIdentificationProtocol)?.accessibilityIdentifier ?: ""
    val label = view.accessibilityLabel ?: ""
    val frame = view.accessibilityFrame.useContents { "L:${origin.x}, T:${origin.y}, W:${size.width}, H:${size.height}" }
    sb.append(indent)
      .append(className)
      .append(" (tag='").append(tag).append("', label='").append(label).append("', frame=[").append(frame).append("], subviews=")
      .append(view.subviews.size)
      .append(")\n")

    val subviews = view.subviews
    for (subview in subviews) {
      (subview as? UIView)?.let { dumpView(it, "$indent  ", sb) }
    }
  }
}

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
@CName("UIAccessibilityIsVoiceOverRunning")
fun overrideUIAccessibilityIsVoiceOverRunning(): Boolean {
  return true
}
