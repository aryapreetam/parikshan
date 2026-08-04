package org.parikshan.issueplayground

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "issue-playground",
    ) {
        App()
    }
}