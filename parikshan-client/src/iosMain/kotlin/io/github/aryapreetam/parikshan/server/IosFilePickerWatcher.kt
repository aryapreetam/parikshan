@file:OptIn(
  kotlinx.cinterop.ExperimentalForeignApi::class,
  kotlinx.cinterop.BetaInteropApi::class
)

package io.github.aryapreetam.parikshan.server

import io.github.aryapreetam.parikshan.protocol.PickerAction
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.cinterop.*
import platform.Foundation.NSItemProvider
import platform.Foundation.NSURL
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.darwin.NSObject
import platform.objc.*
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference

object IosFilePickerWatcher {

  private val swizzled = AtomicInt(0)
  private val originalPresentImp = AtomicReference<COpaquePointer?>(null)
  private val activeAction = AtomicReference<PickerAction?>(null)
  private val wasHandled = AtomicInt(0)
  private val wasCancelled = AtomicInt(0)
  private val startTimeMs = kotlin.concurrent.AtomicLong(0L)
  private val elapsedMs = kotlin.concurrent.AtomicLong(0L)
  private val selectedPaths = AtomicReference<List<String>>(emptyList())
  private val phPickerKey: COpaquePointer = platform.posix.malloc(1u)!!
  private val phPickerSubclassRegistered = AtomicInt(0)

  fun setup() {
    if (!swizzled.compareAndSet(0, 1)) return

    try {
      val vcCls = objc_getClass("UIViewController") as? ObjCClass ?: return
      val sel = sel_registerName("presentViewController:animated:completion:")
      val method = class_getInstanceMethod(vcCls, sel) ?: return

      val newImp = staticCFunction<COpaquePointer?, COpaquePointer?, COpaquePointer?, Boolean, COpaquePointer?, Unit> { self, _cmd, vc, animated, completion ->
        if (IosFilePickerWatcher.handlePresentation(vc)) {
          return@staticCFunction
        }
        val orig = IosFilePickerWatcher.originalPresentImp.value
        if (orig != null) {
          val call = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, Boolean, COpaquePointer?) -> Unit>>(orig.rawValue)
          call?.invoke(self, _cmd, vc, animated, completion)
        }
      }

      val prevImp = method_setImplementation(method, newImp.reinterpret())
      originalPresentImp.value = prevImp
    } catch (e: Throwable) {
      println("Parikshan: Failed to swizzle UIViewController.presentViewController: ${e.message}")
    }
  }

  private fun currentTimeMillis(): Long = memScoped {
    val tv = alloc<platform.posix.timeval>()
    platform.posix.gettimeofday(tv.ptr, null)
    (tv.tv_sec * 1000L) + (tv.tv_usec / 1000L)
  }

  fun register(commandId: String, action: PickerAction, timeoutMs: Long): Response {
    setup()
    wasHandled.value = 0
    wasCancelled.value = 0
    selectedPaths.value = emptyList()
    elapsedMs.value = 0L
    startTimeMs.value = currentTimeMillis()
    activeAction.value = action
    return Response.Ok(commandId)
  }

  fun unregister(commandId: String, awaitCompletion: Boolean, timeoutMs: Long): Response {
    val deadlineMs = currentTimeMillis() + timeoutMs
    if (awaitCompletion && wasHandled.value == 0) {
      while (wasHandled.value == 0 && currentTimeMillis() <= deadlineMs) {
        platform.posix.usleep(10_000u)
      }
    }

    activeAction.value = null
    val handled = wasHandled.value == 1
    val cancelled = wasCancelled.value == 1
    val paths = selectedPaths.value

    return Response.FilePickerHandled(
      id = commandId,
      wasHandled = handled,
      selectedPaths = paths,
      wasCancelled = cancelled,
      elapsedMs = elapsedMs.value
    )
  }

  private fun ensurePhPickerSubclass() {
    if (!phPickerSubclassRegistered.compareAndSet(0, 1)) return
    try {
      memScoped {
        val fnGetClass = interpretCPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>(
          platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "objc_getClass")!!.rawValue
        ) ?: return
        val fnAllocate = interpretCPointer<CFunction<(COpaquePointer?, CPointer<ByteVar>?, ULong) -> COpaquePointer?>>(
          platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "objc_allocateClassPair")!!.rawValue
        ) ?: return
        val fnRegisterName = interpretCPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>(
          platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "sel_registerName")!!.rawValue
        ) ?: return
        val fnGetMethod = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?) -> COpaquePointer?>>(
          platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "class_getInstanceMethod")!!.rawValue
        ) ?: return
        val fnGetTypeEncoding = interpretCPointer<CFunction<(COpaquePointer?) -> CPointer<ByteVar>?>>(
          platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "method_getTypeEncoding")!!.rawValue
        ) ?: return
        val fnAddMethod = interpretCPointer<CFunction<(COpaquePointer?, COpaquePointer?, COpaquePointer?, CPointer<ByteVar>?) -> Boolean>>(
          platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "class_addMethod")!!.rawValue
        ) ?: return
        val fnRegister = interpretCPointer<CFunction<(COpaquePointer?) -> Unit>>(
          platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "objc_registerClassPair")!!.rawValue
        ) ?: return

        val parentClass = fnGetClass("PHPickerResult".cstr.ptr) ?: return
        val customClass = fnAllocate(parentClass, "ParikshanPHPickerResult".cstr.ptr, 0u) ?: return
        val sel = fnRegisterName("itemProvider".cstr.ptr) ?: return
        val method = fnGetMethod(parentClass, sel)
        val types = if (method != null) fnGetTypeEncoding(method) else "@@:".cstr.ptr

        val imp = staticCFunction<COpaquePointer?, COpaquePointer?, COpaquePointer?> { self, _ ->
          if (self == null) return@staticCFunction null
          val selfObj = interpretObjCPointer<Any>(self.rawValue)
          val provider = objc_getAssociatedObject(selfObj, IosFilePickerWatcher.phPickerKey) as? NSObject
          provider?.objcPtr()?.let { interpretCPointer<CPointed>(it) }
        }

        fnAddMethod(customClass, sel, imp.reinterpret(), types ?: "@@:".cstr.ptr)
        fnRegister(customClass)
      }
    } catch (e: Throwable) {
      println("Parikshan: Failed to register ParikshanPHPickerResult: ${e.message}")
    }
  }

  private fun createPhPickerResult(path: String): PHPickerResult? {
    ensurePhPickerSubclass()
    val fnGetClass = interpretCPointer<CFunction<(CPointer<ByteVar>?) -> COpaquePointer?>>(
      platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "objc_getClass")!!.rawValue
    ) ?: return null
    val fnCreateInstance = interpretCPointer<CFunction<(COpaquePointer?, ULong) -> COpaquePointer?>>(
      platform.posix.dlsym(platform.posix.RTLD_DEFAULT, "class_createInstance")!!.rawValue
    ) ?: return null

    val instancePtr = memScoped {
      val cls = fnGetClass("ParikshanPHPickerResult".cstr.ptr) ?: return null
      fnCreateInstance(cls, 0u)
    } ?: return null

    val fileUrl = NSURL.fileURLWithPath(path)
    val itemProvider = NSItemProvider(contentsOfURL = fileUrl)
    itemProvider.suggestedName = fileUrl.lastPathComponent

    val instance = interpretObjCPointer<Any>(instancePtr.rawValue)
    objc_setAssociatedObject(
      instance,
      phPickerKey,
      itemProvider,
      1u
    )
    return instance as? PHPickerResult
  }

  internal fun handlePresentation(vcPtr: COpaquePointer?): Boolean {
    val action = activeAction.value ?: return false
    if (vcPtr == null) return false

    val obj = interpretObjCPointer<Any>(vcPtr.rawValue)
    val desc = obj.toString()
    val isDocPicker = (obj is UIDocumentPickerViewController) || desc.contains("DocumentPicker", ignoreCase = true)
    val isPhPicker = (obj is PHPickerViewController) || desc.contains("PHPicker", ignoreCase = true)

    if (!isDocPicker && !isPhPicker) return false

    if (!wasHandled.compareAndSet(0, 1)) return true

    elapsedMs.value = currentTimeMillis() - startTimeMs.value

    if (isDocPicker) {
      val picker = obj as? UIDocumentPickerViewController
        ?: interpretObjCPointer<UIDocumentPickerViewController>(vcPtr.rawValue)
      val delegate = picker.delegate

      when (action) {
        is PickerAction.Cancel -> {
          wasCancelled.value = 1
          delegate?.documentPickerWasCancelled(picker)
        }

        is PickerAction.Select -> {
          selectedPaths.value = action.paths
          val urls = action.paths.map { path ->
            NSURL.fileURLWithPath(path)
          }
          delegate?.documentPicker(picker, didPickDocumentsAtURLs = urls)
        }
      }
      return true
    }

    if (isPhPicker) {
      val picker = obj as? PHPickerViewController
        ?: interpretObjCPointer<PHPickerViewController>(vcPtr.rawValue)
      val delegate = picker.delegate

      when (action) {
        is PickerAction.Cancel -> {
          wasCancelled.value = 1
          delegate?.picker(picker, didFinishPicking = emptyList<Any>())
        }

        is PickerAction.Select -> {
          selectedPaths.value = action.paths
          val results = action.paths.mapNotNull { path -> createPhPickerResult(path) }
          delegate?.picker(picker, didFinishPicking = results)
        }
      }
      return true
    }

    return true
  }
}
