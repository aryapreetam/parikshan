package sample.app.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryapreetam.composeapp.generated.resources.NotoSansDevanagari
import io.github.aryapreetam.composeapp.generated.resources.Res
import io.github.aryapreetam.composeapp.generated.resources.parikshan_logo
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.painterResource

@Composable
fun SimpleGreetDemo() {
  var name by remember { mutableStateOf("") }
  var greeting by remember { mutableStateOf("") }
  
  val fontFamily = FontFamily(
    Font(Res.font.NotoSansDevanagari, FontWeight.Normal),
    Font(Res.font.NotoSansDevanagari, FontWeight.Bold)
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color(0xFFFAFAFC))
      .padding(horizontal = 24.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Column(
      modifier = Modifier
        .weight(1f)
        .width(320.dp),
      verticalArrangement = Arrangement.Center,
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Image(
        painter = painterResource(Res.drawable.parikshan_logo),
        contentDescription = null,
        modifier = Modifier
          .size(100.dp)
          .testTag("parikshan_image")
      )
      Spacer(modifier = Modifier.height(56.dp))
      OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Name", fontFamily = fontFamily) },
        placeholder = { Text("Enter your name", fontFamily = fontFamily) },
        textStyle = LocalTextStyle.current.copy(fontFamily = fontFamily),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("name_input"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = Color(0xFF7F52FF),
          focusedLabelColor = Color(0xFF7F52FF),
          cursorColor = Color(0xFF7F52FF)
        )
      )

      Spacer(modifier = Modifier.height(16.dp))

      Button(
        onClick = {
          greeting = if (name.isNotBlank()) "Hello, $name!" else ""
        },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = Color(0xFF7F52FF),
          contentColor = Color.White
        ),
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("greet_button")
      ) {
        Text(
          text = "Greet",
          fontSize = 16.sp,
          fontWeight = FontWeight.Bold,
          fontFamily = fontFamily
        )
      }

      Spacer(modifier = Modifier.height(32.dp))

      if (greeting.isNotEmpty()) {
        Text(
          text = greeting,
          fontSize = 28.sp,
          fontWeight = FontWeight.Bold,
          color = Color(0xFF19191C),
          fontFamily = fontFamily,
          modifier = Modifier.testTag("greeting_label")
        )
      }
    }
  }
}
