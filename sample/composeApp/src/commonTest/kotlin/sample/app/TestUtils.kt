package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlinx.coroutines.delay

/**
 * Ensures the app navigation (drawer/rail) is accessible.
 */
suspend fun E2ETestScope.openAppNavigation() {
    // 1. If we can see a primary navigation marker, we're likely already in Wide mode or drawer is open
    if (hasVisibleNode("nav_task_list")) {
        return
    }

    // 2. Try to open the drawer
    if (hasVisibleNode("hamburger_button")) {
        click("hamburger_button")
        // Wait for drawer to appear
        waitFor("nav_task_list")
    }
}

/**
 * Navigates to a specific section by clicking its navigation item,
 * scrolling the navigation container if necessary.
 */
suspend fun E2ETestScope.navigateToSection(navTag: String) {
    openAppNavigation()
    
    // On Wasm, the navigation rail/drawer might need scrolling if items are clipped.
    // We look for the 'nav_rail' or 'navigation_drawer' tags.
    val tree = getTree()
    val navContainer = when {
        tree.any { it.tag == "nav_rail" } -> "nav_rail"
        tree.any { it.tag == "navigation_drawer" } -> "navigation_drawer"
        else -> null
    }
    
    if (navContainer != null) {
        scrollUntilVisible(
            containerSelector = Selector.Tag(navContainer),
            targetSelector = Selector.Tag(navTag)
        )
    }
    
    click(navTag)
}

/**
 * Retries a block until it returns true or max attempts reached.
 */
suspend fun retry(
    maxAttempts: Int = 3,
    delayMs: Long = 500,
    block: suspend () -> Boolean
): Boolean {
    repeat(maxAttempts) {
        if (block()) return true
        delay(delayMs)
    }
    return false
}

/**
 * Utility to scroll a container until a target becomes visible.
 */
suspend fun E2ETestScope.scrollUntilVisible(
  containerSelector: Selector,
  targetSelector: Selector,
  direction: ScrollDirection = ScrollDirection.Down,
  maxScrolls: Int = 30
) {
  repeat(maxScrolls) {
    if (hasVisibleNode(targetSelector)) return
    scroll(containerSelector, direction)
    delay(300)
  }
}
