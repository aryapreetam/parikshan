package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.awt.ComposeWindow
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
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
    return when (command) {
      is Command.Click -> {
        val node = semantics.findByTag(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        videoRecorder.setVirtualCursor(
          xOnScreen = node.bounds.centerX.roundToInt(),
          yOnScreen = node.bounds.centerY.roundToInt()
        )
        videoRecorder.captureNow()
        delay(120)
        if (!semantics.performClick(command.tag)) {
          return Response.Error(command.id, "Node '${command.tag}' is not clickable")
        }
        videoRecorder.captureNow()
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = semantics.findByTag(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        videoRecorder.setVirtualCursor(
          xOnScreen = node.bounds.centerX.roundToInt(),
          yOnScreen = node.bounds.centerY.roundToInt()
        )
        videoRecorder.captureNow()
        delay(120)
        if (!semantics.performSetText(command.tag, command.text)) {
          return Response.Error(command.id, "Node '${command.tag}' does not support text input")
        }
        videoRecorder.captureNow()
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = semantics.findByTag(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        videoRecorder.setVirtualCursor(
          xOnScreen = node.bounds.centerX.roundToInt(),
          yOnScreen = node.bounds.centerY.roundToInt()
        )
        videoRecorder.captureNow()
        if (!semantics.performScrollBy(tag = command.tag, direction = command.direction)) {
          return Response.Error(command.id, "Node '${command.tag}' is not scrollable")
        }
        delay(120)
        videoRecorder.captureNow()
        Response.Ok(command.id)
      }

      is Command.AssertVisible -> {
        val node = semantics.findByTag(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!node.visible) {
          return Response.Error(command.id, "Node '${command.tag}' exists but is not visible")
        }
        Response.NodeInfo(
          id = command.id,
          bounds = node.bounds,
          visible = node.visible,
          text = node.text
        )
      }

      is Command.AssertText -> {
        val node = semantics.findByTag(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        val actual = node.text.orEmpty()
        if (actual != command.expected) {
          return Response.Error(
            id = command.id,
            message = "Text mismatch for '${command.tag}'. expected='${command.expected}' actual='$actual'"
          )
        }
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        val node = waitForTag(command.tag, command.timeoutMs)
          ?: return Response.Error(
            command.id,
            "Timed out waiting for '${command.tag}' after ${command.timeoutMs}ms"
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
        injector.screenshot(image = image, path = command.path)
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

      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  private suspend fun waitForTag(
    tag: String,
    timeoutMs: Long
  ): DesktopNode? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() <= deadline) {
      semantics.findByTag(tag)?.let { return it }
      delay(config.waitPollIntervalMs)
    }
    return null
  }
}
