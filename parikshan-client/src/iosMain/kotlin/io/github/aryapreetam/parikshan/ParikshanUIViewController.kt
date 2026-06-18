@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER", "EXPOSED_SUPER_CLASS", "EXPOSED_PROPERTY_TYPE")
@file:OptIn(
    androidx.compose.ui.InternalComposeUiApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package io.github.aryapreetam.parikshan

import androidx.compose.runtime.*
import io.github.aryapreetam.parikshan.server.ParikshanIosServer
import io.github.aryapreetam.parikshan.server.ComposeRootRegistry
import io.github.aryapreetam.parikshan.server.IosSemanticsAccessor
import platform.UIKit.*
import kotlinx.cinterop.*
import platform.CoreGraphics.*
import androidx.compose.ui.scene.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.window.MetalView
import androidx.compose.ui.viewinterop.*
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.pointer.*
import platform.Foundation.*

@Suppress("FunctionName")
fun ParikshanUIViewController(content: @Composable () -> Unit): UIViewController {
    ParikshanIosServer.startIfNeeded()
    return ParikshanTestViewController(content)
}

internal class TestPlatformContext(
    private val sizeProvider: () -> IntSize,
    private val insetsProvider: () -> CValue<UIEdgeInsets>,
    private val scaleProvider: () -> Float
) : PlatformContext.Empty() {
    override val rootForTestListener: PlatformContext.RootForTestListener
        get() = ComposeRootRegistry
    
    override val windowInfo: WindowInfo = object : WindowInfo {
        override val containerSize: IntSize get() = sizeProvider()
        override val isWindowFocused: Boolean get() = true
        override val keyboardModifiers: PointerKeyboardModifiers get() = PointerKeyboardModifiers(0)
    }

    override val windowInsets: PlatformWindowInsets = object : PlatformWindowInsets {
        override val statusBars: PlatformInsets = object : PlatformInsets {
            override val left: Int get() = 0
            override val top: Int get() = (insetsProvider().useContents<UIEdgeInsets, Double> { top } * scaleProvider()).toInt()
            override val right: Int get() = 0
            override val bottom: Int get() = 0
        }
        override val navigationBars: PlatformInsets = object : PlatformInsets {
            override val left: Int get() = 0
            override val top: Int get() = 0
            override val right: Int get() = 0
            override val bottom: Int get() = (insetsProvider().useContents<UIEdgeInsets, Double> { bottom } * scaleProvider()).toInt()
        }
        override val systemBars: PlatformInsets = object : PlatformInsets {
            override val left: Int get() = 0
            override val top: Int get() = statusBars.top
            override val right: Int get() = 0
            override val bottom: Int get() = navigationBars.bottom
        }
        override val displayCutout: PlatformInsets get() = statusBars
        override val mandatorySystemGestures: PlatformInsets get() = navigationBars
        override val systemGestures: PlatformInsets get() = systemBars
        override val tappableElement: PlatformInsets get() = systemBars
        override val waterfall: PlatformInsets get() = PlatformInsets.Zero
    }
}

@ExportObjCClass
private class ParikshanTestViewController(
    private val content: @Composable () -> Unit
) : UIViewController(nibName = null, bundle = null) {

    private var scene: ComposeScene? = null
    private var metalView: MetalView? = null
    
    private var currentSize by mutableStateOf(IntSize.Zero)
    private var currentInsets by mutableStateOf(platform.UIKit.UIEdgeInsetsZero.readValue())
    private var keyboardHeight = 0.0

    private val activeTouches = mutableSetOf<UITouch>()
    private val touchIdMap = mutableMapOf<UITouch, Long>()
    private var nextTouchId = 0L

    private fun dispatchPointerEvent(eventType: PointerEventType) {
        val scene = scene ?: return
        val scale = UIScreen.mainScreen.scale
        
        val pointers = activeTouches.map { touch ->
            val id = touchIdMap.getOrPut(touch) { nextTouchId++ }
            val location = touch.locationInView(this.view)
            val x = location.useContents<CGPoint, Double> { x } * scale
            val y = location.useContents<CGPoint, Double> { y } * scale
            
            ComposeScenePointer(
                id = PointerId(id),
                position = androidx.compose.ui.geometry.Offset(x.toFloat(), y.toFloat()),
                pressed = touch.phase != UITouchPhase.UITouchPhaseEnded && touch.phase != UITouchPhase.UITouchPhaseCancelled,
                type = PointerType.Touch
            )
        }
        
        if (pointers.isNotEmpty()) {
            scene.sendPointerEvent(
                eventType = eventType,
                pointers = pointers
            )
        }
    }

    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
        touches.forEach { activeTouches.add(it as UITouch) }
        dispatchPointerEvent(PointerEventType.Press)
    }
    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent?) {
        dispatchPointerEvent(PointerEventType.Move)
    }
    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        dispatchPointerEvent(PointerEventType.Release)
        touches.forEach { 
            val t = it as UITouch
            activeTouches.remove(t)
            touchIdMap.remove(t)
        }
        if (activeTouches.isEmpty()) nextTouchId = 0
    }
    override fun touchesCancelled(touches: Set<*>, withEvent: UIEvent?) {
        dispatchPointerEvent(PointerEventType.Release)
        touches.forEach { 
            val t = it as UITouch
            activeTouches.remove(t)
            touchIdMap.remove(t)
        }
        if (activeTouches.isEmpty()) nextTouchId = 0
    }

    override fun loadView() {
        super.loadView()
        val screenBounds = UIScreen.mainScreen.bounds
        val scale = UIScreen.mainScreen.scale.toFloat()
        
        val mView = MetalView(
            retrieveInteropTransaction = {
                object : UIKitInteropTransaction {
                    override val actions: List<UIKitInteropAction> = emptyList()
                    override val isInteropActive: Boolean = false
                }
            },
            useSeparateRenderThreadWhenPossible = false,
            render = { canvas, nanoTime ->
                scene?.render(canvas.asComposeCanvas(), nanoTime)
            }
        )
        mView.setFrame(screenBounds)
        mView.backgroundColor = UIColor.whiteColor
        mView.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
        
        this.view = mView
        this.view.backgroundColor = UIColor.whiteColor
        this.view.setUserInteractionEnabled(true)
        this.view.setMultipleTouchEnabled(true)
        
        this.metalView = mView

        val w = screenBounds.useContents<platform.CoreGraphics.CGRect, Double> { size.width }.toFloat() * scale
        val h = screenBounds.useContents<platform.CoreGraphics.CGRect, Double> { size.height }.toFloat() * scale
        currentSize = IntSize(w.toInt(), h.toInt())

        val s = CanvasLayersComposeScene(
            density = Density(scale),
            size = currentSize,
            coroutineContext = Dispatchers.Main,
            platformContext = TestPlatformContext(
                sizeProvider = { currentSize },
                insetsProvider = { currentInsets },
                scaleProvider = { scale }
            ),
            invalidate = {
                mView.redrawer.setNeedsRedraw()
            }
        )
        this.scene = s
        IosSemanticsAccessor.currentScene = s

        s.setContent(content)
        
        setupKeyboardObservers()
    }

    private fun setupKeyboardObservers() {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIKeyboardWillShowNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            val userInfo = notification?.userInfo
            val keyboardFrame = userInfo?.get(UIKeyboardFrameEndUserInfoKey) as? NSValue
            keyboardFrame?.let {
                val rect = it.CGRectValue
                keyboardHeight = rect.useContents<CGRect, Double> { size.height }
                updateInsets()
            }
        }

        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIKeyboardWillHideNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            keyboardHeight = 0.0
            updateInsets()
        }
    }

    private fun updateInsets() {
        val safeArea = view.safeAreaInsets
        currentInsets = safeArea.useContents {
            UIEdgeInsetsPoint(
                top = top,
                left = 0.0,
                bottom = maxOf(bottom, keyboardHeight),
                right = 0.0
            )
        }
        scene?.invalidatePositionInWindow()
    }

    override fun viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        val bounds = view.bounds
        metalView?.setFrame(bounds)
        updateInsets()
        
        val scale = UIScreen.mainScreen.scale.toFloat()
        val w = bounds.useContents<platform.CoreGraphics.CGRect, Double> { size.width }.toFloat() * scale
        val h = bounds.useContents<platform.CoreGraphics.CGRect, Double> { size.height }.toFloat() * scale
        currentSize = IntSize(w.toInt(), h.toInt())
        scene?.size = currentSize
    }

    override fun viewDidDisappear(animated: Boolean) {
        super.viewDidDisappear(animated)
        scene?.close()
        scene = null
        IosSemanticsAccessor.currentScene = null
        ComposeRootRegistry.fullClear()
    }
}

private fun UIEdgeInsetsPoint(top: Double, left: Double, bottom: Double, right: Double): CValue<UIEdgeInsets> = 
    cValue<UIEdgeInsets> {
        this.top = top
        this.left = left
        this.bottom = bottom
        this.right = right
    }
