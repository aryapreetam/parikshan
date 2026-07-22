package sample.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aryapreetam.composeapp.generated.resources.Res
import io.github.aryapreetam.composeapp.generated.resources.parikshan_logo
import org.jetbrains.compose.resources.painterResource
import sample.app.getPlatformName

@Composable
fun HomeScreen() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFFAFAFC))
      .padding(16.dp)
      .testTag("home_screen"),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Image(
      painter = painterResource(Res.drawable.parikshan_logo),
      contentDescription = "Parikshan Logo",
      modifier = Modifier
        .size(120.dp)
        .testTag("parikshan_logo_image")
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = "Parikshan",
      style = MaterialTheme.typography.headlineLarge,
      fontWeight = FontWeight.Bold,
      color = Color(0xFF7F52FF),
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag("parikshan_title")
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = "A Compose Multiplatform End-to-End Testing Framework",
      style = MaterialTheme.typography.bodyLarge,
      color = Color.Gray,
      textAlign = TextAlign.Center,
      modifier = Modifier.testTag("parikshan_description").padding(horizontal = 16.dp)
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = "Running on: ${getPlatformName()}",
      style = MaterialTheme.typography.labelMedium,
      color = Color.Gray,
      modifier = Modifier.testTag("platform_badge").fillMaxWidth(),
      textAlign = TextAlign.Center
    )
  }
}
