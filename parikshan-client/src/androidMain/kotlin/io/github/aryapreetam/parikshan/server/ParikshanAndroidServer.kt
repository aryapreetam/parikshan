package io.github.aryapreetam.parikshan.server

import android.os.Handler
import android.os.Looper
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
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
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
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

  fun start(rule: ComposeTestRule, port: Int = 9879) {
    if (!running.compareAndSet(false, true)) return
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
      java.net.ServerSocket(port, 50, java.net.InetAddress.getByName("127.0.0.1")).use { serverSocket ->
        println("[ParikshanAndroidServer] Listening on port $port")
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
        val reader = BufferedReader(InputStreamReader(socket.inputStream))
        val writer = OutputStreamWriter(socket.outputStream)

        // Read HTTP Request
        val requestLines = mutableListOf<String>()
        var line = reader.readLine()
        while (line != null && line.isNotEmpty()) {
          requestLines.add(line)
          line = reader.readLine()
        }

        var contentLength = 0
        for (header in requestLines) {
          if (header.lowercase().startsWith("content-length:")) {
            contentLength = header.substringAfter(":").trim().toIntOrNull() ?: 0
          }
        }

        if (contentLength > 0) {
          val bodyChars = CharArray(contentLength)
          var read = 0
          while (read < contentLength) {
            val r = reader.read(bodyChars, read, contentLength - read)
            if (r == -1) break
            read += r
          }
          val body = String(bodyChars)

          val command = try {
            ProtocolJson.decodeCommand(body)
          } catch (e: Exception) {
            null
          }

          if (command != null) {
            println("[ParikshanAndroidServer] Received command: ${command::class.simpleName}")
            val response = try {
              handleCommand(command)
            } catch (e: Throwable) {
              Response.Error(command.id, e.message ?: "Unknown error executing command")
            }
            sendHttpResponse(writer, 200, ProtocolJson.encodeResponse(response))
            if (command is Command.Shutdown) {
              running.set(false)
              shutdownLatch.countDown()
            }
          } else {
            sendHttpResponse(writer, 400, ProtocolJson.encodeResponse(Response.Error("unknown", "Invalid command")))
          }
        } else {
          // Health check
          sendHttpResponse(writer, 200, """{"type":"Ok","id":"health"}""")
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private fun handleCommand(command: Command): Response {
    // Because composeRule handles synchronization internally (it pumps the UI thread),
    // we don't need to wrap this in a mainHandler.post {}. 
    // The ComposeTestRule is designed to be called from the test thread.
    return when (command) {
      is Command.Click -> {
        val node = composeRule.onNodeWithTag(command.tag)
        try { node.performScrollTo() } catch (e: Throwable) { /* Ignore */ }
        node.performClick()
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = composeRule.onNodeWithTag(command.tag)
        try { node.performScrollTo() } catch (e: Throwable) { /* Ignore */ }
        node.performTextClearance()
        node.performTextInput(command.text)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = composeRule.onNodeWithTag(command.tag)
        try {
          node.performTouchInput {
            when (command.direction) {
              ScrollDirection.Up -> swipeDown() // Swiping down reveals content above
              ScrollDirection.Down -> swipeUp() // Swiping up reveals content below
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
        composeRule.onNodeWithTag(command.tag).assertIsDisplayed()
        val nodeInfo = composeRule.onNodeWithTag(command.tag).fetchSemanticsNode()
        val bounds = nodeInfo.boundsInWindow
        Response.NodeInfo(
          id = command.id,
          bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble()),
          visible = true,
          text = nodeInfo.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        )
      }

      is Command.AssertText -> {
        composeRule.onNodeWithTag(command.tag).assertTextEquals(command.expected)
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        composeRule.waitUntil(timeoutMillis = command.timeoutMs) {
          try {
            composeRule.onNodeWithTag(command.tag).assertIsDisplayed()
            true
          } catch (e: Throwable) {
            false
          }
        }
        val nodeInfo = composeRule.onNodeWithTag(command.tag).fetchSemanticsNode()
        val bounds = nodeInfo.boundsInWindow
        Response.NodeInfo(
          id = command.id,
          bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble()),
          visible = true,
          text = nodeInfo.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        )
      }

      is Command.GetTree -> {
        val nodes = mutableListOf<NodeSnapshot>()
        val root = try {
          composeRule.onRoot()
        } catch (e: Throwable) {
          e.printStackTrace()
          null
        }
        
        if (root != null) {
          fun traverse(node: androidx.compose.ui.semantics.SemanticsNode) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
            val textList = node.config.getOrNull(SemanticsProperties.Text)
            val text = textList?.joinToString(" ") { it.text } 
              ?: node.config.getOrNull(SemanticsProperties.EditableText)?.text
            
            val bounds = node.boundsInWindow
            nodes.add(NodeSnapshot(
              tag = tag,
              text = text,
              visible = true,
              bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble())
            ))
            node.children.forEach { traverse(it) }
          }
          
          try {
            traverse(root.fetchSemanticsNode())
          } catch (e: Throwable) {
            e.printStackTrace()
            // Ignore if semantics node is not fully initialized
          }
        }
        Response.Tree(id = command.id, nodes = nodes)
      }

      is Command.Screenshot -> Response.Ok(command.id)
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
      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  private fun sendHttpResponse(writer: OutputStreamWriter, status: Int, body: String) {
    val statusText = if (status == 200) "OK" else "Bad Request"
    val bodyBytes = body.encodeToByteArray()
    writer.write("HTTP/1.1 $status $statusText\r\n")
    writer.write("Content-Type: application/json\r\n")
    writer.write("Content-Length: ${bodyBytes.size}\r\n")
    writer.write("Connection: close\r\n")
    writer.write("\r\n")
    writer.flush()
    writer.write(body)
    writer.flush()
  }
}