@file:OptIn(InternalParikshanApi::class)

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
import com.google.gson.JsonObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * JVM-side driver for Compose/Wasm. It opens the app in Playwright and invokes the
 * in-browser Parikshan bridge installed by Modifier.testTag instrumentation.
 */
internal class WasmDriver internal constructor(
  private val sessionToken: String = System.getProperty("parikshan.token") ?: "",
  val context: ParikshanTestContext? = null,
  private val instancePage: Page? = null,
  private val instanceContext: BrowserContext? = null
) : TestDriver {

  constructor(context: ParikshanTestContext) : this(
    sessionToken = context.sessionToken,
    context = context,
    instancePage = null,
    instanceContext = null
  )

  override val targetPlatform: String = "wasm"

  private val page: Page
    get() = instancePage ?: checkNotNull(sharedPage) { "WasmDriver shared page is not initialized" }

  private val pickerArmed = java.util.concurrent.atomic.AtomicBoolean(false)
  private val pickerHandled = java.util.concurrent.atomic.AtomicBoolean(false)
  @Volatile private var activePickerAction: io.github.aryapreetam.parikshan.protocol.PickerAction? = null
  @Volatile private var pickerSelectedPaths: List<String> = emptyList()
  @Volatile private var pickerWasCancelled: Boolean = false
  @Volatile private var pickerStartTimestamp: Long = 0L
  @Volatile private var pickerElapsedMs: Long = 0L
  private var pickerLatch = java.util.concurrent.CountDownLatch(1)
  private var fileChooserConsumer: java.util.function.Consumer<com.microsoft.playwright.FileChooser>? = null

  override fun validateAndNormalizePickerPaths(paths: List<String>): List<String> =
    PickerPathResolver.resolveAndValidatePaths(paths)

  private fun disarmFileChooser() {
    pickerArmed.set(false)
    fileChooserConsumer?.let { consumer ->
      runCatching {
        page.offFileChooser(consumer)
      }
      fileChooserConsumer = null
    }
    pickerLatch.countDown()
  }

  override suspend fun send(command: Command): Response {
    command.token = sessionToken
    return runCatching {
      withContext(playwrightDispatcher) {
        if (instancePage == null) {
          ensureActivePage(ParikshanWasmConfig.fromSystemProperties())
        }
        handleCommand(command)
      }
    }.getOrElse { throwable ->
      Response.Error(command.id, throwable.message ?: "Wasm driver error")
    }
  }

  override suspend fun close() {
    if (instanceContext != null) {
      withContext(playwrightDispatcher) {
        runCatching { disarmFileChooser() }
        runCatching { instancePage?.close() }
        runCatching { instanceContext.close() }
        context?.instanceId?.let {
          multiInstanceContexts.remove(it)
          releaseInstanceSlot(it)
        }
        markWasmSessionClosed(context?.instanceId)
      }
    }
  }

  override suspend fun relaunchApp() {
    withContext(playwrightDispatcher) {
      val config = ParikshanWasmConfig.fromSystemProperties()
      if (instancePage != null) {
        if (instancePage.isClosed) return@withContext
        runCatching {
          instancePage.evaluate("() => { sessionStorage.clear(); localStorage.clear(); }")
          val baseUrl = config.appUrl.trimEnd('/')
          val separator = if (baseUrl.contains("?")) "&" else "?"
          val token = sessionToken.ifBlank { System.getProperty("parikshan.token") ?: "" }
          val instId = context?.instanceId ?: "wasm"
          instancePage.navigate("$baseUrl${separator}parikshan_instance=$instId&parikshan_token=$token")
          instancePage.waitForFunction(
            "() => typeof window.__parikshan_getTreeJson === 'function'",
            null,
            Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
          )
        }
        return@withContext
      }
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
        relaunchSharedPage(config)
      }
    }
  }

  private suspend fun ensureActivePage(config: ParikshanWasmConfig) {
    connectMutex.withLock {
      ensureActivePageInternal(config)
    }
  }

  private suspend fun readNodeBySelector(selector: io.github.aryapreetam.parikshan.protocol.Selector): NodeSnapshot? {
    if ((selector is io.github.aryapreetam.parikshan.protocol.Selector.Tag || selector is io.github.aryapreetam.parikshan.protocol.Selector.Auto) && selector.index == null) {
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
        val result = invokeBridgeClickResult(selector)
        if (result.startsWith("DISABLED:")) {
          return Response.Error(command.id, result.removePrefix("DISABLED:").trim())
        }
        val bridgeClicked = invokeBridgeClick(selector)
        if (!bridgeClicked) {
          performPhysicalClick(node.bounds.centerX, node.bounds.centerY)
        }
        delay(10)
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val node = readNodeBySelector(selector)
          ?: return Response.Error(command.id, "No node found for selector '${selector.raw}'")
        if (!invokeBridgeInput(selector, command.text)) {
          page.mouse().move(node.bounds.centerX, node.bounds.centerY)
          page.mouse().down()
          delay(20)
          page.mouse().up()
          page.keyboard().press("ControlOrMeta+A")
          page.keyboard().type(command.text)
        }
        delay(10)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val node = readNodeBySelector(selector)
          ?: return Response.Error(command.id, "No node found for selector '${selector.raw}'")
        if (!invokeBridgeScroll(selector, command.direction)) {
          // Focus canvas and ensure it has focus before scrolling
          runCatching {
             page.evaluate("""() => {
               const canvas = document.querySelector('canvas');
               if (canvas) {
                 canvas.focus();
                 if (document.activeElement !== canvas) {
                   canvas.click(); // Force focus if focus() didn't work
                 }
               }
             }""")
          }
          // Reverting to centerX, centerY which worked before
          page.mouse().move(node.bounds.centerX, node.bounds.centerY)
          val (deltaX, deltaY) =
            when (command.direction) {
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Up -> 0.0 to -200.0
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Down -> 0.0 to 200.0
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Left -> -200.0 to 0.0
              io.github.aryapreetam.parikshan.protocol.ScrollDirection.Right -> 200.0 to 0.0
            }
          val steps = 5
          val stepX = deltaX / steps
          val stepY = deltaY / steps
          val delayPerStep = 20L
          repeat(steps) {
            page.mouse().wheel(stepX, stepY)
            delay(delayPerStep)
          }
        }
        delay(50)
        Response.Ok(command.id)
      }

      is Command.WaitFor -> {
        val deadline = System.currentTimeMillis() + command.timeoutMs
        while (System.currentTimeMillis() <= deadline) {
          val node = readNodeBySelector(selector)
          if (node?.visible == true) {
            return Response.NodeInfo(command.id, node.bounds, visible = true, text = node.text)
          }
          delay(15)
        }
        Response.Error(command.id, "Timed out waiting for '${selector.raw}' after ${command.timeoutMs}ms")
      }


      is Command.Drag -> {
        page.mouse().move(command.fromX, command.fromY)
        page.mouse().down()
        delay(30)
        val steps = 20
        val stepDelay = (command.durationMs / steps).coerceAtLeast(10L)
        for (i in 1..steps) {
          val progress = i.toDouble() / steps
          val curX = command.fromX + (command.toX - command.fromX) * progress
          val curY = command.fromY + (command.toY - command.fromY) * progress
          page.mouse().move(curX, curY)
          delay(stepDelay)
        }
        delay(30)
        page.mouse().up()
        Response.Ok(command.id)
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
      is Command.Reset -> Response.Ok(command.id)
      is Command.HideKeyboard -> Response.Ok(command.id)
      is Command.StartRecording -> {
        if (instancePage == null) {
          lastRequestedVideoPath = command.path
        }
        Response.Ok(command.id)
      }
      is Command.StopRecording -> {
        if (instancePage != null) {
          return Response.Ok(command.id)
        }
        try {
          connectMutex.withLock {
            val targetPath = lastRequestedVideoPath
            if (targetPath != null) {
              val videoConfig = runCatching { ParikshanVideoConfig.fromSystemProperties() }.getOrNull()
              if (videoConfig != null && videoConfig.postRollMs > 0) {
                delay(videoConfig.postRollMs)
              }

              val videoObj = runCatching { page.video() }.getOrNull()
              runCatching { page.close() }

              // Poll for the file to exist and have size > 0
              var rawVideoPath: Path? = null
              repeat(50) { attempt ->
                  rawVideoPath = runCatching { videoObj?.path() }.getOrNull()
                  if (rawVideoPath != null && Files.exists(rawVideoPath) && Files.size(rawVideoPath) > 0) {
                      return@repeat
                  }
                  delay(100)
              }

              if (rawVideoPath != null && Files.exists(rawVideoPath) && Files.size(rawVideoPath) > 0) {
                val finalVideoPath = Paths.get(targetPath)
                finalVideoPath.parent?.let { Files.createDirectories(it) }
                try {
                  Files.copy(rawVideoPath, finalVideoPath, StandardCopyOption.REPLACE_EXISTING)
                  val size = Files.size(finalVideoPath)
                  System.err.println("WasmVideo: Successfully saved recording to $targetPath (bytes=$size)")
                  registerWasmVideoPath(targetPath)
                  runCatching { Files.deleteIfExists(rawVideoPath) }
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
          System.err.println("WasmVideo: Error while stopping recording: ${throwable.message}")
          throwable.printStackTrace()
          Response.Error(command.id, throwable.message ?: "StopRecording failed")
        }
      }
      is Command.RegisterFilePicker -> {
        disarmFileChooser()
        activePickerAction = command.action
        pickerSelectedPaths = emptyList()
        pickerWasCancelled = false
        pickerStartTimestamp = System.currentTimeMillis()
        pickerElapsedMs = 0L
        pickerHandled.set(false)
        pickerLatch = java.util.concurrent.CountDownLatch(1)
        pickerArmed.set(true)

        val consumer = java.util.function.Consumer<com.microsoft.playwright.FileChooser> { fileChooser ->
          if (!pickerArmed.get() || !pickerHandled.compareAndSet(false, true)) return@Consumer
          pickerElapsedMs = System.currentTimeMillis() - pickerStartTimestamp

          when (val action = activePickerAction) {
            is io.github.aryapreetam.parikshan.protocol.PickerAction.Select -> {
              pickerSelectedPaths = action.paths
              pickerWasCancelled = false
              val pathsArray = action.paths.map { Paths.get(it).toAbsolutePath().normalize() }.toTypedArray()
              runCatching {
                fileChooser.setFiles(pathsArray)
              }.onFailure {
                System.err.println("Parikshan Wasm: fileChooser.setFiles failed: ${it.message}")
                it.printStackTrace()
              }
            }
            is io.github.aryapreetam.parikshan.protocol.PickerAction.Cancel, null -> {
              pickerSelectedPaths = emptyList()
              pickerWasCancelled = true
              runCatching {
                fileChooser.element().dispatchEvent("cancel")
              }
              runCatching {
                page.keyboard().press("Escape")
              }
            }
          }
          disarmFileChooser()
          pickerLatch.countDown()
        }
        fileChooserConsumer = consumer
        runCatching {
          page.onFileChooser(consumer)
          page.evaluate("() => 1")
        }
        Response.Ok(command.id)
      }

      is Command.UnregisterFilePicker -> {
        if (command.awaitCompletion && pickerArmed.get() && !pickerHandled.get()) {
          val deadline = System.currentTimeMillis() + command.timeoutMs
          while (System.currentTimeMillis() < deadline && pickerArmed.get() && !pickerHandled.get()) {
            runCatching {
              page.waitForTimeout(20.0)
            }
          }
        }

        val handled = pickerHandled.get()
        val paths = pickerSelectedPaths
        val cancelled = pickerWasCancelled
        val elapsed = pickerElapsedMs

        disarmFileChooser()

        Response.FilePickerHandled(
          id = command.id,
          wasHandled = handled,
          selectedPaths = paths,
          wasCancelled = cancelled,
          elapsedMs = elapsed
        )
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
    val bridgeTree = runCatching {
      val payload = page.evaluate("() => (window.__parikshan_getTreeJson ? window.__parikshan_getTreeJson() : '[]')") as? String ?: "[]"
      ProtocolJson.instance.decodeFromString(ListSerializer(NodeSnapshot.serializer()), payload)
    }.getOrDefault(emptyList())

    if (bridgeTree.any { it.tag.isNotEmpty() || !it.text.isNullOrBlank() }) {
      return bridgeTree
    }
    return readDomTree()
  }

  private fun readDomNode(tag: String): NodeSnapshot? {
    val payload = page.evaluate("tag => window.__parikshan_utils.readNode(tag)", tag) as? String ?: return null
    return ProtocolJson.instance.decodeFromString(NodeSnapshot.serializer(), payload)
  }

  private fun readDomTree(): List<NodeSnapshot> {
    val payload = page.evaluate("() => window.__parikshan_utils.readTree()") as? String ?: "[]"
    return ProtocolJson.instance.decodeFromString(ListSerializer(NodeSnapshot.serializer()), payload)
  }

  private suspend fun performPhysicalClick(x: Double, y: Double) {
    val nativeClicked = runCatching {
      page.mouse().click(x, y)
      true
    }.getOrDefault(false)

    if (!nativeClicked) {
      runCatching {
        page.evaluate(
          """([x, y]) => {
            function findCanvas(root) {
              if (!root) return null;
              if (root.tagName === 'CANVAS') return root;
              const children = root.children || [];
              for (let i = 0; i < children.length; i++) {
                const found = findCanvas(children[i]);
                if (found) return found;
              }
              if (root.shadowRoot) return findCanvas(root.shadowRoot);
              return null;
            }
            const canvas = findCanvas(document.body);
            if (!canvas) return false;
            const opts = { bubbles: true, cancelable: true, composed: true, clientX: x, clientY: y, button: 0 };
            canvas.dispatchEvent(new PointerEvent('pointerdown', opts));
            canvas.dispatchEvent(new PointerEvent('pointerup', opts));
            canvas.dispatchEvent(new MouseEvent('click', opts));
            return true;
          }""",
          listOf(x, y)
        )
      }
    }
  }

  private fun invokeBridgeClickResult(selector: io.github.aryapreetam.parikshan.protocol.Selector): String {
    val tag = selector.raw
    val index = selector.index
    return runCatching {
      if (index != null) {
        page.evaluate(
          "([tag, index]) => (window.__parikshan_click_indexed_result ? window.__parikshan_click_indexed_result(tag, index) : null)",
          listOf<Any>(tag, index)
        ) as? String
      } else {
        page.evaluate(
          "tag => (window.__parikshan_click_result ? window.__parikshan_click_result(tag) : null)",
          tag
        ) as? String
      }
    }.getOrNull() ?: "NO_BRIDGE"
  }

  private fun invokeBridgeClick(selector: io.github.aryapreetam.parikshan.protocol.Selector): Boolean {
    val tag = selector.raw
    val index = selector.index
    return runCatching {
      if (index != null) {
        page.evaluate(
          "([tag, index]) => (window.__parikshan_click_indexed ? window.__parikshan_click_indexed(tag, index) : false)",
          listOf<Any>(tag, index)
        ) as? Boolean ?: false
      } else {
        page.evaluate(
          "tag => (window.__parikshan_click ? window.__parikshan_click(tag) : false)",
          tag
        ) as? Boolean ?: false
      }
    }.getOrDefault(false)
  }

  private fun invokeDomClick(selector: io.github.aryapreetam.parikshan.protocol.Selector): Boolean {
    val tag = selector.raw
    if (selector.index != null) return false
    return runCatching {
      page.evaluate("tag => window.__parikshan_utils.invokeClick(tag)", tag) as? Boolean ?: false
    }.getOrDefault(false)
  }

  private fun invokeBridgeInput(selector: io.github.aryapreetam.parikshan.protocol.Selector, text: String): Boolean {
    val tag = selector.raw
    val index = selector.index
    return runCatching {
      if (index != null) {
        page.evaluate("([tag, text, index]) => (window.__parikshan_input_indexed ? window.__parikshan_input_indexed(tag, text, index) : false)", listOf<Any>(tag, text, index)) as? Boolean ?: false
      } else {
        page.evaluate("([tag, text]) => (window.__parikshan_input ? window.__parikshan_input(tag, text) : false)", listOf<Any>(tag, text)) as? Boolean ?: false
      }
    }.getOrDefault(false)
  }

  private fun invokeBridgeScroll(selector: io.github.aryapreetam.parikshan.protocol.Selector, direction: io.github.aryapreetam.parikshan.protocol.ScrollDirection): Boolean =
    false // Force fallback to native Playwright mouse wheel which works perfectly for Wasm Canvas

  companion object {
    private val PARIKSHAN_UTILS_INIT_SCRIPT: String = """
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
            const descendants = current.querySelectorAll?.(`[id="${'$'}tag"]`) ?? [];
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

    private val playwrightExecutor = Executors.newSingleThreadExecutor { runnable ->
      Thread(runnable, "parikshan-playwright").apply { isDaemon = true }
    }
    private val playwrightDispatcher = playwrightExecutor.asCoroutineDispatcher()
    private val connectMutex = Mutex()
    private var sharedPlaywright: Playwright? = null
    private var sharedBrowser: Browser? = null
    private var sharedContext: BrowserContext? = null
    private var sharedPage: Page? = null
    private val multiInstanceContexts = java.util.concurrent.ConcurrentHashMap<String, BrowserContext>()
    private val activeInstanceSlots = java.util.concurrent.ConcurrentHashMap<String, Int>()
    @Volatile
    private var lastRequestedVideoPath: String? = null
    private var playwrightTempDir: Path? = null
    private var persistentUserDataDir: Path? = null
    @Volatile
    private var activeContext: ParikshanTestContext? = null

    internal fun acquireInstanceSlot(instanceId: String): Int = synchronized(activeInstanceSlots) {
      activeInstanceSlots[instanceId]?.let { return@synchronized it }
      val used = activeInstanceSlots.values.toSet()
      val slot = generateSequence(0) { it + 1 }.first { it !in used }
      activeInstanceSlots[instanceId] = slot
      slot
    }

    internal fun releaseInstanceSlot(instanceId: String) = synchronized(activeInstanceSlots) {
      activeInstanceSlots.remove(instanceId)
    }

    internal fun resetInstanceSlotsForTesting() = synchronized(activeInstanceSlots) {
      activeInstanceSlots.clear()
    }

    internal fun resolveMultiInstanceViewportSize(config: ParikshanWasmConfig): Pair<Int, Int>? =
      if (config.headless) Pair(config.viewportWidth, config.viewportHeight) else null

    private fun isKeepAliveEnabled(): Boolean =
      System.getProperty("parikshan.keepAlive") == "true" || System.getenv("PARIKSHAN_KEEP_ALIVE") == "true"

    private fun isPortOpen(port: Int): Boolean {
      return try {
        java.net.Socket("127.0.0.1", port).use { true }
      } catch (_: Exception) {
        false
      }
    }

    init {
      Runtime.getRuntime().addShutdownHook(Thread({
        val targetPath = lastRequestedVideoPath
        val videoConfig = runCatching {
          ParikshanVideoConfig.fromSystemProperties()
        }.getOrNull()

        // Post-roll delay: keep recording for a bit after last command
        if (videoConfig != null && videoConfig.enabled && targetPath != null && videoConfig.postRollMs > 0) {
          runCatching { Thread.sleep(videoConfig.postRollMs) }
        }

        val rawVideoPath = runCatching {
          playwrightExecutor.submit<Path?> {
            // Read video reference BEFORE closing page/context
            val videoObj = runCatching { sharedPage?.video() }.getOrNull()
            if (videoObj == null && videoConfig?.enabled == true && targetPath != null) {
              System.err.println("WasmVideo: Could not get Video object from Page.")
            }

            multiInstanceContexts.values.forEach { runCatching { it.close() } }
            multiInstanceContexts.clear()
            activeInstanceSlots.clear()

            // Close page & context first so Playwright flushes the recording while sharedPlaywright is still open
            if (!isKeepAliveEnabled()) {
              runCatching { sharedPage?.close() }
              runCatching { sharedContext?.close() }
            }

            val resolvedVideoPath = runCatching { videoObj?.path() }.getOrNull()
            if (targetPath != null) {
              System.err.println("WasmVideo: Playwright reports video path: $resolvedVideoPath")
            }

            // Close browser and Playwright AFTER videoObj.path() has been read
            if (!isKeepAliveEnabled()) {
              runCatching { sharedBrowser?.close() }
              runCatching { sharedPlaywright?.close() }
            }

            resolvedVideoPath
          }.get(15, TimeUnit.SECONDS)
        }.getOrNull()

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
                registerWasmVideoPath(targetPath)
                runCatching { Files.deleteIfExists(rawVideoPath) }
              } catch (e: Throwable) {
                System.err.println("WasmVideo: Error copying video to $targetPath: ${e.message}")
                e.printStackTrace()
              }
            } else {
              System.err.println("WasmVideo: Video file NOT found at expected path $rawVideoPath. No fallback attempted to avoid race conditions.")
            }
          }.onFailure {
            System.err.println("WasmVideo: Error saving video to $targetPath: ${it.message}")
            it.printStackTrace()
          }
        } else if (videoConfig?.enabled == true && targetPath != null) {
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
        if (!isKeepAliveEnabled()) {
          runCatching {
            persistentUserDataDir?.let { dir ->
              if (Files.exists(dir)) {
                dir.toFile().deleteRecursively()
              }
            }
          }
        }
        runCatching { playwrightExecutor.shutdownNow() }
      }, "parikshan-wasm-shutdown"))
    }

    /**
     * Constructs standard command-line flags for launching Chromium instances.
     *
     * Configures WebGL fallback rendering, automation flags to suppress info-bars,
     * and outer window positioning and dimensions.
     *
     * @param config The [ParikshanWasmConfig] holding viewport dimensions and execution options.
     * @return List of Chromium command-line flag strings.
     */
    private fun getChromiumArgs(config: ParikshanWasmConfig): List<String> {
      val chromiumArgs = mutableListOf(
        "--disable-gpu",
        "--use-gl=angle",
        "--use-angle=swiftshader",
        "--force-device-scale-factor=1",
        "--enable-automation",
        "--disable-infobars",
        "--test-type",
        "--no-first-run",
        "--no-default-browser-check"
      )
      if (config.appMode) {
        chromiumArgs.add("--app=${config.appUrl}")
        chromiumArgs.add("--window-size=${config.viewportWidth},${config.viewportHeight}")
      } else {
        chromiumArgs.add("--window-size=${config.viewportWidth},${config.viewportHeight}")
      }
      if (!config.headless && config.windowX != null && config.windowY != null) {
        chromiumArgs.add("--window-position=${config.windowX},${config.windowY}")
      }
      return chromiumArgs
    }

    private fun ensureActivePageInternal(config: ParikshanWasmConfig) {
      val isKeepAlive = isKeepAliveEnabled()
      val wasAlreadyRunning = isPortOpen(9222)
      val isBrowserAlive = sharedBrowser?.isConnected == true || (sharedContext != null && sharedPage != null && !sharedPage!!.isClosed)
      if (!isBrowserAlive || sharedPlaywright == null) {
        runCatching { sharedPage?.close() }
        runCatching { sharedContext?.close() }
        if (!isKeepAlive) {
          runCatching { sharedBrowser?.close() }
          runCatching { sharedPlaywright?.close() }
        }

        sharedPage = null
        sharedContext = null
        if (!isKeepAlive) {
          sharedBrowser = null
          sharedPlaywright = null
        }

        if (isKeepAlive && !wasAlreadyRunning) {
          runCatching {
            val tempPlaywright = Playwright.create()
            val executable = tempPlaywright.chromium().executablePath()
            val userDataDir = Files.createTempDirectory("parikshan-wasm-cdp-user")
            val args = mutableListOf(
              executable,
              "--remote-debugging-port=9222",
              "--user-data-dir=${userDataDir.toAbsolutePath()}",
              "--disable-gpu",
              "--use-gl=angle",
              "--use-angle=swiftshader",
              "--force-device-scale-factor=1",
              "--enable-automation",
              "--disable-infobars",
              "--test-type",
              "--no-first-run",
              "--no-default-browser-check",
              "--disable-features=Translate,BackForwardCache"
            )
            if (config.appMode) {
              args.add("--app=${config.appUrl}")
              args.add("--window-size=${config.viewportWidth},${config.viewportHeight}")
            } else {
              args.add("--window-size=${config.viewportWidth},${config.viewportHeight}")
            }
            if (!config.headless && config.windowX != null && config.windowY != null) {
              args.add("--window-position=${config.windowX},${config.windowY}")
            }
            if (config.headless) {
              args.add("--headless")
            }
            val pb = ProcessBuilder(args)
            val logFile = Files.createTempFile("parikshan-chrome-", ".log").toFile()
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            pb.redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            pb.start()
            
            tempPlaywright.close()

            for (i in 1..30) {
              if (isPortOpen(9222)) break
              Thread.sleep(100)
            }
          }
        }

        if (isKeepAlive && isPortOpen(9222)) {
          val playwright = Playwright.create()
          sharedPlaywright = playwright
          runCatching {
            sharedBrowser = playwright.chromium().connectOverCDP("http://127.0.0.1:9222")
          }.onFailure {
            sharedBrowser = null
            playwright.close()
            sharedPlaywright = null
          }
        }

        if (sharedPlaywright == null) {
          val playwright = Playwright.create()
          sharedPlaywright = playwright
          val chromiumArgs = getChromiumArgs(config)

          if (config.appMode) {
            val userDataDir = Files.createTempDirectory("parikshan-wasm-appmode-user")
            persistentUserDataDir = userDataDir
            val videoConfig = ParikshanVideoConfig.fromSystemProperties()
            val launchOptions = BrowserType.LaunchPersistentContextOptions()
              .setHeadless(config.headless)
              .setArgs(chromiumArgs)
              .setViewportSize(config.viewportWidth, config.viewportHeight - 28)

            if (videoConfig.enabled) {
              val tempDir = playwrightTempDir ?: Files.createTempDirectory("parikshan-wasm-video").also { playwrightTempDir = it }
              launchOptions.setRecordVideoDir(tempDir)
              if (videoConfig.videoWidth != null && videoConfig.videoHeight != null) {
                launchOptions.setRecordVideoSize(videoConfig.videoWidth, videoConfig.videoHeight)
              } else {
                launchOptions.setRecordVideoSize(config.viewportWidth, config.viewportHeight)
              }
            }
            val context = playwright.chromium().launchPersistentContext(userDataDir, launchOptions)
            sharedContext = context
          } else {
            // Unified browser process shared between single-instance and multi-instance contexts.
            val launchOptions = BrowserType.LaunchOptions()
              .setHeadless(config.headless)
              .setArgs(chromiumArgs.filterNot { it.startsWith("--app=") })
            sharedBrowser = playwright.chromium().launch(launchOptions)
          }
        }
      }

      if (sharedPage == null || sharedPage!!.isClosed) {
        val videoConfig = ParikshanVideoConfig.fromSystemProperties()
        val contextOptions = Browser.NewContextOptions()
        if (config.appMode) {
          contextOptions.setViewportSize(config.viewportWidth, config.viewportHeight - 28)
        } else {
          contextOptions.setViewportSize(null)
        }

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

        val context = if (sharedContext != null) {
          sharedContext!!
        } else if (isKeepAlive && sharedBrowser != null) {
          sharedBrowser!!.contexts().firstOrNull() ?: sharedBrowser!!.newContext(contextOptions)
        } else {
          sharedBrowser!!.newContext(contextOptions)
        }

        context.addInitScript(PARIKSHAN_UTILS_INIT_SCRIPT)
        sharedContext = context

        val targetUrlNormalized = config.appUrl.trimEnd('/')
        var page = context.pages().firstOrNull { it.url().trimEnd('/').startsWith(targetUrlNormalized) }
        if (page == null) {
          val existingPage = context.pages().firstOrNull()
          if (existingPage != null && (existingPage.url() == "about:blank" || existingPage.url().isBlank())) {
            page = existingPage
          } else {
            page = existingPage ?: context.newPage()
          }
        }

        if (context.pages().size > 1) {
          context.pages().filter { it != page && (it.url() == "about:blank" || it.url().isBlank()) }.forEach {
            runCatching { it.close() }
          }
        }

        page.onConsoleMessage { msg ->
          val target = if (msg.type() == "error") System.err else System.out
          target.println("Wasm Console [${msg.type()}]: ${msg.text()} at ${msg.location()}")
          if (msg.type() == "error") {
            msg.args().forEach { arg ->
              runCatching {
                target.println("  Wasm Console Error Arg: ${arg.jsonValue()}")
              }
            }
          }
        }
        page.onClose { markWasmSessionClosed() }
        sharedPage = page
        markWasmSessionActive()

        // Set viewport and window bounds via CDP for reliable override across platforms
        fun applyViewport() {
          runCatching {
            if (config.appMode) {
              page.setViewportSize(config.viewportWidth, config.viewportHeight - 28)
              page.evaluate("() => { window.resizeTo(${config.viewportWidth}, ${config.viewportHeight}); }")
            } else if (!config.headless) {
              // Non-app mode: use CDP Browser.setWindowBounds for reliable window
              // positioning. CLI --window-position is ignored by macOS window manager;
              // CDP is a programmatic override that works on macOS, Linux, and Windows.
              val cdp = page.context().newCDPSession(page)
              try {
                val windowInfo = cdp.send("Browser.getWindowForTarget")
                val windowId = windowInfo?.get("windowId")?.asInt
                if (windowId != null) {
                  val bounds = JsonObject().apply {
                    if (config.windowX != null && config.windowY != null) {
                      addProperty("left", config.windowX)
                      addProperty("top", config.windowY)
                    }
                    addProperty("width", config.viewportWidth)
                    addProperty("height", config.viewportHeight)
                    addProperty("windowState", "normal")
                  }
                  val params = JsonObject().apply {
                    addProperty("windowId", windowId)
                    add("bounds", bounds)
                  }
                  cdp.send("Browser.setWindowBounds", params)
                }
              } finally {
                cdp.detach()
              }
            }
          }
        }

        applyViewport()

        val currentUrlNormalized = page.url().trimEnd('/')
        val isErrorOrBlank = currentUrlNormalized.startsWith("chrome-error://") || 
                             currentUrlNormalized == "about:blank" || 
                             currentUrlNormalized.isEmpty()

        if (wasAlreadyRunning && !isErrorOrBlank && currentUrlNormalized == targetUrlNormalized) {
          val reloadSuccess = runCatching {
            page.evaluate("() => { sessionStorage.clear(); localStorage.clear(); location.reload(); }")
          }.isSuccess
          if (!reloadSuccess) {
            page.navigate(config.appUrl)
          }
        } else {
          var navSuccess = false
          for (attempt in 1..5) {
            val res = runCatching {
              page.navigate(config.appUrl)
            }
            if (res.isSuccess) {
              navSuccess = true
              break
            }
            Thread.sleep(300)
          }
          if (!navSuccess) {
            page.navigate(config.appUrl)
          }
        }

        // Re-apply viewport after navigation to ensure Compose renders at correct size
        applyViewport()

        runCatching { page.bringToFront() }

        page.waitForFunction(
          "() => typeof window.__parikshan_getTreeJson === 'function'",
          null,
          Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
        )
      }
    }

    private fun ensureBrowserRunning(config: ParikshanWasmConfig) {
      if (sharedBrowser?.isConnected == true && sharedPlaywright != null) return
      val playwright = sharedPlaywright ?: Playwright.create().also { sharedPlaywright = it }
      val chromiumArgs = getChromiumArgs(config).filterNot { it.startsWith("--app=") }
      val launchOptions = BrowserType.LaunchOptions()
        .setHeadless(config.headless)
        .setArgs(chromiumArgs)
      sharedBrowser = playwright.chromium().launch(launchOptions)
    }

    suspend fun connect(
      config: ParikshanWasmConfig = ParikshanWasmConfig.fromSystemProperties(),
      context: ParikshanTestContext? = null
    ): WasmDriver = withContext(playwrightDispatcher) {
      connectMutex.withLock {
        val isMultiInstance = context != null && context.instanceId.isNotBlank() && context.instanceId != "wasm"
        if (!isMultiInstance) {
          activeContext = context
          val windowPos = context?.windowPosition
          val effectiveConfig = if (windowPos != null) {
            config.copy(windowX = windowPos.first, windowY = windowPos.second)
          } else {
            config
          }
          ensureActivePageInternal(effectiveConfig)
          return@withLock WasmDriver(
            sessionToken = context?.sessionToken ?: (System.getProperty("parikshan.token") ?: ""),
            context = context
          )
        }

        // Multi-instance actor isolated context (video disabled in 0.1.0 to avoid orphaned temp recordings)
        ensureBrowserRunning(config)
        val browser = checkNotNull(sharedBrowser) { "Shared browser must be initialized for multi-instance Wasm" }

        val contextOptions = Browser.NewContextOptions()
        val fixedViewport = resolveMultiInstanceViewportSize(config)
        if (fixedViewport != null) {
          contextOptions.setViewportSize(fixedViewport.first, fixedViewport.second)
        } else {
          contextOptions.setViewportSize(null)
        }

        val actorContext = browser.newContext(contextOptions)
        var actorPage: Page? = null
        try {
          actorContext.addInitScript(PARIKSHAN_UTILS_INIT_SCRIPT)
          multiInstanceContexts[context.instanceId] = actorContext

          actorPage = actorContext.newPage()
          val pageRef = actorPage
          pageRef.onConsoleMessage { msg ->
            val target = if (msg.type() == "error") System.err else System.out
            target.println("Wasm Console [${context.instanceId}] [${msg.type()}]: ${msg.text()} at ${msg.location()}")
          }
          pageRef.onClose { markWasmSessionClosed(context.instanceId) }

          val instanceIndex = acquireInstanceSlot(context.instanceId)
          val windowX = (config.windowX ?: 10) + (instanceIndex * (config.viewportWidth + 20))
          val windowY = config.windowY ?: 50

          fun applyActorViewport() {
            runCatching {
              if (fixedViewport != null) {
                pageRef.setViewportSize(fixedViewport.first, fixedViewport.second)
              } else {
                val cdp = actorContext.newCDPSession(pageRef)
                try {
                  val windowInfo = cdp.send("Browser.getWindowForTarget")
                  val windowId = windowInfo?.get("windowId")?.asInt
                  if (windowId != null) {
                    val bounds = JsonObject().apply {
                      addProperty("left", windowX)
                      addProperty("top", windowY)
                      addProperty("width", config.viewportWidth)
                      addProperty("height", config.viewportHeight)
                      addProperty("windowState", "normal")
                    }
                    val params = JsonObject().apply {
                      addProperty("windowId", windowId)
                      add("bounds", bounds)
                    }
                    cdp.send("Browser.setWindowBounds", params)
                  }
                } finally {
                  cdp.detach()
                }
              }
            }
          }

          applyActorViewport()

          val baseUrl = config.appUrl.trimEnd('/')
          val separator = if (baseUrl.contains("?")) "&" else "?"
          val token = context.sessionToken.ifBlank { System.getProperty("parikshan.token") ?: "" }
          val targetUrl = "$baseUrl${separator}parikshan_instance=${context.instanceId}&parikshan_token=$token"

          var navSuccess = false
          for (attempt in 1..5) {
            val res = runCatching { pageRef.navigate(targetUrl) }
            if (res.isSuccess) {
              navSuccess = true
              break
            }
            delay(300)
          }
          if (!navSuccess) {
            pageRef.navigate(targetUrl)
          }

          applyActorViewport()
          runCatching { pageRef.bringToFront() }

          pageRef.waitForFunction(
            "() => typeof window.__parikshan_getTreeJson === 'function'",
            null,
            Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
          )

          markWasmSessionActive(context.instanceId, context.port, token)

          WasmDriver(
            sessionToken = token,
            context = context,
            instancePage = pageRef,
            instanceContext = actorContext
          )
        } catch (t: Throwable) {
          multiInstanceContexts.remove(context.instanceId)
          releaseInstanceSlot(context.instanceId)
          runCatching { actorPage?.close() }
          runCatching { actorContext.close() }
          throw t
        }
      }
    }

    suspend fun connect(context: ParikshanTestContext): WasmDriver =
      connect(config = ParikshanWasmConfig.fromSystemProperties(), context = context)

    private fun relaunchSharedPage(config: ParikshanWasmConfig) {
      val page = sharedPage ?: error("WasmDriver shared page is not initialized")
      if (page.isClosed()) {
        error("WasmDriver shared page is closed")
      }
      runCatching {
        page.evaluate("() => { sessionStorage.clear(); localStorage.clear(); location.reload(); }")
      }
      page.waitForFunction(
        "() => typeof window.__parikshan_getTreeJson === 'function'",
        null,
        Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
      )
    }

    private fun registerWasmVideoPath(targetPath: String) {
      println("[PARIKSHAN_VIDEO_PATH] $targetPath")
      try {
        val finalVideoPath = Paths.get(targetPath)
        val indexFile = File(finalVideoPath.parent.toFile(), "video-index.txt")
        indexFile.parentFile?.mkdirs()
        synchronized(WasmDriver::class.java) {
          indexFile.appendText("$targetPath\n")
        }
      } catch (e: Exception) {
        System.err.println("WARN: Failed to write to video-index.txt in WasmDriver: ${e.message}")
      }
    }

    /**
     * Writes session state properties to `build/parikshan/wasm-session.properties`
     * and actor-keyed descriptor in `build/parikshan/sessions/{instanceId}.json`.
     */
    private fun markWasmSessionActive(
      instanceId: String = activeContext?.instanceId ?: "wasm",
      explicitPort: Int? = null,
      explicitToken: String? = null
    ) {
      runCatching {
        val projectRootDirProp = System.getProperty("parikshan.projectRootDir")
        val baseDir = if (!projectRootDirProp.isNullOrBlank()) {
          File(projectRootDirProp, "build/parikshan")
        } else {
          File(System.getProperty("user.dir"), "build/parikshan")
        }
        baseDir.mkdirs()
        File(baseDir, "wasm-session.properties").writeText("status=active\ntimestamp=${System.currentTimeMillis()}\n")

        val ctx = activeContext
        val port = explicitPort ?: ctx?.port ?: 9876
        val token = explicitToken ?: ctx?.sessionToken ?: (System.getProperty("parikshan.token") ?: "")
        val sessionsDir = File(baseDir, "sessions")
        sessionsDir.mkdirs()
        val sessionJson = """{"instanceId":"$instanceId","target":"wasm","port":$port,"sessionToken":"$token","timestamp":${System.currentTimeMillis()}}"""
        File(sessionsDir, "$instanceId.json").writeText(sessionJson)
      }
    }

    /**
     * Writes session state properties to `build/parikshan/wasm-session.properties`
     * and clears actor-keyed descriptor in `build/parikshan/sessions/{instanceId}.json`.
     */
    private fun markWasmSessionClosed(instanceId: String? = null) {
      runCatching {
        val projectRootDirProp = System.getProperty("parikshan.projectRootDir")
        val baseDir = if (!projectRootDirProp.isNullOrBlank()) {
          File(projectRootDirProp, "build/parikshan")
        } else {
          File(System.getProperty("user.dir"), "build/parikshan")
        }
        baseDir.mkdirs()
        val id = instanceId ?: activeContext?.instanceId ?: "wasm"
        val sessionFile = File(baseDir, "sessions/$id.json")
        if (sessionFile.exists()) {
          sessionFile.delete()
        }
        val remainingSessions = File(baseDir, "sessions").listFiles()?.filter { it.extension == "json" } ?: emptyList()
        if (remainingSessions.isEmpty()) {
          File(baseDir, "wasm-session.properties").writeText("status=closed\ntimestamp=${System.currentTimeMillis()}\n")
        }
      }
    }
  }
}
