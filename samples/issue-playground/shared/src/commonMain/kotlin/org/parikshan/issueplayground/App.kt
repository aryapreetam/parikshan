package org.parikshan.issueplayground

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.parikshan.issueplayground.issue10.Issue10Screen

data class IssueItem(
    val id: String,
    val title: String,
    val description: String
)

val issueList = listOf(
    IssueItem(
        id = "10",
        title = "Issue #10",
        description = "iOS input() crash with NSUnknownKeyException when targeting text fields by label"
    ),
    IssueItem(
        id = "24",
        title = "Issue #24",
        description = "Android input cannot reselect filled fields due to inconsistent text precedence"
    ),
    IssueItem(
        id = "25",
        title = "Issue #25",
        description = "Desktop semantic click attempts to invoke disabled controls and hides callback errors"
    )
)

@Composable
fun App() {
    MaterialTheme {
        var selectedIssueId by remember { mutableStateOf<String?>(null) }
        
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .safeDrawingPadding()
        ) {
            when (selectedIssueId) {
                "10" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { selectedIssueId = null },
                            modifier = Modifier.testTag("back_to_issue_index").padding(8.dp)
                        ) {
                            Text("← Back to Issue Index")
                        }
                        Issue10Screen()
                    }
                }
                "24" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { selectedIssueId = null },
                            modifier = Modifier.testTag("back_to_issue_index").padding(8.dp)
                        ) {
                            Text("← Back to Issue Index")
                        }
                        org.parikshan.issueplayground.issue24.Issue24Screen()
                    }
                }
                "25" -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { selectedIssueId = null },
                            modifier = Modifier.testTag("back_to_issue_index").padding(8.dp)
                        ) {
                            Text("← Back to Issue Index")
                        }
                        org.parikshan.issueplayground.issue25.Issue25Screen()
                    }
                }
                else -> {
                    IssueIndexLauncher(onSelectIssue = { selectedIssueId = it })
                }
            }
        }
    }
}

@Composable
fun IssueIndexLauncher(onSelectIssue: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Issue Playground Index",
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "Select an issue scenario to inspect or manually test",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(issueList) { issue ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectIssue(issue.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = issue.title,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = issue.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}