package io.github.aryapreetam.parikshan

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import io.github.aryapreetam.parikshan.client.ParikshanWasmConfig
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.Response
import java.nio.file.Paths
import kotlinx.coroutines.delay
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
        val clicked = page.evaluate("tag => window.__parikshan_click(tag)", command.tag) as? Boolean ?: false
        if (!clicked) return Response.Error(command.id, "Click failed for '${command.tag}'")
        delay(100)
        Response.Ok(command.id)
      }

      is Command.Input -> {
        val input =
          page.evaluate(
            "args => window.__parikshan_input(args[0], args[1])",
            listOf(command.tag, command.text)
          ) as? Boolean ?: false
        if (!input) return Response.Error(command.id, "Input failed for '${command.tag}'")
        delay(100)
        Response.Ok(command.id)
      }

      is Command.Scroll -> {
        val scrolled =
          page.evaluate(
            "args => window.__parikshan_scroll(args[0], args[1])",
            listOf(command.tag, command.direction.name)
          ) as? Boolean ?: false
        if (!scrolled) return Response.Error(command.id, "Scroll failed for '${command.tag}'")
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
          delay(50)
        }
        Response.Error(command.id, "Timed out waiting for '${command.tag}' after ${command.timeoutMs}ms")
      }

      is Command.Screenshot -> {
        val path = command.hostPath.ifBlank { command.devicePath }
        page.screenshot(Page.ScreenshotOptions().setPath(Paths.get(path)))
        Response.Ok(command.id)
      }

      is Command.PressBack -> {
        page.keyboard().press("Escape")
        Response.Ok(command.id)
      }

      is Command.PressHome -> Response.Ok(command.id)
      is Command.StartRecording -> Response.Ok(command.id)
      is Command.StopRecording -> Response.Ok(command.id)
      is Command.Shutdown -> Response.Ok(command.id)
    }

  private fun readNode(tag: String): NodeSnapshot? {
    val payload = page.evaluate("tag => window.__parikshan_getNodeJson(tag)", tag) as? String
    return payload?.let { ProtocolJson.instance.decodeFromString(NodeSnapshot.serializer(), it) }
  }

  private fun readTree(): List<NodeSnapshot> {
    val payload = page.evaluate("() => window.__parikshan_getTreeJson()") as? String ?: return emptyList()
    return ProtocolJson.instance.decodeFromString(ListSerializer(NodeSnapshot.serializer()), payload)
  }

  companion object {
    private var sharedPlaywright: Playwright? = null
    private var sharedBrowser: Browser? = null
    private var sharedPage: Page? = null

    init {
      Runtime.getRuntime().addShutdownHook(Thread {
        runCatching { sharedBrowser?.close() }
        runCatching { sharedPlaywright?.close() }
      })
    }

    suspend fun connect(config: ParikshanWasmConfig = ParikshanWasmConfig.fromSystemProperties()): WasmDriver {
      if (sharedPlaywright == null) {
        val playwright = Playwright.create()
        sharedPlaywright = playwright
        val browser =
          playwright.chromium().launch(
            BrowserType.LaunchOptions().setHeadless(config.headless)
          )
        sharedBrowser = browser
        val page =
          browser.newPage(
            Browser.NewPageOptions()
              .setViewportSize(config.viewportWidth, config.viewportHeight)
          )
        page.onConsoleMessage { msg ->
          val target = if (msg.type() == "error") System.err else System.out
          target.println("Wasm Console [${msg.type()}]: ${msg.text()}")
        }
        sharedPage = page
      }

      val page = sharedPage!!
      delay(2000) // Give Wasm app some time to load
      page.navigate(config.appUrl)
      page.waitForFunction(
        "() => typeof window.__parikshan_getTreeJson === 'function'",
        null,
        Page.WaitForFunctionOptions().setTimeout(config.bridgeReadyTimeoutMs.toDouble())
      )
      return WasmDriver()
    }
  }
}
