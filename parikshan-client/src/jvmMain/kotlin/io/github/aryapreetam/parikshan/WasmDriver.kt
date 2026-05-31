package io.github.aryapreetam.parikshan

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.github.aryapreetam.parikshan.client.ParikshanVideoConfig
import io.github.aryapreetam.parikshan.client.ParikshanWasmConfig
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import io.github.aryapreetam.parikshan.protocol.resolvedSelector
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * JVM-side driver for Compose/Wasm. It opens the app in Playwright and invokes the
 * in-browser Parikshan bridge installed by Modifier.testTag instrumentation.
 */
class WasmDriver private constructor(
  private val sessionToken: String = System.getProperty("parikshan.token") ?: ""
) : TestDriver {

  private val page: Page
    get() = checkNotNull(sharedPage) { "WasmDriver shared page is not initialized" }

  override suspend fun send(command: Command): Response {
    command.token = sessionToken
    return runCatching {
      withContext(Dispatchers.IO) {
        ensureActivePage(ParikshanWasmConfig.fromSystemProperties())
        handleCommand(command)
      }
    }.getOrElse { throwable ->
      Response.Error(command.id, throwable.message ?: "Wasm driver error")
    }
  }

  override suspend fun close() {
    // Shared browser across tests. JVM shutdown hook will close it.
  }

  override suspend fun relaunchApp() {
    withContext(Dispatchers.IO) {
      val config = ParikshanWasmConfig.fromSystemProperties()
      connectMutex.withLock {
        val p = sharedPage ?: return@withLock
        if (p.isClosed) return@withLock
        
        // Reset app state without killing the window/video
        runCatching {
          p.evaluate("() => { sessionStorage.clear(); localStorage.clear(); }")
          p.navigate(config.appUrl)
          p.waitForFunction(
            "() => typeof window.__parikshan_getTreeJson === 'function'",
            null,
            Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
          )
        }
      }
    }
  }

  private suspend fun ensureActivePage(config: ParikshanWasmConfig) {
    connectMutex.withLock {
      val isBrowserAlive = sharedBrowser?.isConnected == true
      if (!isBrowserAlive || sharedPlaywright == null) {
        runCatching { sharedPage?.close() }
        runCatching { sharedContext?.close() }
        runCatching { sharedBrowser?.close() }
        runCatching { sharedPlaywright?.close() }
        
        sharedPage = null
        sharedContext = null
        sharedBrowser = null
        sharedPlaywright = null

        val playwright = Playwright.create()
        sharedPlaywright = playwright
        val launchOptions = BrowserType.LaunchOptions().setHeadless(config.headless)
        launchOptions.setArgs(listOf(
          "--window-size=${config.viewportWidth + 50},${config.viewportHeight + 100}",
          "--disable-gpu",
          "--use-gl=angle",
          "--use-angle=swiftshader",
          "--no-sandbox"
        ))
        
        sharedBrowser = playwright.chromium().launch(launchOptions)
      }

      val isPageClosed = sharedPage?.isClosed ?: true
      if (sharedPage == null || isPageClosed) {
        val videoConfig = ParikshanVideoConfig.fromSystemProperties()
        val contextOptions = Browser.NewContextOptions().setViewportSize(config.viewportWidth, config.viewportHeight)

        if (videoConfig.enabled) {
          val tempDir = playwrightTempDir ?: Files.createTempDirectory("parikshan-wasm-video").also { playwrightTempDir = it }
          contextOptions.setRecordVideoDir(tempDir)
          
          if (videoConfig.videoWidth != null && videoConfig.videoHeight != null) {
            contextOptions.setRecordVideoSize(videoConfig.videoWidth, videoConfig.videoHeight)
          } else {
            contextOptions.setRecordVideoSize(config.viewportWidth, config.viewportHeight)
          }
          videoConfig.deviceScaleFactor?.let { contextOptions.setDeviceScaleFactor(it) }
        }

        val context = sharedBrowser!!.newContext(contextOptions)
        
        val initScript = """
          window.__parikshan_utils = {
            extractText: function(element) {
              if (!element) return null;
              const candidates = [
                element.innerText, element.textContent, element.getAttribute?.('aria-label'),
                element.getAttribute?.('title'), element.getAttribute?.('value'), element.value, element.placeholder
              ];
              for (const candidate of candidates) {
                const normalized = candidate?.trim?.();
                if (normalized) return normalized;
              }
              const labeledDescendant = element.querySelector?.('[aria-label]');
              const labeledText = labeledDescendant?.getAttribute?.('aria-label')?.trim?.();
              if (labeledText) return labeledText;
              return null;
            },
            findNode: function(tag) {
              const queue = [document.documentElement, document.body].filter(Boolean);
              const visited = new Set();
              let element = null;
              let i = 0;
              while (i < queue.length && element == null) {
                const current = queue[i++];
                if (!current || visited.has(current)) continue;
                visited.add(current);
                if (current.id === tag) { element = current; break; }
                const descendants = current.querySelectorAll?.(`[id="${'$'}{tag}"]`) ?? [];
                if (descendants.length > 0) { element = descendants[0]; break; }
                if (current.shadowRoot) queue.push(current.shadowRoot);
                const children = current.children ?? current.childNodes ?? [];
                for (const child of children) queue.push(child);
              }
              return element;
            },
            readNode: function(tag) {
              const element = this.findNode(tag);
              if (!element) return null;
              const rect = element.getBoundingClientRect();
              const style = window.getComputedStyle(element);
              const text = this.extractText(element);
              const visible = style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
              return JSON.stringify({
                tag,
                bounds: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom },
                visible, text
              });
            },
            readTree: function() {
              const nodes = [];
              const queue = [document.documentElement, document.body].filter(Boolean);
              const visited = new Set();
              let i = 0;
              while (i < queue.length) {
                const current = queue[i++];
                if (!current || visited.has(current)) continue;
                visited.add(current);
                if (current.id) {
                  const rect = current.getBoundingClientRect();
                  const style = window.getComputedStyle(current);
                  const text = this.extractText(current);
                  nodes.push({
                    tag: current.id,
                    bounds: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom },
                    visible: style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0,
                    text
                  });
                }
                if (current.shadowRoot) queue.push(current.shadowRoot);
                const children = current.children ?? current.childNodes ?? [];
                for (const child of children) queue.push(child);
              }
              return JSON.stringify(nodes);
            },
            invokeClick: function(tag) {
              const current = this.findNode(tag);
              if (current) {
                current.click?.();
                current.dispatchEvent?.(new MouseEvent('click', { bubbles: true, cancelable: true, composed: true }));
                return true;
              }
              return false;
            }
          };
        """.trimIndent()
        context.addInitScript(initScript)
        
        sharedContext = context
        val page = context.newPage()
        page.onConsoleMessage { msg ->
          val target = if (msg.type() == "error") System.err else System.out
          target.println("Wasm Console [${msg.type()}]: ${msg.text()}")
        }
        sharedPage = page

        page.navigate(config.appUrl)
        page.waitForFunction(
          "() => typeof window.__parikshan_getTreeJson === 'function'",
          null,
          Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
        )
      }
    }
  }

  private suspend fun readNodeBySelector(selector: io.github.aryapreetam.parikshan.protocol.Selector): NodeSnapshot? {
    if (selector is io.github.aryapreetam.parikshan.protocol.Selector.Tag || selector is io.github.aryapreetam.parikshan.protocol.Selector.Auto) {
      readBridgeNode(selector.raw)?.let { return it }
      readDomNode(selector.raw)?.let { return it }
    }
    val tree = readTree()
    return runCatching { selector.resolveNode(tree).node }.getOrNull()
  }

  private suspend fun handleCommand(command: Command): Response {
    val selector = command.resolvedSelector()
      ?: io.github.aryapreetam.parikshan.protocol.Selector.Auto("")

    return when (command) {
      is Command.Ping -> Response.Ok(command.id)

      is Command.GetTree ->
        Response.Tree(
          id = command.id,
          nodes = readTree()
        )

      is Command.AssertVisible -> {
        val node = readNodeBySelector(selector) ?: return Response.Error(command.id, "No node found for selector '${selector.raw}'")
        if (!node.visible) {
          return Response.Error(command.id, "Node '${selector.raw}' exists but is not visible")
        }
        Response.NodeInfo(command.id, node.bounds, visible = true, text = node.text)
      }

      is Command.AssertText -> {
        val node = readNodeBySelector(selector) ?: return Response.Error(command.id, "No node found for selector '${selector.raw}'")
        if (node.text != command.expected) {
          return Response.Error(
            command.id,
            "Text mismatch for '${selector.raw}'. expected='${command.expected}' actual='${node.text}'"
          )
        }
        Response.Ok(command.id)
      }

      is Command.Click -> {
        val node = readNodeBySelector(selector)
          ?: return Response.Error(command.id, "No node found for selector '${selector.raw}'")
        if (!invokeBridgeClick(selector.raw)) {
          if (!invokeDomClick(selector.raw)) {
            page.mouse().click(node.bounds.centerX, node.bounds.centerY)
          }
        }
        delay(100)
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = readNodeBySelector(selector)
          ?: return Response.Error(command.id, "No node found for selector '${selector.raw}'")
        if (!invokeBridgeInput(selector.raw, command.text)) {
          page.mouse().click(node.bounds.centerX, node.bounds.centerY)
          page.keyboard().press("ControlOrMeta+A")
          page.keyboard().type(command.text)
        }
        delay(100)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = readNodeBySelector(selector)
          ?: return Response.Error(command.id, "No node found for selector '${selector.raw}'")
        if (!invokeBridgeScroll(selector.raw, command.direction)) {
          page.mouse().move(node.bounds.centerX, node.bounds.centerY)
          val (deltaX, deltaY) =
            when (command.direction) {
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Up -> 0.0 to -420.0
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Down -> 0.0 to 420.0
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Left -> -420.0 to 0.0
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Right -> 420.0 to 0.0
            }
          page.mouse().wheel(deltaX, deltaY)
        }
        delay(100)
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        val deadline = System.currentTimeMillis() + command.timeoutMs
        while (System.currentTimeMillis() <= deadline) {
          val node = readNodeBySelector(selector)
          if (node?.visible == true) {
            return Response.NodeInfo(command.id, node.bounds, visible = true, text = node.text)
          }
          delay(120)
        }
        Response.Error(command.id, "Timed out waiting for '${selector.raw}' after ${command.timeoutMs}ms")
      }

      is Command.Screenshot -> {
        val path = command.hostPath.ifBlank { command.devicePath }
        page.screenshot(Page.ScreenshotOptions().setPath(Paths.get(path)).setFullPage(true))
        Response.Ok(command.id)
      }

      is Command.PressBack -> Response.Ok(command.id)
      is Command.PressHome -> Response.Ok(command.id)
      is Command.RelaunchApp -> {
        relaunchSharedPage(ParikshanWasmConfig.fromSystemProperties())
        Response.Ok(command.id)
      }
      is Command.StartRecording -> {
        lastRequestedVideoPath = command.path
        Response.Ok(command.id)
      }
      is Command.StopRecording -> {
        try {
          connectMutex.withLock {
            val targetPath = lastRequestedVideoPath
            if (targetPath != null) {
              val videoConfig = runCatching { ParikshanVideoConfig.fromSystemProperties() }.getOrNull()
              if (videoConfig != null && videoConfig.postRollMs > 0) {
                delay(videoConfig.postRollMs)
              }

              val videoObj = runCatching { sharedPage?.video() }.getOrNull()
              runCatching { sharedPage?.close() }

              // Staff+ Deterministic Finalization: Poll for the file to exist and have size > 0
              var rawVideoPath: Path? = null
              repeat(50) { attempt ->
                  rawVideoPath = runCatching { videoObj?.path() }.getOrNull()
                  if (rawVideoPath != null && Files.exists(rawVideoPath!!) && Files.size(rawVideoPath!!) > 0) {
                      return@repeat
                  }
                  delay(100)
              }

              if (rawVideoPath != null && Files.exists(rawVideoPath!!) && Files.size(rawVideoPath!!) > 0) {
                val finalVideoPath = Paths.get(targetPath)
                finalVideoPath.parent?.let { Files.createDirectories(it) }
                try {
                  Files.copy(rawVideoPath!!, finalVideoPath, StandardCopyOption.REPLACE_EXISTING)
                  val size = Files.size(finalVideoPath)
                  System.err.println("WasmVideo: Successfully saved recording to $targetPath (bytes=$size)")
                  runCatching { Files.deleteIfExists(rawVideoPath!!) }
                } catch (e: Throwable) {
                  System.err.println("WasmVideo: Error copying video to $targetPath: ${e.message}")
                  e.printStackTrace()
                }
              } else {
                System.err.println("WasmVideo: Video file NOT found or empty at expected path $rawVideoPath after 5 seconds.")
              }
              lastRequestedVideoPath = null
            }
          }
          Response.Ok(command.id)
        } catch (throwable: Throwable) {
          System.err.println("WasmVideo: Error while stopping recording: ${'$'}{throwable.message}")
          throwable.printStackTrace()
          Response.Error(command.id, throwable.message ?: "StopRecording failed")
        }
      }
      is Command.Shutdown -> {
        Response.Ok(command.id)
      }
    }
  }

  private fun readNode(tag: String): NodeSnapshot? {
    return readBridgeNode(tag) ?: readDomNode(tag)
  }

  private fun readTree(): List<NodeSnapshot> {
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
    val payload = page.evaluate("tag => window.__parikshan_utils.readNode(tag)", tag) as? String ?: return null
    return ProtocolJson.instance.decodeFromString(NodeSnapshot.serializer(), payload)
  }

  private fun readDomTree(): List<NodeSnapshot> {
    val payload = page.evaluate("() => window.__parikshan_utils.readTree()") as? String ?: "[]"
    return ProtocolJson.instance.decodeFromString(ListSerializer(NodeSnapshot.serializer()), payload)
  }

  private fun invokeBridgeClick(tag: String): Boolean =
    runCatching {
      page.evaluate("tag => (window.__parikshan_click ? window.__parikshan_click(tag) : false)", tag) as? Boolean ?: false
    }.getOrDefault(false)

  private fun invokeDomClick(tag: String): Boolean =
    runCatching {
      page.evaluate("tag => window.__parikshan_utils.invokeClick(tag)", tag) as? Boolean ?: false
    }.getOrDefault(false)

  private fun invokeBridgeInput(tag: String, text: String): Boolean =
    runCatching {
      page.evaluate("([tag, text]) => (window.__parikshan_input ? window.__parikshan_input(tag, text) : false)", arrayOf(tag, text)) as? Boolean ?: false
    }.getOrDefault(false)

  private fun invokeBridgeScroll(tag: String, direction: io.github.aryapreetam.parikshan.protocol.ScrollDirection): Boolean =
    false // Force fallback to native Playwright mouse wheel which works perfectly for Wasm Canvas

  companion object {
    private val connectMutex = Mutex()
    private var sharedPlaywright: Playwright? = null
    private var sharedBrowser: Browser? = null
    private var sharedContext: BrowserContext? = null
    private var sharedPage: Page? = null
    @Volatile
    private var lastRequestedVideoPath: String? = null
    private var playwrightTempDir: Path? = null

    init {
      Runtime.getRuntime().addShutdownHook(Thread({
        val targetPath = lastRequestedVideoPath
        val videoConfig = runCatching {
          ParikshanVideoConfig.fromSystemProperties()
        }.getOrNull()

        // Post-roll delay: keep recording for a bit after last command
        if (videoConfig != null && videoConfig.enabled && videoConfig.postRollMs > 0) {
          runCatching { Thread.sleep(videoConfig.postRollMs) }
        }

        // Read video reference BEFORE closing anything
        val videoObj = runCatching { sharedPage?.video() }.getOrNull()
        if (videoObj == null && videoConfig?.enabled == true) {
          System.err.println("WasmVideo: Could not get Video object from Page.")
        }

        // Close in order to finalize video
        runCatching { sharedPage?.close() }
        runCatching { sharedContext?.close() }
        
        val rawVideoPath = runCatching { videoObj?.path() }.getOrNull()
        System.err.println("WasmVideo: Playwright reports video path: $rawVideoPath")

        runCatching { sharedBrowser?.close() }
        runCatching { sharedPlaywright?.close() }

        if (targetPath != null && rawVideoPath != null) {
          runCatching {
            val finalVideoPath = Paths.get(targetPath)
            finalVideoPath.parent?.let { Files.createDirectories(it) }
            
            if (Files.exists(rawVideoPath)) {
              try {
                Files.copy(rawVideoPath, finalVideoPath, StandardCopyOption.REPLACE_EXISTING)
                val size = runCatching { Files.size(finalVideoPath) }.getOrNull()
                val sizeText = size?.toString() ?: "unknown"
                System.err.println("WasmVideo: Successfully saved recording to $targetPath (bytes=$sizeText)")
                runCatching { Files.deleteIfExists(rawVideoPath) }
              } catch (e: Throwable) {
                System.err.println("WasmVideo: Error copying video to $targetPath: ${'$'}{e.message}")
                e.printStackTrace()
              }
            } else {
              System.err.println("WasmVideo: Video file NOT found at expected path $rawVideoPath. No fallback attempted to avoid race conditions.")
            }
          }.onFailure {
            System.err.println("WasmVideo: Error saving video to $targetPath: ${it.message}")
            it.printStackTrace()
          }
        } else if (videoConfig?.enabled == true) {
          System.err.println("WasmVideo: Skip saving. targetPath=$targetPath, rawVideoPath=$rawVideoPath")
        }

        // Cleanup leftover temp directories generated by Playwright
        runCatching {
          playwrightTempDir?.let { dir ->
            if (Files.exists(dir)) {
              dir.toFile().deleteRecursively()
            }
          }
        }
      }, "parikshan-wasm-shutdown"))
    }

    suspend fun connect(config: ParikshanWasmConfig = ParikshanWasmConfig.fromSystemProperties()): WasmDriver = connectMutex.withLock {
      val isBrowserAlive = sharedBrowser?.isConnected == true
      val isPageClosed = sharedPage?.isClosed ?: true

      if (!isBrowserAlive || sharedPlaywright == null) {
        // Initial setup or browser crashed
        runCatching { sharedPage?.close() }
        runCatching { sharedContext?.close() }
        runCatching { sharedBrowser?.close() }
        runCatching { sharedPlaywright?.close() }
        
        val playwright = Playwright.create()
        sharedPlaywright = playwright
        val launchOptions = BrowserType.LaunchOptions().setHeadless(config.headless)
        launchOptions.setArgs(listOf(
          "--window-size=${config.viewportWidth + 50},${config.viewportHeight + 100}",
          "--disable-gpu",
          "--use-gl=angle",
          "--use-angle=swiftshader",
          "--no-sandbox"
        ))
        
        val browser = playwright.chromium().launch(launchOptions)
        sharedBrowser = browser
      }

      if (sharedPage == null || isPageClosed) {
        // Browser is alive, but we need a new page (e.g. after a video stop)
        val videoConfig = ParikshanVideoConfig.fromSystemProperties()
        val contextOptions = Browser.NewContextOptions().setViewportSize(config.viewportWidth, config.viewportHeight)

        if (videoConfig.enabled) {
          val tempDir = playwrightTempDir ?: Files.createTempDirectory("parikshan-wasm-video").also { playwrightTempDir = it }
          contextOptions.setRecordVideoDir(tempDir)
          
          if (videoConfig.videoWidth != null && videoConfig.videoHeight != null) {
            contextOptions.setRecordVideoSize(videoConfig.videoWidth, videoConfig.videoHeight)
          } else {
            contextOptions.setRecordVideoSize(config.viewportWidth, config.viewportHeight)
          }
          videoConfig.deviceScaleFactor?.let { contextOptions.setDeviceScaleFactor(it) }
        }

        val context = sharedBrowser!!.newContext(contextOptions)
        
        val initScript = """
          window.__parikshan_utils = {
            extractText: function(element) {
              if (!element) return null;
              const candidates = [
                element.innerText, element.textContent, element.getAttribute?.('aria-label'),
                element.getAttribute?.('title'), element.getAttribute?.('value'), element.value, element.placeholder
              ];
              for (const candidate of candidates) {
                const normalized = candidate?.trim?.();
                if (normalized) return normalized;
              }
              const labeledDescendant = element.querySelector?.('[aria-label]');
              const labeledText = labeledDescendant?.getAttribute?.('aria-label')?.trim?.();
              if (labeledText) return labeledText;
              return null;
            },
            findNode: function(tag) {
              const queue = [document.documentElement, document.body].filter(Boolean);
              const visited = new Set();
              let element = null;
              let i = 0;
              while (i < queue.length && element == null) {
                const current = queue[i++];
                if (!current || visited.has(current)) continue;
                visited.add(current);
                if (current.id === tag) { element = current; break; }
                const descendants = current.querySelectorAll?.(`[id="${'$'}{tag}"]`) ?? [];
                if (descendants.length > 0) { element = descendants[0]; break; }
                if (current.shadowRoot) queue.push(current.shadowRoot);
                const children = current.children ?? current.childNodes ?? [];
                for (const child of children) queue.push(child);
              }
              return element;
            },
            readNode: function(tag) {
              const element = this.findNode(tag);
              if (!element) return null;
              const rect = element.getBoundingClientRect();
              const style = window.getComputedStyle(element);
              const text = this.extractText(element);
              const visible = style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
              return JSON.stringify({
                tag,
                bounds: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom },
                visible, text
              });
            },
            readTree: function() {
              const nodes = [];
              const queue = [document.documentElement, document.body].filter(Boolean);
              const visited = new Set();
              let i = 0;
              while (i < queue.length) {
                const current = queue[i++];
                if (!current || visited.has(current)) continue;
                visited.add(current);
                if (current.id) {
                  const rect = current.getBoundingClientRect();
                  const style = window.getComputedStyle(current);
                  const text = this.extractText(current);
                  nodes.push({
                    tag: current.id,
                    bounds: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom },
                    visible: style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0,
                    text
                  });
                }
                if (current.shadowRoot) queue.push(current.shadowRoot);
                const children = current.children ?? current.childNodes ?? [];
                for (const child of children) queue.push(child);
              }
              return JSON.stringify(nodes);
            },
            invokeClick: function(tag) {
              const current = this.findNode(tag);
              if (current) {
                current.click?.();
                current.dispatchEvent?.(new MouseEvent('click', { bubbles: true, cancelable: true, composed: true }));
                return true;
              }
              return false;
            }
          };
        """.trimIndent()
        context.addInitScript(initScript)
        
        sharedContext = context

        val page = context.newPage()
        page.onConsoleMessage { msg ->
          val target = if (msg.type() == "error") System.err else System.out
          target.println("Wasm Console [${msg.type()}]: ${msg.text()}")
        }
        sharedPage = page

        page.navigate(config.appUrl)
        page.waitForFunction(
          "() => typeof window.__parikshan_getTreeJson === 'function'",
          null,
          Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
        )
      }

      return WasmDriver()
    }

    private suspend fun relaunchSharedPage(config: ParikshanWasmConfig) {
      connectMutex.withLock {
        val page = sharedPage ?: error("WasmDriver shared page is not initialized")
        if (page.isClosed()) {
          error("WasmDriver shared page is closed")
        }
        page.navigate(config.appUrl)
        page.waitForFunction(
          "() => typeof window.__parikshan_getTreeJson === 'function'",
          null,
          Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
        )
      }
    }
  }
}
