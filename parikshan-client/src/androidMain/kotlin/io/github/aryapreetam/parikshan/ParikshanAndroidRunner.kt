package io.github.aryapreetam.parikshan

import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.aryapreetam.parikshan.server.AndroidServer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generic instrumentation test runner for Parikshan.
 * The Gradle plugin will execute this test class via `adb shell am instrument`.
 * It launches the user's default app activity, starts the in-app HTTP server,
 * and passes the ComposeTestRule to it so it can execute remote E2E commands.
 *
 * @suppress
 */
@RunWith(AndroidJUnit4::class)
class ParikshanAndroidRunner {

  @get:Rule
  val composeRule = createEmptyComposeRule()

  @Test
  fun startTestServer() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val args = InstrumentationRegistry.getArguments()

    val launcherClass = args.getString("launcher_class")
    val intent = if (!launcherClass.isNullOrBlank()) {
      val fullyQualified = if (launcherClass.startsWith(".")) {
        "${context.packageName}$launcherClass"
      } else {
        launcherClass
      }
      Intent().setClassName(context.packageName, fullyQualified)
    } else {
      context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: error("Could not find launch intent for package ${context.packageName}")
    }

    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)

    val sessionToken = args.getString("parikshan_token") ?: ""
    val portStr = args.getString("parikshan_port")
    val resolvedPort = portStr?.toIntOrNull() ?: 9879

    // Start the test server and pass the compose rule
    AndroidServer.start(composeRule, port = resolvedPort, sessionToken = sessionToken)

    // Wait for shutdown command
    AndroidServer.awaitShutdown()
  }
}
