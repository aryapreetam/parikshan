package org.example.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource

import cmp_latest.app.shared.generated.resources.Res
import cmp_latest.app.shared.generated.resources.compose_multiplatform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Preview
fun App() {
    MaterialTheme {
        var showContent by remember { mutableStateOf(false) }
        var dropdownExpanded by remember { mutableStateOf(false) }
        var selectedColor by remember { mutableStateOf("") }
        var showDialog by remember { mutableStateOf(false) }

        val colors = remember {
            listOf(
                "Red", "Blue", "Green", "Yellow", "Orange", "Purple", "Pink", "Brown", 
                "Black", "White", "Gray", "Cyan", "Magenta", "Gold", "Silver", "Bronze", 
                "Turquoise", "Lavender", "Maroon", "Navy", "Teal"
            )
        }

        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .safeContentPadding()
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = { showContent = !showContent }) {
                Text("Click me!")
            }
            AnimatedVisibility(showContent) {
                val greeting = remember { Greeting().greet() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(Res.drawable.compose_multiplatform),
                        contentDescription = null,
                        modifier = Modifier.size(150.dp)
                    )
                    Text("Compose: $greeting")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dropdown Playground Section
            Box {
                Button(onClick = { dropdownExpanded = true }) {
                    Text("Select Color")
                }
                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.testTag("dropdown_menu").heightIn(max = 200.dp)
                ) {
                    colors.forEach { color ->
                        DropdownMenuItem(
                            text = { Text(color) },
                            onClick = {
                                selectedColor = color
                                dropdownExpanded = false
                                showDialog = true
                            }
                        )
                    }
                }
            }

            if (showDialog) {
                AlertDialog(
                    onDismissRequest = { showDialog = false },
                    title = { Text("Color Selected") },
                    text = { Text("Chosen color was: $selectedColor") },
                    confirmButton = {
                        Button(onClick = { showDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }
}