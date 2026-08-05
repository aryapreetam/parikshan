package org.parikshan.issueplayground.issue10

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        
        // Custom field wrapper layout matching user's Issue #10 setup:
        // A label Text("Host") placed alongside/above a BasicTextField.
        // When input("Host", ...) targets "Host", it matches the label sub-node
        // which lacks Compose's SetText action, triggering native KVC fallback.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Text(
                text = "Host",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            BasicTextField(
                value = hostText,
                onValueChange = { hostText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color.LightGray.copy(alpha = 0.3f))
                    .padding(8.dp)
            )
        }
    }
}
