package com.example.app

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.composeunstyled.*
import com.composables.ui.components.Text
import com.composables.ui.theme.ComposablesTheme

@Composable
fun App() {
  ComposablesTheme {
    var selectedColor by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    val colors = remember {
      listOf(
        "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Pink", "Brown", 
        "Black", "White", "Gray", "Cyan", "Magenta", "Gold", "Silver", "Bronze", 
        "Turquoise", "Lavender", "Maroon", "Navy", "Teal"
      )
    }

    Box(
      modifier = Modifier.safeDrawingPadding().fillMaxSize().padding(16.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
      ) {
        Text(
          text = "Hello Beautiful World!",
          textAlign = TextAlign.Center,
        )
        Text(
          text = "Go to App.kt to edit your app",
          textAlign = TextAlign.Center,
        )
        Text(
          text =
            "Pro tip: Use the `dev` configuration in your IDE to auto-reload your app when you edit your code",
          textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box {
          UnstyledDropdownMenu(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = it },
            anchor = {
              Box(
                modifier = Modifier
                  .clickable { menuExpanded = true }
                  .padding(horizontal = 16.dp, vertical = 8.dp)
              ) {
                Text("Select Color")
              }
            },
            panel = {
              DropdownMenuPanel(
                modifier = Modifier
                  .testTag("dropdown_menu")
                  .background(Color.White, RoundedCornerShape(8.dp))
                  .width(200.dp)
                  .heightIn(max = 200.dp)
                  .verticalScroll(rememberScrollState())
                  .padding(4.dp)
              ) {
                colors.forEach { color ->
                  UnstyledDropdownMenuItem(
                    onClick = {
                      selectedColor = color
                      menuExpanded = false
                      showDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                  ) {
                    Text(text = color)
                  }
                }
              }
            }
          )
        }
      }

      UnstyledDialog(
        visible = showDialog,
        onDismissRequest = { showDialog = false },
        overlay = {
          Scrim(
            modifier = Modifier
              .fillMaxSize()
              .background(Color.Black.copy(alpha = 0.5f))
          )
        }
      ) {
        val dialogScope = this
        Box(
          modifier = Modifier.fillMaxSize(),
          contentAlignment = Alignment.Center
        ) {
          dialogScope.DialogPanel(
            modifier = Modifier
              .background(Color.White, RoundedCornerShape(12.dp))
              .padding(24.dp)
              .width(280.dp)
          ) {
            Column(
              verticalArrangement = Arrangement.spacedBy(16.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = "Color Selected",
                textAlign = TextAlign.Center
              )
              Text(
                text = "Chosen color was: $selectedColor",
                textAlign = TextAlign.Center
              )
              Box(
                modifier = Modifier
                  .clickable { showDialog = false }
                  .padding(horizontal = 16.dp, vertical = 8.dp)
              ) {
                Text("OK")
              }
            }
          }
        }
      }
    }
  }
}

@Preview
@Composable
fun AppPreview() {
  App()
}
