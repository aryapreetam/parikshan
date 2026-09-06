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

    init {
        setPhase(phase)
        setView(view)
        setWindow(view as? UIWindow ?: view?.window)
        IosSemanticsAccessor.logDebug("SimulatedTouch init: phase=${this.phase.value}, view=${this.view}, window=${this.window}")
    }

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
        try {
            this.setValue(view, forKey = "view")
        } catch (_: Throwable) {
            try {
                this.setValue(view, forKey = "_view")
            } catch (_: Throwable) {}
        }
    }

    fun setWindow(window: UIWindow?) {
        try {
            this.setValue(window, forKey = "window")
        } catch (_: Throwable) {
            try {
                this.setValue(window, forKey = "_window")
            } catch (_: Throwable) {}
        }
    }

    fun setPhase(p: UITouchPhase) {
        var success = false
        try {
            this.setValue(p.value, forKey = "phase")
            success = true
        } catch (e: Throwable) {
            try {
                this.setValue(p.value, forKey = "_phase")
                success = true
            } catch (e2: Throwable) {
                IosSemanticsAccessor.logDebug("setPhase failed to set ${p.value}: ${e.message} / ${e2.message}")
            }
        }
        if (success) {
            IosSemanticsAccessor.logDebug("setPhase succeeded, touch.phase is now: ${this.phase.value}")
        }
    }
}


internal object IosSemanticsAccessor {

  private var accessibilitySwizzled = false

  internal fun logDebug(message: String) {
    try {
      val formatted = "[Parikshan Accessor] $message"
      @Suppress("CAST_NEVER_SUCCEEDS")
      val nsString = formatted as platform.Foundation.NSString
      platform.Foundation.NSLog("%@", nsString)
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

  fun performClickResult(tag: String, selector: Selector?): String {
    val activeSelector = selector ?: tag.takeIf { it.isNotBlank() }?.let { Selector.Auto(it) } ?: Selector.Auto("")
    val node = findNode(tag, selector) ?: return "Node not found for selector: $activeSelector"

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

    val snapshot = snapshotNode(node)
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
    // Prioritize OverlayInputView (actual key/gesture listener in Compose)
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
    if (className.contains("InputView") || className.contains("ComposeView") || className.contains("MetalView")) {
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

  private fun collectGestureRecognizers(view: UIView, list: MutableList<UIGestureRecognizer>) {
    view.gestureRecognizers?.forEach { rec ->
      if (rec is UIGestureRecognizer) {
        list.add(rec)
      }
    }
    view.subviews.forEach { subview ->
      if (subview is UIView) {
        collectGestureRecognizers(subview, list)
      }
    }
  }

  private fun dispatchTouchToAll(touch: SimulatedTouch, phase: UITouchPhase, window: UIWindow, event: UIEvent) {
    val touchSet = NSSet.setWithObject(touch)
    val targetView = touch.view ?: window

    val viewsToNotify = mutableListOf<UIView>()
    viewsToNotify.add(targetView)
    if (targetView != window && targetView.superview != null) {
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

    val recognizers = mutableListOf<UIGestureRecognizer>()
    collectGestureRecognizers(window, recognizers)
    for (rec in recognizers) {
      try {
        when (phase) {
          UITouchPhase.UITouchPhaseBegan -> rec.touchesBegan(touchSet, withEvent = event)
          UITouchPhase.UITouchPhaseMoved -> rec.touchesMoved(touchSet, withEvent = event)
          UITouchPhase.UITouchPhaseEnded -> rec.touchesEnded(touchSet, withEvent = event)
          else -> {}
        }
      } catch (e: Throwable) {
        // Ignore errors from individual gesture recognizers
      }
    }
  }

  private fun findSemanticsNode(tag: String, matchText: String?): SemanticsNode? {
    val activeOwners = io.github.aryapreetam.parikshan.IosSemanticsRegistry.getActiveOwners()
    logDebug("findSemanticsNode: tag='$tag', text='$matchText', activeOwners=${activeOwners.size}")
    if (activeOwners.isEmpty()) return null

    for (owner in activeOwners) {
      @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      var allNodes = owner.getAllSemanticsNodes(mergingEnabled = false)
      var found = if (tag.isNotBlank()) {
        allNodes.find {
          it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == tag
        }
      } else null

      if (found == null && !matchText.isNullOrBlank()) {
        found = allNodes.find { n ->
          val nodeTexts = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
          val nodeTextStr = nodeTexts?.joinToString(" ") { it.text }
          val nodeLabel = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(" ")
          (nodeTextStr != null && nodeTextStr.contains(matchText, ignoreCase = true)) ||
              (nodeLabel != null && matchText != null && nodeLabel.contains(matchText, ignoreCase = true))
        }
      }
      if (found != null) return found

      @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
      allNodes = owner.getAllSemanticsNodes(mergingEnabled = true)
      found = if (tag.isNotBlank()) {
        allNodes.find {
          it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) == tag
        }
      } else null

      if (found == null && !matchText.isNullOrBlank()) {
        found = allNodes.find { n ->
          val nodeTexts = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
          val nodeTextStr = nodeTexts?.joinToString(" ") { it.text }
          val nodeLabel = n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription)?.joinToString(" ")
          (nodeTextStr != null && nodeTextStr.contains(matchText, ignoreCase = true)) ||
              (nodeLabel != null && matchText != null && nodeLabel.contains(matchText, ignoreCase = true))
        }
      }
      if (found != null) return found
    }
    return null
  }

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

    logDebug("Kotlin class info: ${node::class.simpleName}, ${node::class.qualifiedName}")
    try {
      logDebug("Searching for target node via IosSemanticsRegistry active owners.")
      var targetSemanticsNode: SemanticsNode? = findSemanticsNode(tag, snapshot.text)
      logDebug("Found target SemanticsNode via registry: $targetSemanticsNode")
      
      if (targetSemanticsNode != null) {
        var current: SemanticsNode? = targetSemanticsNode
        var setTextAction = current?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsActions.SetText)
        var depth = 0
        while (setTextAction == null && current?.parent != null && depth < 3) {
          current = current?.parent
          setTextAction = current?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsActions.SetText)
          depth++
        }
        if (current != null && setTextAction != null) {
          targetSemanticsNode = current
        }

        // Request focus via semantics if available
        val requestFocusAction = targetSemanticsNode?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsActions.RequestFocus)
        if (requestFocusAction != null) {
          logDebug("Requesting focus via semantics action.")
          requestFocusAction.action?.invoke()
        }
        
        // Set text via semantics
        if (setTextAction != null) {
          logDebug("Invoking SetText semantics action with '$text'")
          val success = setTextAction.action?.invoke(androidx.compose.ui.text.AnnotatedString(text))
          logDebug("SetText action result: $success")
          if (success == true) {
            pumpRunLoop(iterations = 2, intervalSeconds = 0.005)
            return "OK"
          }
        } else {
          logDebug("SetText semantics action not found on node or its parents.")
        }
      }
    } catch (e: Throwable) {
      logDebug("Failed to execute text input via semantics action: ${e.message}")
    }

    val nodeClass = object_getClass(node)
    if (nodeClass != null) {
      val className = class_getName(nodeClass)?.toKString() ?: ""
      logDebug("=== Inspecting Node class name: $className ===")
      
      // Inspect Ivars
      val ivarCountRef = nativeHeap.alloc<UIntVar>()
      try {
        val ivars = class_copyIvarList(nodeClass, ivarCountRef.ptr)
        if (ivars != null) {
          val count = ivarCountRef.value.toInt()
          logDebug("Found $count ivars in Node class $className")
          for (i in 0 until count) {
            val ivar = ivars[i]
            val ivarName = ivar_getName(ivar)?.toKString() ?: ""
            val ivarType = ivar_getTypeEncoding(ivar)?.toKString() ?: ""
            logDebug("  Ivar: name=$ivarName, type=$ivarType")
          }
          platform.posix.free(ivars)
        }
      } finally {
        nativeHeap.free(ivarCountRef)
      }

      // Inspect Methods
      val methodCountRef = nativeHeap.alloc<UIntVar>()
      try {
        val methods = class_copyMethodList(nodeClass, methodCountRef.ptr)
        if (methods != null) {
          val count = methodCountRef.value.toInt()
          logDebug("Found $count methods implemented by Node class $className")
          for (i in 0 until count) {
            val method = methods[i]
            val sel = method_getName(method)
            val methodName = sel_getName(sel)?.toKString() ?: ""
            logDebug("  Method: $methodName")
          }
          platform.posix.free(methods)
        }
      } finally {
        nativeHeap.free(methodCountRef)
      }
    }

    // Focus the text field via accessibilityActivate (Compose's native accessibility handler)
    var focusedViaAccessibility = false
    if (node is NSObject) {
      logDebug("Attempting accessibilityActivate on text field node...")
      try {
        if (node.accessibilityActivate()) {
          logDebug("accessibilityActivate succeeded for focusing text field node.")
          focusedViaAccessibility = true
        } else {
          logDebug("accessibilityActivate returned false for focusing text field node.")
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

    // Allow focus/keyboard state to update by actively polling for first responder
    var firstResponder = findFirstResponder()
    if (firstResponder == null) {
      for (i in 0 until 10) {
        pumpRunLoop(iterations = 1, intervalSeconds = 0.005)
        firstResponder = findFirstResponder()
        if (firstResponder != null) break
      }
    }
    logDebug("findFirstResponder returned: $firstResponder, className=${firstResponder?.let { it::class.simpleName }}, implements UIKeyInput=${firstResponder is UIKeyInputProtocol}")

    if (firstResponder != null) {
      val objcClass = object_getClass(firstResponder)
      if (objcClass != null) {
        val className = class_getName(objcClass)?.toKString() ?: ""
        logDebug("=== Inspecting first responder class name: $className ===")
        
        // Inspect Ivars
        val ivarCountRef = nativeHeap.alloc<UIntVar>()
        try {
          val ivars = class_copyIvarList(objcClass, ivarCountRef.ptr)
          if (ivars != null) {
            val count = ivarCountRef.value.toInt()
            logDebug("Found $count ivars in $className")
            for (i in 0 until count) {
              val ivar = ivars[i]
              val ivarName = ivar_getName(ivar)?.toKString() ?: ""
              val ivarType = ivar_getTypeEncoding(ivar)?.toKString() ?: ""
              logDebug("  Ivar: name=$ivarName, type=$ivarType")
            }
            platform.posix.free(ivars)
          }
        } finally {
          nativeHeap.free(ivarCountRef)
        }

        // Inspect Methods
        val methodCountRef = nativeHeap.alloc<UIntVar>()
        try {
          val methods = class_copyMethodList(objcClass, methodCountRef.ptr)
          if (methods != null) {
            val count = methodCountRef.value.toInt()
            logDebug("Found $count methods implemented by $className")
            for (i in 0 until count) {
              val method = methods[i]
              val sel = method_getName(method)
              val methodName = sel_getName(sel)?.toKString() ?: ""
              logDebug("  Method: $methodName")
            }
            platform.posix.free(methods)
          }
        } finally {
          nativeHeap.free(methodCountRef)
        }
      }
    }

    if (firstResponder == null) {
      return "Active text responder not found"
    }

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
          logDebug("Found Compose text input responder via KVC. Mutating input directly.")
          
          val currentTextLength = snapshot.text?.length ?: 0
          logDebug("Direct clearing existing text of length $currentTextLength.")
          
          input.performSelector(beginSel)
          try {
            repeat(currentTextLength) {
              if (input.respondsToSelector(deleteSel)) {
                input.performSelector(deleteSel)
              }
            }
            logDebug("Direct inserting text: '$text'")
            if (input.respondsToSelector(insertSel)) {
              input.performSelector(insertSel, withObject = NSString.create(string = text))
            }
          } finally {
            input.performSelector(endSel)
          }

          pumpRunLoop(iterations = 10, intervalSeconds = 0.01)
          return "OK"
        }
      }
    }

    if (firstResponder is UIKeyInputProtocol) {
      val keyInput = firstResponder as UIKeyInputProtocol
      
      // Clear existing text by sending backspaces based on current snapshot text length
      val currentTextLength = snapshot.text?.length ?: 0
      logDebug("Clearing existing text of length $currentTextLength.")
      repeat(currentTextLength) {
        keyInput.deleteBackward()
        pumpRunLoop(iterations = 2, intervalSeconds = 0.01)
      }
      // Also fallback to hasText() loop if it returns true
      while (keyInput.hasText()) {
        keyInput.deleteBackward()
        pumpRunLoop(iterations = 2, intervalSeconds = 0.01)
      }
      pumpRunLoop(iterations = 5, intervalSeconds = 0.01)

      logDebug("Inserting text character-by-character: '$text'")
      for (char in text) {
        keyInput.insertText(char.toString())
        pumpRunLoop(iterations = 3, intervalSeconds = 0.01)
      }
      pumpRunLoop(iterations = 10, intervalSeconds = 0.01)

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
