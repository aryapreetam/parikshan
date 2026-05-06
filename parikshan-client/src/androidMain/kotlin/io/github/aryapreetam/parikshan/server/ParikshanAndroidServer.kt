package io.github.aryapreetam.parikshan.server

import android.os.Bundle
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertTextEquals
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp

@OptIn(ExperimentalTestApi::class)
object ParikshanAndroidServer {
  private val running = AtomicBoolean(false)
  private var serverThread: Thread? = null
  private val shutdownLatch = CountDownLatch(1)
  private lateinit var composeRule: ComposeTestRule
  private var sessionToken: String = ""
  @Volatile
  private var shutdownRequested = false

  fun isShutdownRequested() = shutdownRequested

  fun start(rule: ComposeTestRule, port: Int = 9879, sessionToken: String? = null) {
    if (!running.compareAndSet(false, true)) return
    
    // Resolve session token from instrumentation args
    val args = InstrumentationRegistry.getArguments()
    this.sessionToken = sessionToken ?: args.getString("parikshan_token") ?: ""
    println("[ParikshanAndroidServer] Starting with token: ${this.sessionToken.take(8)}...")
    
    composeRule = rule
    serverThread = Thread {
      runServer(port)
    }.apply {
      name = "ParikshanAndroidServer"
      start()
    }
  }

  fun awaitShutdown() {
    shutdownLatch.await()
  }

  private fun runServer(port: Int) {
    try {
      ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1")).use { serverSocket ->
        println("[ParikshanAndroidServer] Securely listening on loopback:$port")
        while (running.get()) {
          try {
            val client = serverSocket.accept()
            Thread { handleConnection(client) }.start()
          } catch (e: Exception) {
            if (running.get()) e.printStackTrace()
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      running.set(false)
    } finally {
      shutdownLatch.countDown()
    }
  }

  private fun handleConnection(client: Socket) {
    try {
      client.use { socket ->
        val input = socket.getInputStream()
        val output = socket.getOutputStream()

        // Byte-accurate HTTP Header Parsing
        val headerLines = readHeaders(input)
        var contentLength = 0
        for (header in headerLines) {
          if (header.lowercase().startsWith("content-length:")) {
            contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
          }
        }

        if (contentLength > 0) {
          // Accurate byte reading to avoid UTF-8 truncation
          val bodyBytes = ByteArray(contentLength)
          var totalRead = 0
          while (totalRead < contentLength) {
            val read = input.read(bodyBytes, totalRead, contentLength - totalRead)
            if (read == -1) break
            totalRead += read
          }
          
          val body = String(bodyBytes, Charsets.UTF_8)
          val command = try { ProtocolJson.decodeCommand(body) } catch (e: Exception) { null }

          if (command != null) {
            // SECURITY: Validate Token
            if (sessionToken.isNotEmpty() && command.token != sessionToken) {
                println("[ParikshanAndroidServer] BLOCKED: Invalid session token")
                sendHttpResponse(output, 401, ProtocolJson.encodeResponse(Response.Error(command.id, "Unauthorized: Invalid Session Token")))
                return
            }

            println("[ParikshanAndroidServer] Executing: ${command::class.simpleName}")
            val response = try {
              handleCommand(command)
            } catch (e: Throwable) {
              Response.Error(command.id, e.message ?: "Unknown error executing command")
            }
            sendHttpResponse(output, 200, ProtocolJson.encodeResponse(response))
            
            if (command is Command.Shutdown) {
              running.set(false)
              shutdownLatch.countDown()
            }
          } else {
            sendHttpResponse(output, 400, ProtocolJson.encodeResponse(Response.Error("unknown", "Invalid command format")))
          }
        } else {
          // Health check
          sendHttpResponse(output, 200, """{"type":"ok","id":"health"}""")
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun readHeaders(input: InputStream): List<String> {
    val headers = mutableListOf<String>()
    var currentLine = StringBuilder()
    while (true) {
      val b = input.read()
      if (b == -1) break
      val c = b.toChar()
      if (c == '\n') {
        val line = currentLine.toString().trim()
        if (line.isEmpty()) break
        headers.add(line)
        currentLine = StringBuilder()
      } else if (c != '\r') {
        currentLine.append(c)
      }
    }
    return headers
  }

  private fun handleCommand(command: Command): Response {
    return when (command) {
      is Command.Click -> {
        val node = composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst()
        try { node.performScrollTo() } catch (e: Throwable) { /* Ignore */ }
        node.performClick()
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst()
        try { node.performScrollTo() } catch (e: Throwable) { /* Ignore */ }
        node.performTextClearance()
        node.performTextInput(command.text)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst()
        try {
          node.performTouchInput {
            when (command.direction) {
              ScrollDirection.Up -> swipeDown()
              ScrollDirection.Down -> swipeUp()
              ScrollDirection.Left -> swipeRight()
              ScrollDirection.Right -> swipeLeft()
            }
          }
        } catch (e: Throwable) {
          return Response.Error(command.id, "Failed to scroll '${command.tag}': ${e.message}")
        }
        Response.Ok(command.id)
      }

      is Command.AssertVisible -> {
        composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst().assertIsDisplayed()
        val nodeInfo = composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst().fetchSemanticsNode()
        val bounds = nodeInfo.boundsInWindow
        Response.NodeInfo(
          id = command.id,
          bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble()),
          visible = true,
          text = nodeInfo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        )
      }

      is Command.AssertText -> {
        composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst().assertTextEquals(command.expected)
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        composeRule.waitUntil(timeoutMillis = command.timeoutMs) {
          try {
            composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst().assertIsDisplayed()
            true
          } catch (e: Throwable) {
            false
          }
        }
        val nodeInfo = composeRule.onAllNodes(hasTestTag(command.tag).or(hasText(command.tag, substring = true, ignoreCase = true))).onFirst().fetchSemanticsNode()
        val bounds = nodeInfo.boundsInWindow
        Response.NodeInfo(
          id = command.id,
          bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble()),
          visible = true,
          text = nodeInfo.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }
        )
      }

      is Command.GetTree -> {
        val nodes = mutableListOf<NodeSnapshot>()
        val root = try { composeRule.onRoot() } catch (e: Throwable) { null }
        
        if (root != null) {
          val rootNode = try { root.fetchSemanticsNode() } catch (e: Throwable) { null }
          val rootBounds = rootNode?.boundsInWindow

          fun traverse(node: androidx.compose.ui.semantics.SemanticsNode) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
            val textList = node.config.getOrNull(SemanticsProperties.Text)
            val text = textList?.joinToString("") { it.text } 
              ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
            
            val bounds = node.boundsInWindow
            val hasArea = bounds.width > 0f && bounds.height > 0f
            val isPhysicallyVisible = if (rootBounds != null && hasArea) {
              val centerX = bounds.left + (bounds.width / 2f)
              val centerY = bounds.top + (bounds.height / 2f)
              centerX >= rootBounds.left && centerX <= rootBounds.right &&
                centerY >= rootBounds.top && centerY <= rootBounds.bottom
            } else {
              hasArea
            }

            nodes.add(NodeSnapshot(
              tag = tag,
              text = text,
              visible = isPhysicallyVisible,
              bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble())
            ))
            node.children.forEach { traverse(it) }
          }
          if (rootNode != null) { traverse(rootNode) }
        }
        Response.Tree(id = command.id, nodes = nodes)
      }

      is Command.Screenshot -> {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Save to internal storage; the host driver will pull it via ADB
        val deviceFile = java.io.File("/sdcard/parikshan-screenshot.png")
        device.takeScreenshot(deviceFile)
        Response.Ok(command.id)
      }
      
      is Command.PressBack -> {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
        Response.Ok(command.id)
      }
      is Command.PressHome -> {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressHome()
        Response.Ok(command.id)
      }
      is Command.StartRecording -> Response.Ok(command.id)
      is Command.StopRecording -> Response.Ok(command.id)
      is Command.Shutdown -> {
        shutdownRequested = true
        Response.Ok(command.id)
      }
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  private fun sendHttpResponse(output: OutputStream, status: Int, body: String) {
    val statusText = if (status == 200) "OK" else if (status == 401) "Unauthorized" else "Bad Request"
    val bodyBytes = body.toByteArray(Charsets.UTF_8)
    val header = "HTTP/1.1 $status $statusText\r\n" +
                 "Content-Type: application/json; charset=utf-8\r\n" +
                 "Content-Length: ${bodyBytes.size}\r\n" +
                 "Connection: close\r\n\r\n"
    output.write(header.toByteArray(Charsets.UTF_8))
    output.write(bodyBytes)
    output.flush()
  }
}
