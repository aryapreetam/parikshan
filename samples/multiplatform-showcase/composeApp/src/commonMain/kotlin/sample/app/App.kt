package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// Import navigation & screens
import sample.app.navigation.SampleScreen
import sample.app.navigation.SidebarNavigation
import sample.app.screens.HomeScreen
import sample.app.screens.SimpleGreetDemo

// Import playgrounds
import sample.app.playgrounds.AccessibilityPlayground
import sample.app.playgrounds.FormPlayground
import sample.app.playgrounds.GesturePlayground
import sample.app.playgrounds.NavigationPlayground
import sample.app.playgrounds.OverlayPlayground
import sample.app.playgrounds.ScrollPlayground
import sample.app.playgrounds.SelectorParityPlayground
import sample.app.playgrounds.TimingPlayground

val isDemo = false

@Composable
fun App() {
  MaterialTheme {
    if (isDemo) {
      SimpleGreetDemo()
    } else {
      MultiplatformShowcaseApp()
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplatformShowcaseApp(
  activeScreen: SampleScreen? = null,
  onScreenSelected: ((SampleScreen) -> Unit)? = null
) {
  val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
  val snackbarHostState = remember { SnackbarHostState() }
  val coroutineScope = rememberCoroutineScope()

  var internalActiveScreen by remember { mutableStateOf(SampleScreen.Home) }

  val activeScreenVal = activeScreen ?: internalActiveScreen
  val onScreenSelectedVal = onScreenSelected ?: { internalActiveScreen = it }

  BoxWithConstraints(
    modifier = Modifier.fillMaxSize().background(Color.White)
  ) {
    val isCompact = maxWidth < 700.dp

    if (isCompact) {
      ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
          ModalDrawerSheet(
            drawerContainerColor = Color.White,
            modifier = Modifier.width(280.dp).testTag("navigation_drawer")
          ) {
            SidebarNavigation(
              activeScreen = activeScreenVal,
              onScreenSelected = {
                onScreenSelectedVal(it)
                coroutineScope.launch { drawerState.close() }
              },
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      ) {
        Scaffold(
          topBar = {
            TopAppBar(
              title = { Text("Parikshan Sample") },
              navigationIcon = {
                IconButton(
                  onClick = { coroutineScope.launch { drawerState.open() } },
                  modifier = Modifier.testTag("hamburger_button")
                ) {
                  Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu"
                  )
                }
              }
            )
          },
          snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.testTag("app_snackbar_host")) }
        ) { paddingValues ->
          ContentSurface(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            activeScreen = activeScreenVal,
            onScreenSelected = onScreenSelectedVal,
            snackbarHostState = snackbarHostState
          )
        }
      }
    } else {
      Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState, modifier = Modifier.testTag("app_snackbar_host")) }
      ) { paddingValues ->
        Row(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
          SidebarNavigation(
            activeScreen = activeScreenVal,
            onScreenSelected = onScreenSelectedVal,
            modifier = Modifier.width(240.dp)
          )
          ContentSurface(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            activeScreen = activeScreenVal,
            onScreenSelected = onScreenSelectedVal,
            snackbarHostState = snackbarHostState
          )
        }
      }
    }
  }
}

@Composable
private fun ContentSurface(
  modifier: Modifier,
  activeScreen: SampleScreen,
  onScreenSelected: (SampleScreen) -> Unit,
  snackbarHostState: SnackbarHostState
) {
  Surface(
    modifier = modifier.testTag("content_surface"),
    color = Color.White
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
      when (activeScreen) {
        SampleScreen.Home -> HomeScreen()
        SampleScreen.FormPlayground -> FormPlayground(snackbarHostState = snackbarHostState)
        SampleScreen.OverlayPlayground -> OverlayPlayground()
        SampleScreen.NavigationPlayground -> NavigationPlayground()
        SampleScreen.ScrollPlayground -> ScrollPlayground()
        SampleScreen.GesturePlayground -> GesturePlayground()
        SampleScreen.TimingPlayground -> TimingPlayground()
        SampleScreen.AccessibilityPlayground -> AccessibilityPlayground()
        SampleScreen.SelectorParityPlayground -> SelectorParityPlayground()
      }
    }
  }
}
