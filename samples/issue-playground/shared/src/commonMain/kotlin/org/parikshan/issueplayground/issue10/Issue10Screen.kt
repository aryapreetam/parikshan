package org.parikshan.issueplayground.issue10

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun Issue10Screen() {
    var hostText by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Issue #10 Reproducer",
            style = MaterialTheme.typography.headlineSmall
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Standard Material 3 OutlinedTextField matching user's Issue #10 setup:
        // Clicking Text("Host") label inside OutlinedTextField focuses the field,
        // so findFirstResponder() returns non-null UIView, triggering native KVC valueForKey("input") crash.
        OutlinedTextField(
            value = hostText,
            onValueChange = { hostText = it },
            label = { Text("Host") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
