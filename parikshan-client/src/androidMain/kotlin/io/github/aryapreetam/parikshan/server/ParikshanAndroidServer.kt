package io.github.aryapreetam.parikshan.server

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.resolvedSelector
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

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

  private data class SelectorCandidate(
    val node: SemanticsNode,
    val score: Int,
    val area: Float,
    val depth: Int
  )

  private fun selectorLabel(command: Command): String =
    (command as? Command.HasSelector)?.let { it.selector?.raw ?: it.tag }.orEmpty()

  private fun findFirstInteraction(command: Command): SemanticsNodeInteraction? =
    findFirstNode(command)?.let { interactionFor(it) }

  private fun findFirstNode(command: Command): SemanticsNode? {
    val selector = command.resolvedSelector() ?: return null
    return selectorCandidates(selector).firstOrNull()?.node
  }

  private fun selectorCandidates(selector: Selector): List<SelectorCandidate> {
    if (selector.raw.isBlank()) return emptyList()
    val matcher =
      SemanticsMatcher("Parikshan selector '${selector.raw}'") { node ->
        selectorScore(node = node, selector = selector) != null
      }

    return composeRule
      .onAllNodes(matcher, useUnmergedTree = true)
      .fetchSemanticsNodes(atLeastOneRootRequired = false)
      .asSequence()
      .filter { isVisible(it) }
      .mapNotNull { node ->
        selectorScore(node = node, selector = selector)?.let { score ->
          SelectorCandidate(
            node = node,
            score = score,
            area = nodeArea(node),
            depth = nodeDepth(node)
          )
        }
      }
      .sortedWith(
        compareBy<SelectorCandidate> { it.score }
          .thenBy { it.area }
          .thenByDescending { it.depth }
      )
      .toList()
  }

  private fun selectorScore(
    node: SemanticsNode,
    selector: Selector
  ): Int? {
    val raw = selector.raw.trim()
    if (raw.isEmpty()) return null

    val tag = node.config.getOrNull(SemanticsProperties.TestTag)?.trim()
    val text = directTextOf(node)?.trim()

    return when (selector) {
      is Selector.Tag ->
        if (tag == selector.value.trim()) 0 else null

      is Selector.Text ->
        textScore(text = text, raw = selector.value.trim())

      is Selector.Auto ->
        when {
          tag == raw -> 0
          text?.equals(raw, ignoreCase = true) == true -> 10
          text?.contains(raw, ignoreCase = true) == true -> 20
          else -> null
        }
    }
  }

  private fun textScore(
    text: String?,
    raw: String
  ): Int? =
    when {
      raw.isEmpty() || text == null -> null
      text.equals(raw, ignoreCase = true) -> 10
      text.contains(raw, ignoreCase = true) -> 20
      else -> null
    }

  private fun interactionFor(node: SemanticsNode): SemanticsNodeInteraction =
    composeRule.onNode(
      matcher = SemanticsMatcher("SemanticsNode(id=${node.id})") { it.id == node.id },
      useUnmergedTree = true
    )

  private fun clickTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.OnClick) != null) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun inputTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.SetText) != null) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun scrollTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.ScrollBy) != null) {
        return current
      }
      current = current.parent
    }
    return null
  }

  private fun nodeArea(node: SemanticsNode): Float {
    val bounds = node.boundsInWindow
    return bounds.width * bounds.height
  }

  private fun nodeDepth(node: SemanticsNode): Int {
    var depth = 0
    var current = node.parent
    while (current != null) {
      depth += 1
      current = current.parent
    }
    return depth
  }

  private fun isVisible(node: SemanticsNode): Boolean {
    val bounds = node.boundsInWindow
    return bounds.width > 0f && bounds.height > 0f && node.layoutInfo.isPlaced
  }

  private fun snapshotTextOf(node: SemanticsNode): String? =
    directTextOf(node) ?: descendantTextsOf(node).takeIf { it.isNotEmpty() }?.joinToString("")

  private fun directTextOf(node: SemanticsNode): String? {
    node.config.getOrNull(SemanticsProperties.EditableText)?.text
      ?.takeIf { it.isNotBlank() }
      ?.let { return it }

    val values = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
    if (values.isNotEmpty()) {
      values.joinToString("") { it.text }.takeIf { it.isNotBlank() }?.let { return it }
    }

    val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    if (contentDescription.isNotEmpty()) {
      return contentDescription.joinToString("").takeIf { it.isNotBlank() }
    }

    return null
  }

  private fun descendantTextsOf(node: SemanticsNode): List<String> =
    buildList {
      node.children.forEach { child ->
        appendDescendantText(child, this)
      }
    }

  private fun appendDescendantText(
    node: SemanticsNode,
    texts: MutableList<String>
  ) {
    directTextOf(node)?.let { texts += it }
    node.children.forEach { child ->
      appendDescendantText(child, texts)
    }
  }

  private fun handleCommand(command: Command): Response {
    return when (command) {
      is Command.Click -> {
        val matched = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${selectorLabel(command)}'")
        val target = clickTargetFor(matched)
          ?: return Response.Error(command.id, "Node '${selectorLabel(command)}' is not clickable")
        val interaction = interactionFor(target)
        try { interaction.performScrollTo() } catch (e: Throwable) { /* Ignore */ }
        interaction.performClick()
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val matched = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${selectorLabel(command)}'")
        val target = inputTargetFor(matched)
          ?: return Response.Error(command.id, "Node '${selectorLabel(command)}' does not accept text input")
        val interaction = interactionFor(target)
        try { interaction.performScrollTo() } catch (e: Throwable) { /* Ignore */ }
        interaction.performTextClearance()
        interaction.performTextInput(command.text)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val matched = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${selectorLabel(command)}'")
        val interaction = interactionFor(scrollTargetFor(matched) ?: matched)
        try {
          interaction.performTouchInput {
            when (command.direction) {
              ScrollDirection.Up -> swipeDown()
              ScrollDirection.Down -> swipeUp()
              ScrollDirection.Left -> swipeRight()
              ScrollDirection.Right -> swipeLeft()
            }
          }
        } catch (e: Throwable) {
          return Response.Error(command.id, "Failed to scroll '${selectorLabel(command)}': ${e.message}")
        }
        Response.Ok(command.id)
      }

      is Command.AssertVisible -> {
        val nodeInfo = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${selectorLabel(command)}'")
        if (!isVisible(nodeInfo)) {
          return Response.Error(command.id, "Node '${selectorLabel(command)}' exists but is not visible")
        }
        val bounds = nodeInfo.boundsInWindow
        Response.NodeInfo(
          id = command.id,
          bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble()),
          visible = true,
          text = snapshotTextOf(nodeInfo)
        )
      }

      is Command.AssertText -> {
        val nodeInfo = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${selectorLabel(command)}'")
        val actual = snapshotTextOf(nodeInfo).orEmpty()
        if (actual != command.expected) {
          return Response.Error(
            command.id,
            "Text mismatch for '${selectorLabel(command)}'. expected='${command.expected}' actual='$actual'"
          )
        }
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        composeRule.waitUntil(timeoutMillis = command.timeoutMs) {
          findFirstNode(command)?.let { isVisible(it) } == true
        }
        val nodeInfo = findFirstNode(command)
          ?: return Response.Error(command.id, "No node found for selector '${selectorLabel(command)}'")
        val bounds = nodeInfo.boundsInWindow
        Response.NodeInfo(
          id = command.id,
          bounds = Bounds(bounds.left.toDouble(), bounds.top.toDouble(), bounds.right.toDouble(), bounds.bottom.toDouble()),
          visible = true,
          text = snapshotTextOf(nodeInfo)
        )
      }

      is Command.GetTree -> {
        val nodes = mutableListOf<NodeSnapshot>()
        val root = try { composeRule.onRoot() } catch (e: Throwable) { null }
        
        if (root != null) {
          val rootNode = try { root.fetchSemanticsNode() } catch (e: Throwable) { null }
          val rootBounds = rootNode?.boundsInWindow

          fun traverse(node: SemanticsNode) {
            val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
            val editableText = node.config.getOrNull(SemanticsProperties.EditableText)?.text
            val spokenText = node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text }.orEmpty()
            val contentDescription = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()

            val text = editableText?.takeIf { it.isNotBlank() }
              ?: spokenText.takeIf { it.isNotBlank() }
              ?: contentDescription.ifBlank { null }
            
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
      is Command.RelaunchApp -> relaunchTargetApp(command.id)
      is Command.StartRecording -> Response.Ok(command.id)
      is Command.StopRecording -> Response.Ok(command.id)
      is Command.Shutdown -> {
        shutdownRequested = true
        Response.Ok(command.id)
      }
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  private fun relaunchTargetApp(commandId: String): Response {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val intent =
      context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: return Response.Error(commandId, "Could not find launch intent for package ${context.packageName}")
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
    instrumentation.waitForIdleSync()
    composeRule.waitForIdle()
    return Response.Ok(commandId)
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
