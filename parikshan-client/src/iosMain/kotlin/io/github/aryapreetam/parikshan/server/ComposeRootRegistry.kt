@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER", "EXPOSED_SUPER_CLASS", "EXPOSED_PROPERTY_TYPE")
@file:OptIn(
    androidx.compose.ui.InternalComposeUiApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.platform.PlatformContext

internal object ComposeRootRegistry : PlatformContext.RootForTestListener {
    private val _roots = mutableListOf<PlatformRootForTest>()

    val roots: List<PlatformRootForTest>
        get() = _roots.toList()

    override fun onRootForTestCreated(root: PlatformRootForTest) {
        if (!_roots.contains(root)) {
            _roots.add(root)
        }
    }

    override fun onRootForTestDisposed(root: PlatformRootForTest) {
        _roots.remove(root)
    }

    /**
     * Clears all registered roots except the main application root (index 0).
     * This is used to dismiss overlays between tests without losing access to the app.
     */
    fun clear() {
        if (_roots.isEmpty()) return
        val mainRoot = _roots.first()
        _roots.clear()
        _roots.add(mainRoot)
    }

    /**
     * Completely clears all registered roots. Used when the view controller is destroyed.
     */
    fun fullClear() {
        _roots.clear()
    }
}
