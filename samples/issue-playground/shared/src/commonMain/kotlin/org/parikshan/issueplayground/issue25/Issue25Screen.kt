package org.parikshan.issueplayground.issue25

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
fun Issue25Screen() {
    var callbackInvoked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Issue #25 Reproducer",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Disabled button matching Issue #25 reproducer:
        // Semantic click must NOT invoke this callback, and must NOT fall back to AWT Robot click.
        Button(
            enabled = false,
            modifier = Modifier.testTag("disabled_action"),
            onClick = {
                callbackInvoked = true
                error("Disabled callback was invoked")
            }
        ) {
            Text("Disabled action")
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Callback Invoked: $callbackInvoked")
    }
}
