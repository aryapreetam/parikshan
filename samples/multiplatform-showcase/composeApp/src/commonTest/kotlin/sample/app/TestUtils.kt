package sample.app

import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlinx.coroutines.delay

/**
 * Ensures the app navigation (drawer/rail) is accessible.
 */
suspend fun E2ETestScope.openAppNavigation() {
    // If the persistent sidebar or drawer items are already visible, no need to open a drawer                                                                       
    if (hasVisibleNode("nav_rail") || hasVisibleNode("nav_home_screen")) {                                                                                             
        return                                                                                                                                                       
    }  

    // 2. Wait for hamburger button to appear, click it, and wait for navigation menu
    waitFor("hamburger_button")
    click("hamburger_button")
    waitFor("nav_home_screen")
}

/**
 * Navigates to a specific section by clicking its navigation item,
 * scrolling the navigation container if necessary.
 */
suspend fun E2ETestScope.navigateToSection(navTag: String) {
    openAppNavigation()                                                                                                                                              
                                                                                                                                                                         
    val tree = getTree()                                                                                                                                             
    val navContainer = when {                                                                                                                                        
        tree.any { it.tag == "nav_rail" && it.visible } -> "nav_rail"                                                                                                
        tree.any { it.tag == "navigation_drawer" && it.visible } -> "navigation_drawer"                                                                              
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
