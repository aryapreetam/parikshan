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
        val initialTree = getTree()
        if (initialTree.any { it.tag == "navigation_drawer" && it.visible }) {
            return@executeParallel
        }
        val hasRail = initialTree.any { it.tag == "nav_rail" && it.visible }
        val hasHamburger = initialTree.any { it.tag == "hamburger_button" && it.visible }
        if (hasRail && !hasHamburger) {
            return@executeParallel
        }
        hideKeyboard()
        if (!hasHamburger) {
            waitFor("hamburger_button")
        }
        click("hamburger_button")
        waitFor("navigation_drawer")
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
        val visibleTags = tree.asSequence().filter { it.visible }.mapNotNull { it.tag }.toHashSet()
        val navContainer = when {
            "navigation_drawer" in visibleTags -> "navigation_drawer"
            "nav_rail" in visibleTags -> "nav_rail"
            else -> null
        }

        if (navContainer != null && navTag !in visibleTags) {
            val sectionOrder = listOf(
                "nav_home_screen",
                "nav_form_playground",
                "nav_overlay_playground",
                "nav_navigation_playground",
                "nav_scroll_playground",
                "nav_gesture_playground",
                "nav_timing_playground",
                "nav_accessibility_playground",
                "nav_selector_parity_playground",
                "nav_file_picker_playground"
            )

            val targetIndex = sectionOrder.indexOf(navTag)
            val currentIndex = sectionOrder.indexOfFirst { it in visibleTags }

            val scrollDirection = if (currentIndex != -1 && targetIndex < currentIndex) {
                ScrollDirection.Up
            } else {
                ScrollDirection.Down
            }

            val scrollTargetContainer = if ("nav_rail" in visibleTags) "nav_rail" else navContainer
            scrollUntilVisible(
                containerSelector = Selector.Tag(scrollTargetContainer),
                targetSelector = Selector.Tag(navTag),
                direction = scrollDirection
            )
        }

        click(navTag)
        if (navContainer == "navigation_drawer") {
            assertNotVisible("navigation_drawer")
        }
    }
}


