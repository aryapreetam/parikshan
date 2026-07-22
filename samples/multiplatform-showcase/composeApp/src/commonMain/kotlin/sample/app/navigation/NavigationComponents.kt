package sample.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun SidebarNavigation(
  activeScreen: SampleScreen,
  onScreenSelected: (SampleScreen) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollState = rememberScrollState()
  Column(
    modifier = modifier
      .fillMaxHeight()
      .background(Color.White)
      .testTag("nav_rail")
      .verticalScroll(scrollState)
      .padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Text("Parikshan Showcase", style = MaterialTheme.typography.titleMedium)
    
    Text("Overview", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    NavigationItemButton("Home", SampleScreen.Home, activeScreen, onScreenSelected, "nav_home_screen")
    
    Text("E2E Playgrounds", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    NavigationItemButton("Form Playground", SampleScreen.FormPlayground, activeScreen, onScreenSelected, "nav_form_playground")
    NavigationItemButton("Overlay Playground", SampleScreen.OverlayPlayground, activeScreen, onScreenSelected, "nav_overlay_playground")
    NavigationItemButton("Navigation Playground", SampleScreen.NavigationPlayground, activeScreen, onScreenSelected, "nav_navigation_playground")
    NavigationItemButton("Scroll Playground", SampleScreen.ScrollPlayground, activeScreen, onScreenSelected, "nav_scroll_playground")
    NavigationItemButton("Gesture Playground", SampleScreen.GesturePlayground, activeScreen, onScreenSelected, "nav_gesture_playground")
    NavigationItemButton("Timing Playground", SampleScreen.TimingPlayground, activeScreen, onScreenSelected, "nav_timing_playground")
    NavigationItemButton("A11y & Semantics", SampleScreen.AccessibilityPlayground, activeScreen, onScreenSelected, "nav_accessibility_playground")
    NavigationItemButton("Selector Parity", SampleScreen.SelectorParityPlayground, activeScreen, onScreenSelected, "nav_selector_parity_playground")
  }
}

@Composable
fun NavigationItemButton(
  label: String,
  target: SampleScreen,
  activeScreen: SampleScreen,
  onScreenSelected: (SampleScreen) -> Unit,
  testTag: String
) {
  val isSelected = activeScreen == target
  Button(
    onClick = { onScreenSelected(target) },
    colors = ButtonDefaults.buttonColors(
      containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
      contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    ),
    modifier = Modifier.fillMaxWidth().testTag(testTag)
  ) {
    Text(label)
  }
}
