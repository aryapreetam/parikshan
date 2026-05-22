package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.awt.ComposeWindow
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.resolvedSelector
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.server.routing.post
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ParikshanServer {
  private val activeServer = AtomicReference<RunningParikshanServer?>(null)

  fun start(
    window: ComposeWindow,
    config: ParikshanServerConfig = ParikshanServerConfig()
  ): ParikshanServerHandle {
    activeServer.get()?.let { existing ->
      existing.stop()
    }

    val server = RunningParikshanServer(window = window, config = config) {
      activeServer.set(null)
    }
    server.start()
    activeServer.set(server)
    return server
  }
}

private class RunningParikshanServer(
  private val window: ComposeWindow,
  private val config: ParikshanServerConfig,
  private val onStopped: () -> Unit
) : ParikshanServerHandle {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val semantics = DesktopSemanticsAccessor(window)
  private val injector = DesktopEventInjector()
  private val videoRecorder = DesktopVideoRecorder(semanticsAccessor = semantics)

  private val server =
    embeddedServer(Netty, host = config.host, port = config.port) {
      installServerRoutes()
    }

  fun start() {
    server.start(wait = false)
  }

  override fun stop() {
    videoRecorder.stop()
    runCatching {
      server.stop(gracePeriodMillis = 500, timeoutMillis = 2_000)
    }
    scope.cancel()
    onStopped()
  }

  private fun Application.installServerRoutes() {
    install(WebSockets)

    routing {
      post("/") {
        val raw = call.receiveText()
        val command = runCatching { ProtocolJson.decodeCommand(raw) }.getOrNull()
          ?: return@post call.respondText(
            ProtocolJson.encodeResponse(Response.Error("unknown", "Invalid JSON")),
            status = HttpStatusCode.BadRequest
          )

        // Security check
        val expectedToken = System.getProperty("parikshan.token")
        if (!expectedToken.isNullOrEmpty() && command.token != expectedToken) {
          return@post call.respondText(
            ProtocolJson.encodeResponse(Response.Error(command.id, "Unauthorized: Token mismatch")),
            status = HttpStatusCode.Unauthorized
          )
        }

        val response = runCatching { handleCommand(command) }.getOrElse { throwable ->
          if (throwable is CancellationException) throw throwable
          Response.Error(command.id, throwable.message ?: "Unknown error")
        }

        call.respondText(ProtocolJson.encodeResponse(response))

        if (command is Command.Shutdown) {
          scope.launch {
            delay(100)
            stop()
          }
        }
      }

      webSocket(config.path) {
        for (frame in incoming) {
          if (frame !is Frame.Text) {
            continue
          }

          val raw = frame.readText()
          val command =
            runCatching { ProtocolJson.decodeCommand(raw) }.getOrElse { error ->
              send(
                Frame.Text(
                  ProtocolJson.encodeResponse(
                    Response.Error(
                      id = "unknown",
                      message = "Invalid command payload: ${error.message}"
                    )
                  )
                )
              )
              continue
            }

          val response =
            runCatching {
              handleCommand(command)
            }.getOrElse { throwable ->
              if (throwable is CancellationException) {
                throw throwable
              }
              Response.Error(
                id = command.id,
                message = throwable.message ?: "Unknown server error"
              )
            }

          send(Frame.Text(ProtocolJson.encodeResponse(response)))

          if (command is Command.Shutdown) {
            scope.launch {
              delay(100)
              stop()
            }
            break
          }
        }
      }
    }
  }

  private suspend fun handleCommand(command: Command): Response {
    val activeSelector = command.resolvedSelector()

    return when (command) {
      is Command.Click -> {
        val sel = activeSelector ?: return Response.Error(command.id, "Command has no selector")
        val node = semantics.findBySelector(sel)
          ?: return Response.Error(command.id, "No node found for selector '${sel.raw}'")
        videoRecorder.setVirtualCursor(
          xOnScreen = node.bounds.centerX.roundToInt(),
          yOnScreen = node.bounds.centerY.roundToInt()
        )
        videoRecorder.captureNow()
        delay(120)
        if (!semantics.performClick(sel)) {
          return Response.Error(command.id, "Node '${sel.raw}' is not clickable")
        }
        videoRecorder.captureNow()
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val sel = activeSelector ?: return Response.Error(command.id, "Command has no selector")
        val node = semantics.findBySelector(sel)
          ?: return Response.Error(command.id, "No node found for selector '${sel.raw}'")
        videoRecorder.setVirtualCursor(
          xOnScreen = node.bounds.centerX.roundToInt(),
          yOnScreen = node.bounds.centerY.roundToInt()
        )
        videoRecorder.captureNow()
        delay(120)
        if (!semantics.performSetText(sel, command.text)) {
          return Response.Error(command.id, "Node '${sel.raw}' does not support text input")
        }
        videoRecorder.captureNow()
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val sel = activeSelector ?: return Response.Error(command.id, "Command has no selector")
        val node = semantics.findBySelector(sel)
          ?: return Response.Error(command.id, "No node found for selector '${sel.raw}'")
        videoRecorder.setVirtualCursor(
          xOnScreen = node.bounds.centerX.roundToInt(),
          yOnScreen = node.bounds.centerY.roundToInt()
        )
        videoRecorder.captureNow()
        if (!semantics.performScrollBy(selector = sel, direction = command.direction)) {
          return Response.Error(command.id, "Node '${sel.raw}' is not scrollable")
        }
        delay(120)
        videoRecorder.captureNow()
        Response.Ok(command.id)
      }

      is Command.AssertVisible -> {
        val sel = activeSelector ?: return Response.Error(command.id, "Command has no selector")
        val node = semantics.findBySelector(sel)
          ?: return Response.Error(command.id, "No node found for selector '${sel.raw}'")
        if (!node.visible) {
          return Response.Error(command.id, "Node '${sel.raw}' exists but is not visible")
        }
        Response.NodeInfo(
          id = command.id,
          bounds = node.bounds,
          visible = node.visible,
          text = node.text
        )
      }

      is Command.AssertText -> {
        val sel = activeSelector ?: return Response.Error(command.id, "Command has no selector")
        val node = semantics.findBySelector(sel)
          ?: return Response.Error(command.id, "No node found for selector '${sel.raw}'")
        val actual = node.text.orEmpty()
        if (actual != command.expected) {
          return Response.Error(
            id = command.id,
            message = "Text mismatch for '${sel.raw}'. expected='${command.expected}' actual='$actual'"
          )
        }
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        val sel = activeSelector ?: return Response.Error(command.id, "Command has no selector")
        val node = waitForSelector(sel, command.timeoutMs)
          ?: return Response.Error(
            command.id,
            "Timed out waiting for '${sel.raw}' after ${command.timeoutMs}ms"
          )
        videoRecorder.captureNow()
        Response.NodeInfo(
          id = command.id,
          bounds = node.bounds,
          visible = node.visible,
          text = node.text
        )
      }

      is Command.Screenshot -> {
        val captureBounds = semantics.windowBoundsOnScreen()
        val image = injector.createScreenCapture(captureBounds)
        injector.screenshot(image = image, path = command.devicePath)
        videoRecorder.captureNow()
        Response.Ok(command.id)
      }

      is Command.GetTree ->
        Response.Tree(
          id = command.id,
          nodes = semantics.snapshotTree()
        )

      is Command.StartRecording -> {
        videoRecorder.start(
          DesktopVideoSessionConfig(
            sessionName = command.sessionName,
            outputPath = command.path,
            fps = command.fps,
            showCursor = command.showCursor
          )
        )
        Response.Ok(command.id)
      }

      is Command.StopRecording -> {
        videoRecorder.stop()
        Response.Ok(command.id)
      }

      is Command.PressBack -> Response.Ok(command.id) // No-op on desktop
      is Command.PressHome -> Response.Ok(command.id) // No-op on desktop
      is Command.RelaunchApp ->
        Response.Error(command.id, "relaunchApp() is handled by the DesktopDriver process launcher")
      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  private suspend fun waitForSelector(
    selector: io.github.aryapreetam.parikshan.protocol.Selector,
    timeoutMs: Long
  ): DesktopNode? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() <= deadline) {
      semantics.findBySelector(selector)?.let { return it }
      delay(config.waitPollIntervalMs)
    }
    return null
  }
}
