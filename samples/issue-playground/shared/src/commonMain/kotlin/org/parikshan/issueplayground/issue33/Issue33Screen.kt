package org.parikshan.issueplayground.issue33

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun Issue33Screen() {
    val filterTypes = remember { listOf("Source", "Destination", "Internal", "All") }

    // Section 1: Exact Issue #33 search field + device-type filter menu
    var query by remember { mutableStateOf("") }
    var debouncedCalls by remember { mutableIntStateOf(0) }
    var menuExpanded by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") }
    var postClearCount by remember { mutableIntStateOf(0) }

    // Mirrors 500ms search debounce (delays callback only, not controlled text updates)
    LaunchedEffect(query) {
        delay(500)
        debouncedCalls += 1
    }

    // Section 2: Cursor-position replacement and clearing verification
    var cursorField by remember { mutableStateOf(TextFieldValue("")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Issue #33 Reproducer",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Untagged single-line BasicTextField with placeholder "Search by device name"
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(12.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Search by device name",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row {
            Box {
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.semantics {
                        contentDescription = "Filter by device type"
                    }
                ) {
                    Text("Device Filter")
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    filterTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedFilter = type
                                menuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { postClearCount += 1 },
                modifier = Modifier.testTag("post_clear_action")
            ) {
                Text("Post-Clear Action")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(text = "Filter Index: ${filterTypes.indexOf(selectedFilter)} | Debounce: $debouncedCalls")
        Text(text = "Query Empty: ${query.isEmpty()} | Query Length: ${query.length}")
        Text(text = "Post-Clear Count: $postClearCount")

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Cursor Position Section",
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Untagged BasicTextField controlled by TextFieldValue for cursor-at-start/middle/end tests
        BasicTextField(
            value = cursorField,
            onValueChange = { cursorField = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(10.dp),
            decorationBox = { innerTextField ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (cursorField.text.isEmpty()) {
                        Text(
                            text = "Cursor field placeholder",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                }
            }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row {
            OutlinedButton(
                onClick = {
                    cursorField = cursorField.copy(selection = TextRange(0))
                },
                modifier = Modifier.testTag("cursor_start")
            ) {
                Text("Start")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    val mid = cursorField.text.length / 2
                    cursorField = cursorField.copy(selection = TextRange(mid))
                },
                modifier = Modifier.testTag("cursor_middle")
            ) {
                Text("Middle")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = {
                    val end = cursorField.text.length
                    cursorField = cursorField.copy(selection = TextRange(end))
                },
                modifier = Modifier.testTag("cursor_end")
            ) {
                Text("End")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(text = "Cursor Empty: ${cursorField.text.isEmpty()} | Cursor Length: ${cursorField.text.length}")
        Text(text = "Cursor Offset: ${cursorField.selection.start}")
    }
}
