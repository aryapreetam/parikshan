@file:OptIn(
  kotlinx.cinterop.ExperimentalForeignApi::class,
  kotlinx.cinterop.BetaInteropApi::class
)

package io.github.aryapreetam.parikshan.server

import io.github.aryapreetam.parikshan.IosBridgeState
import io.github.aryapreetam.parikshan.pumpRunLoop
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.AF_INET
import platform.posix.INADDR_ANY
import platform.posix.IPPROTO_TCP
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.listen
import platform.posix.read
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.write
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.Worker

/**
 * Lightweight HTTP server that runs inside the iOS app process.
 * Accepts JSON commands via POST /parikshan and delegates to
 * IosBridgeState on the main thread (with NSRunLoop pumping
 * so Compose can recompose between commands).
 *
 * Protocol: same JSON encoding as ParikshanServer (Desktop),
 * transported over HTTP POST instead of WebSocket.
 */
object ParikshanIosServer {
  private val running = AtomicInt(0)
  private val serverFd = AtomicInt(-1)

  fun startIfNeeded(port: Int = 9878) {
    if (!running.compareAndSet(0, 1)) return // Already running

    val worker = Worker.start(name = "parikshan-ios-server")
    worker.executeAfter(0L) {
      runServer(port)
    }
  }

  fun stop() {
    running.value = 0
    val fd = serverFd.value
    if (fd >= 0) {
      close(fd)
      serverFd.value = -1
    }
  }

  private fun runServer(port: Int) {
    memScoped {
      val fd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
      if (fd < 0) {
        println("[ParikshanIosServer] Failed to create socket")
        running.value = 0
        return
      }
      serverFd.value = fd

      // Allow address reuse
      val reuseVal = alloc<platform.posix.int32_tVar>()
      reuseVal.value = 1
      setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuseVal.ptr, sizeOf<platform.posix.int32_tVar>().convert())

      val addr = alloc<sockaddr_in>()
      addr.sin_family = AF_INET.convert()
      // Manual big-endian conversion (htons not available as top-level on Darwin)
      val p = port.toUShort()
      addr.sin_port = ((p.toInt() shr 8) or ((p.toInt() and 0xFF) shl 8)).toUShort()
      addr.sin_addr.s_addr = INADDR_ANY.toUInt()

      if (bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
        println("[ParikshanIosServer] Failed to bind to port $port")
        close(fd)
        running.value = 0
        return
      }

      if (listen(fd, 5) < 0) {
        println("[ParikshanIosServer] Failed to listen")
        close(fd)
        running.value = 0
        return
      }

      println("[ParikshanIosServer] Listening on port $port")

      while (running.value == 1) {
        val clientFd = accept(fd, null, null)
        if (clientFd < 0) {
          if (running.value == 0) break // Server stopped
          continue
        }
        handleConnection(clientFd)
      }

      close(fd)
      serverFd.value = -1
    }
  }

  private fun handleConnection(clientFd: Int) {
    try {
      // Read full HTTP request into buffer (persistent connection — handle multiple requests)
      while (running.value == 1) {
        val request = readHttpRequest(clientFd) ?: break
        val body = extractBody(request) ?: break

        if (body.isBlank()) {
          // Likely a health-check or invalid request
          sendHttpResponse(clientFd, 200, """{"type":"Ok","id":"health"}""")
          continue
        }

        // Decode command
        val command = try {
          ProtocolJson.decodeCommand(body)
        } catch (e: Throwable) {
          val errorResp = ProtocolJson.encodeResponse(
            Response.Error(id = "unknown", message = "Invalid command: ${e.message}")
          )
          sendHttpResponse(clientFd, 400, errorResp)
          continue
        }

        // Execute on main thread with settle
        val response = executeOnMainThread(command)
        val responseJson = ProtocolJson.encodeResponse(response)
        sendHttpResponse(clientFd, 200, responseJson)

        if (command is Command.Shutdown) break
      }
    } catch (_: Throwable) {
      // Connection error, close silently
    } finally {
      close(clientFd)
    }
  }

  private fun executeOnMainThread(command: Command): Response {
    val result = AtomicReference<Response?>(null)
    val done = AtomicInt(0)

    dispatch_async(dispatch_get_main_queue()) {
      val resp = handleCommand(command)
      result.value = resp
      done.value = 1
    }

    // Spin-wait for main thread completion (we're on the server worker thread)
    val deadline = platform.posix.time(null) + 30 // 30 second timeout
    while (done.value == 0 && platform.posix.time(null) < deadline) {
      platform.posix.usleep(10_000u) // 10ms
    }

    return result.value ?: Response.Error(
      id = command.id,
      message = "Timed out waiting for main thread execution"
    )
  }

  private fun handleCommand(command: Command): Response {
    // This runs on the main thread
    return when (command) {
      is Command.Click -> {
        if (!IosSemanticsAccessor.performClick(command.tag)) {
          return Response.Error(command.id, "No clickable node for '${command.tag}'")
        }
        pumpRunLoop(iterations = 5, intervalSeconds = 0.05)
        Response.Ok(command.id)
      }

      is Command.Input -> {
        if (!IosSemanticsAccessor.performInput(command.tag, command.text)) {
          return Response.Error(command.id, "No input node for '${command.tag}'")
        }
        pumpRunLoop(iterations = 5, intervalSeconds = 0.05)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        if (!IosSemanticsAccessor.performScroll(command.tag, command.direction)) {
          return Response.Error(command.id, "No scroll node for '${command.tag}'")
        }
        pumpRunLoop(iterations = 3, intervalSeconds = 0.05)
        Response.Ok(command.id)
      }

      is Command.AssertVisible -> {
        pumpRunLoop(iterations = 3, intervalSeconds = 0.05)
        val node = IosSemanticsAccessor.snapshotNode(command.tag)
          ?: return Response.Error(command.id, "No node for '${command.tag}'")
        if (!node.visible) {
          return Response.Error(command.id, "Node '${command.tag}' not visible")
        }
        Response.NodeInfo(id = command.id, bounds = node.bounds, visible = node.visible, text = node.text)
      }

      is Command.AssertText -> {
        pumpRunLoop(iterations = 3, intervalSeconds = 0.05)
        val node = IosSemanticsAccessor.snapshotNode(command.tag)
          ?: return Response.Error(command.id, "No node for '${command.tag}'")
        val actual = node.text.orEmpty()
        if (actual != command.expected) {
          return Response.Error(command.id, "Text mismatch: expected='${command.expected}' actual='$actual'")
        }
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        val timeoutMs = command.timeoutMs
        val pollIntervalMs = 50L
        val startNs = kotlin.time.TimeSource.Monotonic.markNow()
        var node = IosSemanticsAccessor.snapshotNode(command.tag)
        while (node?.visible != true) {
          if (startNs.elapsedNow().inWholeMilliseconds >= timeoutMs) {
            return Response.Error(command.id, "Timed out waiting for '${command.tag}' after ${timeoutMs}ms")
          }
          pumpRunLoop(iterations = 1, intervalSeconds = pollIntervalMs.toDouble() / 1000.0)
          node = IosSemanticsAccessor.snapshotNode(command.tag)
        }
        Response.NodeInfo(id = command.id, bounds = node.bounds, visible = node.visible, text = node.text)
      }

      is Command.GetTree -> {
        pumpRunLoop(iterations = 3, intervalSeconds = 0.05)
        Response.Tree(id = command.id, nodes = IosSemanticsAccessor.snapshotTree())
      }

      is Command.Screenshot -> Response.Ok(command.id)
      is Command.PressBack -> Response.Ok(command.id)
      is Command.PressHome -> Response.Ok(command.id)
      is Command.StartRecording -> Response.Ok(command.id)
      is Command.StopRecording -> Response.Ok(command.id)
      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  // ── HTTP helpers ──

  private fun readHttpRequest(fd: Int): String? {
    val buffer = StringBuilder()
    val buf = ByteArray(4096)
    var contentLength = -1
    var headerEnd = -1

    while (true) {
      val n = buf.usePinned { pinned ->
        read(fd, pinned.addressOf(0), buf.size.convert()).toInt()
      }
      if (n <= 0) return null

      buffer.append(buf.decodeToString(0, n))

      // Find end of headers
      if (headerEnd < 0) {
        headerEnd = buffer.indexOf("\r\n\r\n")
        if (headerEnd >= 0) {
          val headers = buffer.substring(0, headerEnd)
          val clLine = headers.lines().firstOrNull {
            it.startsWith("Content-Length:", ignoreCase = true)
          }
          contentLength = clLine?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
        }
      }

      if (headerEnd >= 0) {
        val bodyStart = headerEnd + 4
        val bodyReceived = buffer.length - bodyStart
        if (bodyReceived >= contentLength) {
          return buffer.toString()
        }
      }
    }
  }

  private fun extractBody(request: String): String? {
    val idx = request.indexOf("\r\n\r\n")
    if (idx < 0) return null
    return request.substring(idx + 4)
  }

  private fun sendHttpResponse(fd: Int, status: Int, body: String) {
    val statusText = if (status == 200) "OK" else "Bad Request"
    val bodyBytes = body.encodeToByteArray()
    val response = "HTTP/1.1 $status $statusText\r\n" +
      "Content-Type: application/json\r\n" +
      "Content-Length: ${bodyBytes.size}\r\n" +
      "Connection: keep-alive\r\n" +
      "\r\n"
    val headerBytes = response.encodeToByteArray()

    headerBytes.usePinned { pinned ->
      write(fd, pinned.addressOf(0), headerBytes.size.convert())
    }
    bodyBytes.usePinned { pinned ->
      write(fd, pinned.addressOf(0), bodyBytes.size.convert())
    }
  }
}
