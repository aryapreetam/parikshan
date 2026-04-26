@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
package io.github.aryapreetam.parikshan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireOwner
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.window.ComposeViewport
import io.github.aryapreetam.parikshan.server.WasmSemanticsAccessor
import org.w3c.dom.HTMLElement

/**
 * Initializes the Parikshan bridge for Wasm.
 */
fun initializeParikshanWasm() {
    ParikshanTagBridgeHooks.ensureBridgeInstalled()
}

/**
 * Replaces ComposeViewport to automatically grab the SemanticsOwner for E2E testing in Wasm.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("FunctionName")
fun ParikshanComposeViewport(viewportContainer: HTMLElement, content: @Composable () -> Unit) {
    ComposeViewport(viewportContainer) {
        Box(modifier = Modifier.fillMaxSize().then(ParikshanSemanticsGrabberElement)) {
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
        try {
            WasmSemanticsAccessor.injectOwner(requireOwner())
        } catch (_: Throwable) {}
    }
}
