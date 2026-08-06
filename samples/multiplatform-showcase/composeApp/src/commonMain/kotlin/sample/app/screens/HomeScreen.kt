package sample.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sample.app.composeapp.generated.resources.Res
import sample.app.composeapp.generated.resources.parikshan_logo
import org.jetbrains.compose.resources.painterResource
import sample.app.getPlatformName

@Composable
fun HomeScreen() {
  val scrollState = rememberScrollState()
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .padding(16.dp)
      .testTag("home_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      modifier = Modifier
        .widthIn(max = 480.dp)
        .fillMaxWidth()
        .verticalScroll(scrollState),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Image(
        painter = painterResource(Res.drawable.parikshan_logo),
        contentDescription = "Parikshan Logo",
        modifier = Modifier
          .size(110.dp)
          .testTag("parikshan_logo_image")
      )

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Parikshan",
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF7F52FF),
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("parikshan_title")
      )

      Spacer(modifier = Modifier.height(8.dp))

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
}
