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
import platform.posix.IPPROTO_TCP
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.listen
import platform.posix.read
import platform.posix.recv
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.write
import platform.posix.getenv
import kotlinx.cinterop.useContents
import platform.Foundation.NSString
import platform.Foundation.stringWithUTF8String
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImagePNGRepresentation
import platform.Foundation.base64EncodedStringWithOptions
import platform.CoreGraphics.*
import kotlinx.cinterop.readValue
import kotlin.concurrent.AtomicInt
import kotlin.concurrent.AtomicReference
import kotlin.native.concurrent.Worker

// --- DEFINITIVE EAGER INITIALIZATION ---
// This top-level property forces the server to start as soon as the Kotlin framework is loaded by the iOS app.
@Suppress("unused")
private val parikshanEagerBoot = ParikshanIosServer.startIfNeeded()

object ParikshanIosServer {
  private val running = AtomicInt(0)
  private val serverFd = AtomicInt(-1)
  private var sessionToken: String = ""

  fun startIfNeeded(port: Int = 9878) {
    if (!running.compareAndSet(0, 1)) return

    println("[ParikshanIosServer] BOOTING on port $port")
    val worker = Worker.start(name = "parikshan-ios-server")
    worker.executeAfter(0L) {
      // Resolve token from environment
      val tokenC = getenv("PARIKSHAN_TOKEN") ?: getenv("SIMCTL_CHILD_PARIKSHAN_TOKEN")
      if (tokenC != null) {
        sessionToken = platform.Foundation.NSString.stringWithUTF8String(tokenC) ?: ""
      }
      println("[ParikshanIosServer] Server starting with token: ${sessionToken.take(8)}...")
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

      val reuseVal = alloc<platform.posix.int32_tVar>()
      reuseVal.value = 1
      setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, reuseVal.ptr, sizeOf<platform.posix.int32_tVar>().convert())

      val addr = alloc<sockaddr_in>()
      addr.sin_family = AF_INET.convert()
      val p = port.toUShort()
      addr.sin_port = ((p.toInt() shr 8) or ((p.toInt() and 0xFF) shl 8)).toUShort()
      addr.sin_addr.s_addr = 0u // INADDR_ANY

      if (bind(fd, addr.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) < 0) {
        println("[ParikshanIosServer] Failed to bind to port $port")
        close(fd)
        running.value = 0
        return
      }

      if (listen(fd, 5) < 0) {
        close(fd)
        running.value = 0
        return
      }

      println("[ParikshanIosServer] Securely listening on port $port")

      while (running.value == 1) {
        val clientFd = accept(fd, null, null)
        if (clientFd < 0) {
          if (running.value == 0) break
          continue
        }
        handleConnection(clientFd)
      }
      close(fd)
    }
  }

  private fun handleConnection(clientFd: Int) {
    try {
      while (running.value == 1) {
        val headBuffer = ByteArray(8192)
        val n = headBuffer.usePinned { p -> recv(clientFd, p.addressOf(0), 8192.convert(), 0).toInt() }
        if (n <= 0) break
        
        val headStr = headBuffer.decodeToString(0, n)
        
        // HEALTH CHECK (GET)
        if (headStr.startsWith("GET ")) {
            sendHttpResponse(clientFd, 200, """{"type":"ok","id":"health"}""")
            break
        }

        // POST COMMAND
        val headerEnd = headStr.indexOf("\r\n\r\n")
        if (headerEnd < 0) break
        
        val headers = headStr.substring(0, headerEnd)
        val clMatch = Regex("Content-Length:\\s*(\\d+)", RegexOption.IGNORE_CASE).find(headers)
        val contentLength = clMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        
        val bodyStart = headerEnd + 4
        val initialBodyRead = n - bodyStart
        val bodyBuffer = ByteArray(contentLength)
        
        if (initialBodyRead > 0) {
            headBuffer.copyInto(bodyBuffer, 0, bodyStart, n)
        }
        
        var bodyRead = initialBodyRead
        while (bodyRead < contentLength) {
            val rem = bodyBuffer.usePinned { p -> recv(clientFd, p.addressOf(bodyRead), (contentLength - bodyRead).convert(), 0).toInt() }
            if (rem <= 0) break
            bodyRead += rem
        }
        
        val requestBody = bodyBuffer.decodeToString()
        val command = try { ProtocolJson.decodeCommand(requestBody) } catch (e: Throwable) { null }
        
        if (command == null) {
            sendHttpResponse(clientFd, 400, ProtocolJson.encodeResponse(Response.Error("unknown", "Invalid JSON")))
            break
        }

        // SECURITY: Token validation
        if (sessionToken.isNotEmpty() && command.token != sessionToken) {
            println("[ParikshanIosServer] ACCESS DENIED: Invalid token")
            sendHttpResponse(clientFd, 401, ProtocolJson.encodeResponse(Response.Error(command.id, "Unauthorized")))
            break
        }

        val response = executeOnMainThread(command)
        sendHttpResponse(clientFd, 200, ProtocolJson.encodeResponse(response))
        break 
      }
    } finally {
      close(clientFd)
    }
  }

  private fun executeOnMainThread(command: Command): Response {
    val result = AtomicReference<Response?>(null)
    val done = AtomicInt(0)
    dispatch_async(dispatch_get_main_queue()) {
      result.value = handleCommand(command)
      done.value = 1
    }
    val deadline = platform.posix.time(null) + 30
    while (done.value == 0 && platform.posix.time(null) < deadline) {
      platform.posix.usleep(10_000u)
    }
    return result.value ?: Response.Error(command.id, "Timeout")
  }

  private fun handleCommand(command: Command): Response {
    return when (command) {
      is Command.Click -> {
        if (!IosSemanticsAccessor.performClick(command.tag)) return Response.Error(command.id, "Click failed")
        pumpRunLoop(iterations = 5, intervalSeconds = 0.05)
        Response.Ok(command.id)
      }
      is Command.Input -> {
        if (!IosSemanticsAccessor.performInput(command.tag, command.text)) return Response.Error(command.id, "Input failed")
        pumpRunLoop(iterations = 5, intervalSeconds = 0.05)
        Response.Ok(command.id)
      }
      is Command.Scroll -> {
        if (!IosSemanticsAccessor.performScroll(command.tag, command.direction)) return Response.Error(command.id, "Scroll failed")
        pumpRunLoop(iterations = 3, intervalSeconds = 0.05)
        Response.Ok(command.id)
      }
      is Command.AssertVisible -> {
        val node = IosSemanticsAccessor.snapshotNode(command.tag) ?: return Response.Error(command.id, "Not found")
        Response.NodeInfo(command.id, node.bounds, node.visible, node.text)
      }
      is Command.AssertText -> {
        val node = IosSemanticsAccessor.snapshotNode(command.tag) ?: return Response.Error(command.id, "Not found")
        if (node.text != command.expected) return Response.Error(command.id, "Mismatch")
        Response.Ok(command.id)
      }
      is Command.WaitFor -> {
        val deadline = platform.posix.time(null) + ((command.timeoutMs + 999L) / 1000L)
        while (platform.posix.time(null) <= deadline) {
          val node = IosSemanticsAccessor.snapshotNode(command.tag)
          if (node?.visible == true) {
            return Response.NodeInfo(command.id, node.bounds, visible = true, text = node.text)
          }
          pumpRunLoop(iterations = 1, intervalSeconds = 0.05)
        }
        Response.Error(command.id, "Timed out waiting for '${command.tag}' after ${command.timeoutMs}ms")
      }
      is Command.GetTree -> Response.Tree(command.id, IosSemanticsAccessor.snapshotTree())
      
      is Command.Screenshot -> {
        val window = UIApplication.sharedApplication.keyWindow ?: return Response.Error(command.id, "No window")
        UIGraphicsBeginImageContextWithOptions(window.bounds.useContents { size.readValue() }, false, 0.0)
        window.drawViewHierarchyInRect(window.bounds, true)
        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        val data = image?.let { UIImagePNGRepresentation(it) }
        val base64 = data?.base64EncodedStringWithOptions(0u) ?: ""
        Response.NodeInfo(command.id, io.github.aryapreetam.parikshan.protocol.Bounds(0.0,0.0,0.0,0.0), true, base64)
      }

      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
      else -> Response.Ok(command.id)
    }
  }

  private fun sendHttpResponse(fd: Int, status: Int, body: String) {
    val statusText = if (status == 200) "OK" else if (status == 401) "Unauthorized" else "Error"
    val bodyBytes = body.encodeToByteArray()
    val head = "HTTP/1.1 $status $statusText\r\nContent-Type: application/json\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n"
    val headBytes = head.encodeToByteArray()
    write(fd, headBytes.usePinned { it.addressOf(0) }, headBytes.size.convert())
    write(fd, bodyBytes.usePinned { it.addressOf(0) }, bodyBytes.size.convert())
  }
}
