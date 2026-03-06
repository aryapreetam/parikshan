package io.github.aryapreetam.parikshan

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.github.aryapreetam.parikshan.client.ParikshanVideoConfig
import io.github.aryapreetam.parikshan.client.ParikshanWasmConfig
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString

class WasmDriver private constructor(
  private val session: WasmPlaywrightSession
) : TestDriver {
  override suspend fun send(command: Command): Response =
    session.handle(command)

  override suspend fun close() {
    // Intentionally no-op: session lifecycle is class-scoped and managed centrally.
  }

  companion object {
    suspend fun connect(
      className: String,
      wasmConfig: ParikshanWasmConfig,
      videoConfig: ParikshanVideoConfig
    ): WasmDriver {
      val session = ParikshanWasmSessionManager.acquire(className, wasmConfig, videoConfig)
      return WasmDriver(session)
    }
  }
}

private object ParikshanWasmSessionManager {
  private val lock = Any()
  private val shutdownHookInstalled = AtomicBoolean(false)
  private var activeClassName: String? = null
  private var activeSession: WasmPlaywrightSession? = null

  suspend fun acquire(
    className: String,
    wasmConfig: ParikshanWasmConfig,
    videoConfig: ParikshanVideoConfig
  ): WasmPlaywrightSession {
    installShutdownHookIfNeeded()
    synchronized(lock) {
      val existing = activeSession
      if (existing != null && activeClassName == className) {
        return existing
      }
      existing?.close()

      val created =
        WasmPlaywrightSession.start(
          className = className,
          wasmConfig = wasmConfig,
          videoConfig = videoConfig
        )
      activeClassName = className
      activeSession = created
      return created
    }
  }

  private fun installShutdownHookIfNeeded() {
    if (!shutdownHookInstalled.compareAndSet(false, true)) {
      return
    }
    Runtime.getRuntime().addShutdownHook(
      Thread {
        synchronized(lock) {
          activeSession?.close()
          activeSession = null
          activeClassName = null
        }
      }
    )
  }
}

private class WasmPlaywrightSession private constructor(
  private val page: Page,
  private val context: BrowserContext,
  private val browser: Browser,
  private val playwright: Playwright,
  private val targetVideoPath: Path?
) {
  private var closed = false

  suspend fun handle(command: Command): Response {
    return when (command) {
      is Command.Click -> {
        val node = getNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        page.mouse().click(node.bounds.centerX, node.bounds.centerY)
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = getNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        page.mouse().click(node.bounds.centerX, node.bounds.centerY)
        page.keyboard().press("ControlOrMeta+A")
        page.keyboard().type(command.text)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = getNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        page.mouse().move(node.bounds.centerX, node.bounds.centerY)
        val (deltaX, deltaY) =
          when (command.direction) {
            ScrollDirection.Up -> 0.0 to -420.0
            ScrollDirection.Down -> 0.0 to 420.0
            ScrollDirection.Left -> -420.0 to 0.0
            ScrollDirection.Right -> 420.0 to 0.0
          }
        page.mouse().wheel(deltaX, deltaY)
        Response.Ok(command.id)
      }

      is Command.AssertVisible -> {
        val node = getNode(command.tag)
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
        val node = getNode(command.tag)
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
        val node = waitForNode(command.tag, command.timeoutMs)
          ?: return Response.Error(
            command.id,
            "Timed out waiting for '${command.tag}' after ${command.timeoutMs}ms"
          )
        Response.NodeInfo(
          id = command.id,
          bounds = node.bounds,
          visible = node.visible,
          text = node.text
        )
      }

      is Command.Screenshot -> {
        val screenshotPath = Path.of(command.path)
        screenshotPath.parent?.let { parent -> Files.createDirectories(parent) }
        page.screenshot(
          Page.ScreenshotOptions()
            .setPath(screenshotPath)
            .setFullPage(true)
        )
        Response.Ok(command.id)
      }

      is Command.GetTree ->
        Response.Tree(
          id = command.id,
          nodes = getTree()
        )

      is Command.PressBack -> Response.Ok(command.id) // No-op on WASM
      is Command.PressHome -> Response.Ok(command.id) // No-op on WASM
      is Command.StartRecording -> Response.Ok(command.id)
      is Command.StopRecording -> Response.Ok(command.id)
      is Command.Shutdown -> Response.Ok(command.id)
      is Command.Ping -> Response.Ok(command.id)
    }
  }

  fun close() {
    if (closed) {
      return
    }
    closed = true

    runCatching { page.close() }
    val rawVideoPath = runCatching { page.video()?.path() }.getOrNull()
    runCatching { context.close() }
    runCatching { browser.close() }
    runCatching { playwright.close() }

    val finalVideoPath = targetVideoPath
    if (finalVideoPath != null && rawVideoPath != null) {
      runCatching {
        finalVideoPath.parent?.let { Files.createDirectories(it) }
        Files.move(rawVideoPath, finalVideoPath, StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }

  private suspend fun waitForNode(
    tag: String,
    timeoutMs: Long
  ): NodeSnapshot? {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() <= deadline) {
      getNode(tag)?.let { return it }
      delay(120)
    }
    return null
  }

  private fun getNode(tag: String): NodeSnapshot? {
    val payload =
      page.evaluate(
        "tag => (window.__parikshan_getNodeJson ? window.__parikshan_getNodeJson(tag) : null)",
        tag
      ) as? String
        ?: return null
    return ProtocolJson.instance.decodeFromString(NodeSnapshot.serializer(), payload)
  }

  private fun getTree(): List<NodeSnapshot> {
    val payload =
      page.evaluate(
        "() => (window.__parikshan_getTreeJson ? window.__parikshan_getTreeJson() : '[]')"
      ) as? String
        ?: "[]"
    return ProtocolJson.instance.decodeFromString(
      ListSerializer(NodeSnapshot.serializer()),
      payload
    )
  }

  companion object {
    fun start(
      className: String,
      wasmConfig: ParikshanWasmConfig,
      videoConfig: ParikshanVideoConfig
    ): WasmPlaywrightSession {
      val playwright = Playwright.create()
      val browser =
        playwright.chromium().launch(
          BrowserType.LaunchOptions()
            .setHeadless(wasmConfig.headless)
        )

      val videoPath =
        if (videoConfig.enabled) {
          val simpleName = className.substringAfterLast('.').replace('$', '_')
          Path.of(videoConfig.outputDir, "$simpleName.mp4")
        } else {
          null
        }
      val tempVideoDir =
        if (videoConfig.enabled) {
          val directory = File(videoConfig.outputDir, ".playwright-raw").also { it.mkdirs() }
          directory.toPath()
        } else {
          null
        }

      val contextOptions =
        Browser.NewContextOptions()
          .setViewportSize(wasmConfig.viewportWidth, wasmConfig.viewportHeight)
      if (tempVideoDir != null) {
        contextOptions.setRecordVideoDir(tempVideoDir)
      }
      val context = browser.newContext(contextOptions)
      val page = context.newPage()
      
      page.onConsoleMessage { msg ->
        println("WASM Console [${msg.type()}]: ${msg.text()}")
      }
      
      page.navigate(wasmConfig.appUrl)

      waitForBridge(page = page, timeoutMs = wasmConfig.bridgeReadyTimeoutMs)

      return WasmPlaywrightSession(
        page = page,
        context = context,
        browser = browser,
        playwright = playwright,
        targetVideoPath = videoPath
      )
    }

    private fun waitForBridge(
      page: Page,
      timeoutMs: Long
    ) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() <= deadline) {
        val ready =
          runCatching {
            page.evaluate(
              "() => (typeof window.__parikshan_getNodeJson === 'function' && typeof window.__parikshan_getTreeJson === 'function')"
            ) as Boolean
          }.getOrDefault(false)
        if (ready) {
          return
        }
        Thread.sleep(120)
      }
      error("Timed out waiting for WASM Parikshan bridge. Ensure parikshanTag is used in the UI.")
    }
  }
}
