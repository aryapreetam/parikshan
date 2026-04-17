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
        if (!invokeBridgeClick(command.tag)) {
          if (!invokeDomClick(command.tag)) {
            page.mouse().click(node.bounds.centerX, node.bounds.centerY)
          }
        }
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = getNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!invokeBridgeInput(command.tag, command.text)) {
          page.mouse().click(node.bounds.centerX, node.bounds.centerY)
          page.keyboard().press("ControlOrMeta+A")
          page.keyboard().type(command.text)
        }
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = getNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!invokeBridgeScroll(command.tag, command.direction)) {
          page.mouse().move(node.bounds.centerX, node.bounds.centerY)
          val (deltaX, deltaY) =
            when (command.direction) {
              ScrollDirection.Up -> 0.0 to -420.0
              ScrollDirection.Down -> 0.0 to 420.0
              ScrollDirection.Left -> -420.0 to 0.0
              ScrollDirection.Right -> 420.0 to 0.0
            }
          page.mouse().wheel(deltaX, deltaY)
        }
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
    return readBridgeNode(tag) ?: readDomNode(tag)
  }

  private fun getTree(): List<NodeSnapshot> {
    val bridgeNodes = readBridgeTree()
    return if (bridgeNodes.isNotEmpty()) bridgeNodes else readDomTree()
  }

  private fun readBridgeNode(tag: String): NodeSnapshot? {
    val payload =
      page.evaluate(
        "tag => (window.__parikshan_getNodeJson ? window.__parikshan_getNodeJson(tag) : null)",
        tag
      ) as? String ?: return null
    return ProtocolJson.instance.decodeFromString(NodeSnapshot.serializer(), payload)
  }

  private fun readBridgeTree(): List<NodeSnapshot> {
    val payload =
      page.evaluate(
        "() => (window.__parikshan_getTreeJson ? window.__parikshan_getTreeJson() : '[]')"
      ) as? String ?: "[]"
    return ProtocolJson.instance.decodeFromString(
      ListSerializer(NodeSnapshot.serializer()),
      payload
    )
  }

  private fun readDomNode(tag: String): NodeSnapshot? {
    val payload =
      page.evaluate(
        """
        tag => {
          const extractText = (element) => {
            if (!element) return null;

            const candidates = [
              element.innerText,
              element.textContent,
              element.getAttribute?.('aria-label'),
              element.getAttribute?.('title'),
              element.getAttribute?.('value'),
              element.value,
              element.placeholder
            ];

            for (const candidate of candidates) {
              const normalized = candidate?.trim?.();
              if (normalized) return normalized;
            }

            const labeledDescendant = element.querySelector?.('[aria-label]');
            const labeledText = labeledDescendant?.getAttribute?.('aria-label')?.trim?.();
            if (labeledText) return labeledText;

            return null;
          };

          const queue = [document.documentElement, document.body].filter(Boolean);
          const visited = new Set();
          let element = null;

          while (queue.length > 0 && element == null) {
            const current = queue.shift();
            if (!current || visited.has(current)) continue;
            visited.add(current);

            if (current.id === tag) {
              element = current;
              break;
            }

            const descendants = current.querySelectorAll?.(`[id="${tag}"]`) ?? [];
            if (descendants.length > 0) {
              element = descendants[0];
              break;
            }

            if (current.shadowRoot) {
              queue.push(current.shadowRoot);
            }

            const children = current.children ?? current.childNodes ?? [];
            for (const child of children) {
              queue.push(child);
            }
          }

          if (!element) return null;
          const rect = element.getBoundingClientRect();
          const style = window.getComputedStyle(element);
          const text = extractText(element);
          const visible = style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
          return JSON.stringify({
            tag,
            bounds: {
              left: rect.left,
              top: rect.top,
              right: rect.right,
              bottom: rect.bottom
            },
            visible,
            text
          });
        }
        """.trimIndent(),
        tag
      ) as? String ?: return null
    return ProtocolJson.instance.decodeFromString(NodeSnapshot.serializer(), payload)
  }

  private fun readDomTree(): List<NodeSnapshot> {
    val payload =
      page.evaluate(
        """
        () => {
          const extractText = (element) => {
            if (!element) return null;

            const candidates = [
              element.innerText,
              element.textContent,
              element.getAttribute?.('aria-label'),
              element.getAttribute?.('title'),
              element.getAttribute?.('value'),
              element.value,
              element.placeholder
            ];

            for (const candidate of candidates) {
              const normalized = candidate?.trim?.();
              if (normalized) return normalized;
            }

            const labeledDescendant = element.querySelector?.('[aria-label]');
            const labeledText = labeledDescendant?.getAttribute?.('aria-label')?.trim?.();
            if (labeledText) return labeledText;

            return null;
          };

          const nodes = [];
          const queue = [document.documentElement, document.body].filter(Boolean);
          const visited = new Set();

          while (queue.length > 0) {
            const current = queue.shift();
            if (!current || visited.has(current)) continue;
            visited.add(current);

            if (current.id) {
              const rect = current.getBoundingClientRect();
              const style = window.getComputedStyle(current);
              const text = extractText(current);
              nodes.push({
                tag: current.id,
                bounds: {
                  left: rect.left,
                  top: rect.top,
                  right: rect.right,
                  bottom: rect.bottom
                },
                visible: style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0,
                text
              });
            }

            if (current.shadowRoot) {
              queue.push(current.shadowRoot);
            }

            const children = current.children ?? current.childNodes ?? [];
            for (const child of children) {
              queue.push(child);
            }
          }
          return JSON.stringify(nodes);
        }
        """.trimIndent()
      ) as? String ?: "[]"
    return ProtocolJson.instance.decodeFromString(
      ListSerializer(NodeSnapshot.serializer()),
      payload
    )
  }

  private fun invokeBridgeClick(tag: String): Boolean =
    runCatching {
      page.evaluate(
        "tag => (window.__parikshan_click ? window.__parikshan_click(tag) : false)",
        tag
      ) as? Boolean ?: false
    }.getOrDefault(false)

  private fun invokeDomClick(tag: String): Boolean =
    runCatching {
      page.evaluate(
        """
        tag => {
          const queue = [document.documentElement, document.body].filter(Boolean);
          const visited = new Set();

          while (queue.length > 0) {
            const current = queue.shift();
            if (!current || visited.has(current)) continue;
            visited.add(current);

            if (current.id === tag) {
              current.click?.();
              current.dispatchEvent?.(new MouseEvent('click', { bubbles: true, cancelable: true, composed: true }));
              return true;
            }

            if (current.shadowRoot) {
              queue.push(current.shadowRoot);
            }

            const children = current.children ?? current.childNodes ?? [];
            for (const child of children) {
              queue.push(child);
            }
          }

          return false;
        }
        """.trimIndent(),
        tag
      ) as? Boolean ?: false
    }.getOrDefault(false)

  private fun invokeBridgeInput(
    tag: String,
    text: String
  ): Boolean =
    runCatching {
      page.evaluate(
        "([tag, text]) => (window.__parikshan_input ? window.__parikshan_input(tag, text) : false)",
        arrayOf(tag, text)
      ) as? Boolean ?: false
    }.getOrDefault(false)

  private fun invokeBridgeScroll(
    tag: String,
    direction: ScrollDirection
  ): Boolean =
    runCatching {
      page.evaluate(
        "([tag, direction]) => (window.__parikshan_scroll ? window.__parikshan_scroll(tag, direction) : false)",
        arrayOf(tag, direction.name)
      ) as? Boolean ?: false
    }.getOrDefault(false)

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

      waitForPageReady(page = page, timeoutMs = wasmConfig.bridgeReadyTimeoutMs)

      return WasmPlaywrightSession(
        page = page,
        context = context,
        browser = browser,
        playwright = playwright,
        targetVideoPath = videoPath
      )
    }

    private fun waitForPageReady(
      page: Page,
      timeoutMs: Long
    ) {
      val deadline = System.currentTimeMillis() + timeoutMs
      while (System.currentTimeMillis() <= deadline) {
        val ready =
          runCatching {
            page.evaluate(
              """
              () => {
                if (
                  typeof window.__parikshan_getNodeJson === 'function' &&
                  typeof window.__parikshan_getTreeJson === 'function'
                ) {
                  return true;
                }
                return document.readyState === 'interactive' || document.readyState === 'complete';
              }
              """.trimIndent()
            ) as Boolean
          }.getOrDefault(false)
        if (ready) {
          return
        }
        Thread.sleep(120)
      }
      error(
        "Timed out waiting for the WASM page to become ready. " +
          "Ensure the app finished loading at ${'$'}{page.url()}."
      )
    }
  }
}
