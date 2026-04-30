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
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
      handleCommand(command)
    }.getOrElse { throwable ->
      Response.Error(command.id, throwable.message ?: "Wasm driver error")
    }
  }

  override suspend fun close() {
    // Shared browser across tests. JVM shutdown hook will close it.
  }

  private suspend fun handleCommand(command: Command): Response =
    when (command) {
      is Command.Ping -> Response.Ok(command.id)

      is Command.GetTree ->
        Response.Tree(
          id = command.id,
          nodes = readTree()
        )

      is Command.AssertVisible -> {
        val node = readNode(command.tag) ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!node.visible) {
          return Response.Error(command.id, "Node '${command.tag}' exists but is not visible")
        }
        Response.NodeInfo(command.id, node.bounds, visible = true, text = node.text)
      }

      is Command.AssertText -> {
        val node = readNode(command.tag) ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (node.text != command.expected) {
          return Response.Error(
            command.id,
            "Text mismatch for '${command.tag}'. expected='${command.expected}' actual='${node.text}'"
          )
        }
        Response.Ok(command.id)
      }

      is Command.Click -> {
        val node = readNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!invokeBridgeClick(command.tag)) {
          if (!invokeDomClick(command.tag)) {
            page.mouse().click(node.bounds.centerX, node.bounds.centerY)
          }
        }
        delay(100)
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = readNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!invokeBridgeInput(command.tag, command.text)) {
          page.mouse().click(node.bounds.centerX, node.bounds.centerY)
          page.keyboard().press("ControlOrMeta+A")
          page.keyboard().type(command.text)
        }
        delay(100)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = readNode(command.tag)
          ?: return Response.Error(command.id, "No node found for tag '${command.tag}'")
        if (!invokeBridgeScroll(command.tag, command.direction)) {
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
          val node = readNode(command.tag)
          if (node?.visible == true) {
            return Response.NodeInfo(command.id, node.bounds, visible = true, text = node.text)
          }
          delay(120)
        }
        Response.Error(command.id, "Timed out waiting for '${command.tag}' after ${command.timeoutMs}ms")
      }

      is Command.Screenshot -> {
        val path = command.hostPath.ifBlank { command.devicePath }
        page.screenshot(Page.ScreenshotOptions().setPath(Paths.get(path)).setFullPage(true))
        Response.Ok(command.id)
      }

      is Command.PressBack -> Response.Ok(command.id)
      is Command.PressHome -> Response.Ok(command.id)
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
                // Allow a brief post-roll so the last actions are captured
                delay(videoConfig.postRollMs)
              }

              val videoObj = runCatching { sharedPage?.video() }.getOrNull()

              // Close the page to force Playwright to finalize the recording for this page
              runCatching { sharedPage?.close() }

              // Give Playwright a moment to rename/flush the video file
              delay(1000)

              val rawVideoPath = runCatching { videoObj?.path() }.getOrNull()

              if (rawVideoPath != null && Files.exists(rawVideoPath)) {
                val finalVideoPath = Paths.get(targetPath)
                finalVideoPath.parent?.let { Files.createDirectories(it) }
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
                System.err.println("WasmVideo: Video file NOT found at expected path $rawVideoPath. Searching in temp dir...")
                val recoveredPath = playwrightTempDir?.let { dir ->
                  if (Files.exists(dir)) {
                    Files.list(dir).use { stream ->
                      stream.filter { it.toString().endsWith(".webm") || it.toString().endsWith(".mp4") }
                        .findFirst().orElse(null)
                    }
                  } else null
                }

                if (recoveredPath != null && Files.exists(recoveredPath)) {
                  val finalVideoPath = Paths.get(targetPath)
                  finalVideoPath.parent?.let { Files.createDirectories(it) }
                  try {
                    Files.copy(recoveredPath, finalVideoPath, StandardCopyOption.REPLACE_EXISTING)
                    val size = runCatching { Files.size(finalVideoPath) }.getOrNull()
                    val sizeText = size?.toString() ?: "unknown"
                    System.err.println("WasmVideo: Recovered video from $recoveredPath and saved to $targetPath (bytes=$sizeText)")
                    runCatching { Files.deleteIfExists(recoveredPath) }
                  } catch (e: Throwable) {
                    System.err.println("WasmVideo: Error copying recovered video to $targetPath: ${'$'}{e.message}")
                    e.printStackTrace()
                  }
                } else {
                  System.err.println("WasmVideo: Failed to find any video file in temp dir: $playwrightTempDir")
                }
              }

              // Recreate a fresh page so subsequent commands continue to be recorded
              try {
                val wasmConfig = ParikshanWasmConfig.fromSystemProperties()
                val newPage = sharedContext?.newPage()
                newPage?.onConsoleMessage { msg ->
                  val target = if (msg.type() == "error") System.err else System.out
                  target.println("Wasm Console [${msg.type()}]: ${msg.text()}")
                }
                sharedPage = newPage
                newPage?.navigate(wasmConfig.appUrl)
                newPage?.waitForFunction(
                  "() => typeof window.__parikshan_getTreeJson === 'function'",
                  null,
                  Page.WaitForFunctionOptions().setTimeout(wasmConfig.bridgeReadyTimeoutMs.toDouble())
                )
              } catch (t: Throwable) {
                System.err.println("WasmVideo: Error recreating page after stop: ${t.message}")
              } finally {
                lastRequestedVideoPath = null
              }
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
    val payload =
      page.evaluate(
        """
        tag => {
          const extractText = (element) => {
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
          };
          const queue = [document.documentElement, document.body].filter(Boolean);
          const visited = new Set();
          let element = null;
          while (queue.length > 0 && element == null) {
            const current = queue.shift();
            if (!current || visited.has(current)) continue;
            visited.add(current);
            if (current.id === tag) { element = current; break; }
            const descendants = current.querySelectorAll?.(`[id="${tag}"]`) ?? [];
            if (descendants.length > 0) { element = descendants[0]; break; }
            if (current.shadowRoot) queue.push(current.shadowRoot);
            const children = current.children ?? current.childNodes ?? [];
            for (const child of children) queue.push(child);
          }
          if (!element) return null;
          const rect = element.getBoundingClientRect();
          const style = window.getComputedStyle(element);
          const text = extractText(element);
          const visible = style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
          return JSON.stringify({
            tag,
            bounds: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom },
            visible, text
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
      page.evaluate("tag => (window.__parikshan_click ? window.__parikshan_click(tag) : false)", tag) as? Boolean ?: false
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
            if (current.shadowRoot) queue.push(current.shadowRoot);
            const children = current.children ?? current.childNodes ?? [];
            for (const child of children) queue.push(child);
          }
          return false;
        }
        """.trimIndent(),
        tag
      ) as? Boolean ?: false
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
        
        // Wait for Playwright to finalize the video file on disk.
        // It often needs a moment to rename from .temp to .webm/mp4.
        Thread.sleep(1000)

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
              System.err.println("WasmVideo: Video file NOT found at expected path $rawVideoPath. Searching in temp dir...")
              val recoveredPath = playwrightTempDir?.let { dir ->
                if (Files.exists(dir)) {
                  Files.list(dir).use { stream ->
                    stream.filter { it.toString().endsWith(".webm") || it.toString().endsWith(".mp4") }
                      .findFirst().orElse(null)
                  }
                } else null
              }
              if (recoveredPath != null && Files.exists(recoveredPath)) {
                try {
                  Files.copy(recoveredPath, finalVideoPath, StandardCopyOption.REPLACE_EXISTING)
                  val size = runCatching { Files.size(finalVideoPath) }.getOrNull()
                  val sizeText = size?.toString() ?: "unknown"
                  System.err.println("WasmVideo: Recovered video from $recoveredPath and saved to $targetPath (bytes=$sizeText)")
                  runCatching { Files.deleteIfExists(recoveredPath) }
                } catch (e: Throwable) {
                  System.err.println("WasmVideo: Error copying recovered video to $targetPath: ${'$'}{e.message}")
                  e.printStackTrace()
                }
              } else {
                System.err.println("WasmVideo: Failed to find any video file in temp dir: $playwrightTempDir")
              }
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
      val isPageClosed = if (sharedPage == null) true else {
        runCatching { sharedPage?.isClosed() ?: true }.getOrNull() ?: true
      }

      if (sharedPlaywright == null || sharedPage == null || isPageClosed) {
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
        launchOptions.setArgs(listOf("--window-size=${config.viewportWidth + 50},${config.viewportHeight + 100}"))
        
        val browser = playwright.chromium().launch(launchOptions)
        sharedBrowser = browser

        val videoConfig = ParikshanVideoConfig.fromSystemProperties()
        System.err.println("WasmDriver: videoConfig=$videoConfig")
        val contextOptions = Browser.NewContextOptions().setViewportSize(config.viewportWidth, config.viewportHeight)

        if (videoConfig.enabled) {
          val tempDir = Files.createTempDirectory("parikshan-wasm-video")
          playwrightTempDir = tempDir
          contextOptions.setRecordVideoDir(tempDir)
          
          // If the user provided an explicit video size, use it. Otherwise default
          // to the Playwright context viewport so the recorded video matches what
          // was visible during the test run (avoids Playwright's 800x450 default).
          if (videoConfig.videoWidth != null && videoConfig.videoHeight != null) {
            contextOptions.setRecordVideoSize(videoConfig.videoWidth, videoConfig.videoHeight)
          } else {
            contextOptions.setRecordVideoSize(config.viewportWidth, config.viewportHeight)
          }

          // Only set device scale factor if explicitly requested. When not set,
          // Playwright will use the system/default device pixel ratio which preserves
          // the visual fidelity (DPR) users see on their displays.
          videoConfig.deviceScaleFactor?.let { contextOptions.setDeviceScaleFactor(it) }
        }

        val context = browser.newContext(contextOptions)
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
  }
}
