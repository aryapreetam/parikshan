@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER", "EXPOSED_SUPER_CLASS", "EXPOSED_PROPERTY_TYPE", "DEPRECATION")
@file:OptIn(
    androidx.compose.ui.InternalComposeUiApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
package io.github.aryapreetam.parikshan

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import io.github.aryapreetam.parikshan.server.IosServer
import platform.UIKit.UIViewController
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireOwner
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.node.Owner

@kotlin.native.concurrent.ThreadLocal
internal object IosSemanticsRegistry {
    private val activeOwners = mutableSetOf<SemanticsOwner>()

    fun injectOwner(owner: Owner) {
        activeOwners.add(owner.semanticsOwner)
    }

    fun removeOwner(owner: Owner) {
        activeOwners.remove(owner.semanticsOwner)
    }

    fun getActiveOwners(): Set<SemanticsOwner> = activeOwners.toSet()
}

/**
 * @suppress
 */
@Suppress("FunctionName")
fun ParikshanUIViewController(content: @Composable () -> Unit): UIViewController {
    IosServer.startIfNeeded()
    return ComposeUIViewController {
        Box(modifier = Modifier.fillMaxSize().then(ParikshanIosSemanticsGrabberElement)) {
            content()
        }
    }
}

private data object ParikshanIosSemanticsGrabberElement : ModifierNodeElement<ParikshanIosSemanticsGrabberNode>() {
    override fun create() = ParikshanIosSemanticsGrabberNode()
    override fun update(node: ParikshanIosSemanticsGrabberNode) {}
    override fun InspectorInfo.inspectableProperties() {
        name = "parikshanIosSemanticsGrabber"
    }
}

private class ParikshanIosSemanticsGrabberNode : Modifier.Node() {
    override fun onAttach() {
        super.onAttach()
        register()
    }

    private fun register() {
        try {
            IosSemanticsRegistry.injectOwner(requireOwner())
        } catch (_: Throwable) {}
    }

    override fun onDetach() {
        try {
            IosSemanticsRegistry.removeOwner(requireOwner())
        } catch (_: Throwable) {}
        super.onDetach()
    }
}
