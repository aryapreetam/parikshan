package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.InternalParikshanApi
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlinx.coroutines.delay

/**
 * Ensures the app navigation (drawer/rail) is accessible.
 */
@OptIn(InternalParikshanApi::class)
suspend fun E2ETestScope.openAppNavigation() {
    executeParallel {
        if (hasVisibleNode("hamburger_button")) {
            if (!hasVisibleNode("navigation_drawer")) {
                click("hamburger_button")
                waitFor("navigation_drawer")
            }
        }
    }
}

/**
 * Navigates to a specific section by clicking its navigation item,
 * scrolling the navigation container if necessary.
 */
@OptIn(InternalParikshanApi::class)
suspend fun E2ETestScope.navigateToSection(navTag: String) {
    openAppNavigation()

    executeParallel {
        val tree = getTree()
        val navContainer = when {
            tree.any { it.tag == "navigation_drawer" && it.visible } -> "navigation_drawer"
            tree.any { it.tag == "nav_rail" && it.visible } -> "nav_rail"
            else -> null
        }

        if (navContainer != null) {
            val sectionOrder = listOf(
                "nav_home_screen",
                "nav_form_playground",
                "nav_overlay_playground",
                "nav_navigation_playground",
                "nav_scroll_playground",
                "nav_gesture_playground",
                "nav_timing_playground",
                "nav_accessibility_playground"
            )

            val targetIndex = sectionOrder.indexOf(navTag)
            val currentIndex = sectionOrder.indexOfFirst { hasVisibleNode(it) }

            val scrollDirection = if (currentIndex != -1 && targetIndex < currentIndex) {
                ScrollDirection.Up
            } else {
                ScrollDirection.Down
            }

            scrollUntilVisible(
                containerSelector = Selector.Tag(navContainer),
                targetSelector = Selector.Tag(navTag),
                direction = scrollDirection
            )
        }

        click(navTag)
    }
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
