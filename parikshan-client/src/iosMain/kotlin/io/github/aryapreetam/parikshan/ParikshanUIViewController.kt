@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
package io.github.aryapreetam.parikshan

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireOwner
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.window.ComposeUIViewController
import io.github.aryapreetam.parikshan.server.IosSemanticsAccessor
import io.github.aryapreetam.parikshan.server.ParikshanIosServer
import platform.UIKit.UIViewController
import androidx.compose.foundation.layout.Box

/**
 * Replaces ComposeUIViewController to automatically start the Parikshan in-app HTTP server
 * and grab the SemanticsOwner for E2E testing without requiring per-node bridges.
 */
@Suppress("FunctionName")
fun ParikshanUIViewController(content: @Composable () -> Unit): UIViewController {
    ParikshanIosServer.startIfNeeded()
    return ComposeUIViewController {
        Box(modifier = ParikshanSemanticsGrabberElement) {
            content()
        }
    }
}

private data object ParikshanSemanticsGrabberElement : ModifierNodeElement<ParikshanSemanticsGrabberNode>() {
    override fun create() = ParikshanSemanticsGrabberNode()
    override fun update(node: ParikshanSemanticsGrabberNode) {}
    override fun InspectorInfo.inspectableProperties() {
        name = "parikshanSemanticsGrabber"
    }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
private class ParikshanSemanticsGrabberNode : Modifier.Node() {
    override fun onAttach() {
        super.onAttach()
        IosSemanticsAccessor.injectOwner(requireOwner())
    }
}
