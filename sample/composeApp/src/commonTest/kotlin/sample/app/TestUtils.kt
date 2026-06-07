package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlinx.coroutines.delay

import io.github.aryapreetam.parikshan.protocol.first

suspend fun E2ETestScope.openAppNavigation() {
    // 1. If we can see a primary navigation marker, we're likely already in Wide mode or drawer is open
    if (hasVisibleNode("nav_task_list")) {
        return
    }

    // 2. Try to open the drawer
    var attempts = 0
    while (attempts < 3) {
        if (hasVisibleNode("hamburger_button")) {
            click("hamburger_button")
            // Wait for drawer to appear
            val drawerVisible = retry(maxAttempts = 5, delayMs = 200) {
                hasVisibleNode("nav_task_list")
            }
            if (drawerVisible) return
        }
        attempts++
        delay(500)
    }
}

suspend fun E2ETestScope.scrollUntilVisible(
  containerSelector: Selector,
  targetSelector: Selector,
  direction: ScrollDirection = ScrollDirection.Down,
  maxScrolls: Int = 30
) {
  repeat(maxScrolls + 1) { attempt ->
    if (hasVisibleNode(targetSelector)) {
      return
    }
    if (attempt == maxScrolls) {
      println("scrollUntilVisible timed out waiting for ${targetSelector.raw}. Printing visible tree nodes:")
      runCatching {
        getTree().forEach { node ->
          if (node.visible) {
            println("  Node: tag='${node.tag}', text='${node.text}', bounds=${node.bounds}")
          }
        }
      }
      throw AssertionError(
        "Could not make '${targetSelector.raw}' visible after $maxScrolls scroll actions"
      )
    }
    scroll(containerSelector, direction)
    delay(200)
  }
}
