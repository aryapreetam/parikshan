package io.github.aryapreetam.parikshan

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aryapreetam.parikshan.server.ParikshanAndroidServer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generic instrumentation test runner for Parikshan.
 * The Gradle plugin will execute this test class via `adb shell am instrument`.
 * It launches the user's default app activity, starts the in-app HTTP server,
 * and passes the ComposeTestRule to it so it can execute remote E2E commands.
 */
@RunWith(AndroidJUnit4::class)
class ParikshanAndroidRunner {

  @get:Rule
  val composeRule = createEmptyComposeRule()

  @Test
  fun startParikshanServer() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext

    // Launch the default launcher activity of the target app
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: error("Could not find launch intent for package ${context.packageName}")
    
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    
    context.startActivity(intent)

    // Resolve token from instrumentation arguments
    val args = InstrumentationRegistry.getArguments()
    val sessionToken = args.getString("parikshan_token") ?: ""

    // Start the Parikshan server and pass the compose rule
    ParikshanAndroidServer.start(composeRule, port = 9879, sessionToken = sessionToken)

    // Wait for shutdown command
    ParikshanAndroidServer.awaitShutdown()
  }
}
