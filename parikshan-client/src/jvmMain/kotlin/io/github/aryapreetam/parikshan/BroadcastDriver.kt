package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class MultiTargetFailureException(message: String) : AssertionError(message)

class BroadcastDriver(
  val drivers: List<TestDriver>
) : TestDriver {
  override val targetPlatform: String = "sync"

  @Volatile
  private var activeRoutePlatform: String? = null

  override fun setRouteTarget(platform: String?) {
    activeRoutePlatform = platform
  }


  private fun getTargetDrivers(): List<TestDriver> {
    val route = activeRoutePlatform
    return if (route != null) {
      drivers.filter { it.targetPlatform == route }
    } else {
      drivers
    }
  }

  override suspend fun send(command: Command): Response = coroutineScope {
    val targetDrivers = getTargetDrivers()
    if (targetDrivers.isEmpty()) {
      return@coroutineScope Response.Ok("no-op-target-filtered")
    }

    // For multi-target drag commands, remap coordinates from the primary driver's coordinate space
    // to each target's coordinate space. Desktop JVM uses screen-absolute pixels while
    // Wasm/Playwright uses viewport-relative coordinates — raw broadcast would miss the slider.
    if (activeRoutePlatform == null && targetDrivers.size > 1 && command is Command.Drag) {
      val primaryDriver = targetDrivers.first()

      // Phase 1: Resolve per-target coordinates in parallel
      val resolvedCommands = targetDrivers.map { driver ->
        driver to async(Dispatchers.IO) {
          val primaryTreeRes = runCatching { primaryDriver.send(Command.GetTree(id = "${command.id}_tree_primary")) }.getOrNull()
          val primaryNodes = (primaryTreeRes as? Response.Tree)?.nodes ?: emptyList()

          val primaryNode = primaryNodes
            .filter { n ->
              (n.tag.isNotEmpty() || !n.text.isNullOrEmpty()) &&
                  command.fromX >= n.bounds.left && command.fromX <= n.bounds.right &&
                  command.fromY >= n.bounds.top && command.fromY <= n.bounds.bottom
            }
            .minByOrNull { n -> n.bounds.width * n.bounds.height }

          if (primaryNode != null && primaryNode.bounds.width > 0 && primaryNode.bounds.height > 0) {
            val targetTreeRes = runCatching { driver.send(Command.GetTree(id = "${command.id}_tree_${driver.targetPlatform}")) }.getOrNull()
            val targetNodes = (targetTreeRes as? Response.Tree)?.nodes ?: emptyList()
            val targetNode = targetNodes.firstOrNull { n ->
              (primaryNode.tag.isNotEmpty() && n.tag == primaryNode.tag) ||
                  (!primaryNode.text.isNullOrEmpty() && n.text == primaryNode.text)
            }
            if (targetNode != null) {
              val normFromX = (command.fromX - primaryNode.bounds.left) / primaryNode.bounds.width
              val normToX = (command.toX - primaryNode.bounds.left) / primaryNode.bounds.width
              val normFromY = (command.fromY - primaryNode.bounds.top) / primaryNode.bounds.height
              val normToY = (command.toY - primaryNode.bounds.top) / primaryNode.bounds.height

              command.copy(
                fromX = targetNode.bounds.left + (targetNode.bounds.width * normFromX),
                fromY = targetNode.bounds.top + (targetNode.bounds.height * normFromY),
                toX = targetNode.bounds.left + (targetNode.bounds.width * normToX),
                toY = targetNode.bounds.top + (targetNode.bounds.height * normToY)
              )
            } else command
          } else command
        }
      }.map { (driver, deferred) -> driver to deferred.await() }

      // Phase 2: Execute gestures in parallel on separate IO threads
      val deferreds = resolvedCommands.map { (driver, targetCommand) ->
        driver to async(Dispatchers.IO) {
          runCatching { driver.send(targetCommand) }
        }
      }
      val results = deferreds.map { (driver, deferred) -> driver to deferred.await() }

      val failures = results.filter { (_, res) -> res.isFailure || res.getOrNull() is Response.Error }
      if (failures.isNotEmpty()) {
        val message = failures.joinToString("\n") { (driver, res) ->
          val errorMsg = res.exceptionOrNull()?.message
            ?: (res.getOrNull() as? Response.Error)?.message
            ?: "Unknown error"
          "[${driver.targetPlatform.replaceFirstChar { it.uppercase() }}] $errorMsg"
        }
        throw MultiTargetFailureException(message)
      }

      val successfulResponse = results.firstNotNullOfOrNull { (_, res) ->
        val r = res.getOrNull()
        if (r != null && r !is Response.Error) r else null
      }
      return@coroutineScope successfulResponse ?: Response.Error(id = command.id, message = "All targets failed to execute drag: $command")
    }

    val deferreds = targetDrivers.map { driver ->
      val targetCommand = if (command is Command.Screenshot) {
        val ext = command.hostPath.substringAfterLast('.', "")
        val baseHost = command.hostPath.substringBeforeLast('.')
        val baseDevice = command.devicePath.substringBeforeLast('.')
        val suffix = if (ext.isNotEmpty()) ".$ext" else ""
        command.copy(
          hostPath = "${baseHost}_${driver.targetPlatform}$suffix",
          devicePath = "${baseDevice}_${driver.targetPlatform}$suffix"
        )
      } else {
        command
      }

      driver to async(Dispatchers.IO) {
        runCatching {
          driver.send(targetCommand)
        }
      }
    }

    val results = deferreds.map { (driver, deferred) ->
      driver to deferred.await()
    }

    val failures = results.filter { (_, res) ->
      res.isFailure || res.getOrNull() is Response.Error
    }

    if (failures.isNotEmpty()) {
      val message = failures.joinToString("\n") { (driver, res) ->
        val errorMsg = res.exceptionOrNull()?.message 
          ?: (res.getOrNull() as? Response.Error)?.message 
          ?: "Unknown error"
        "[${driver.targetPlatform.replaceFirstChar { it.uppercase() }}] $errorMsg"
      }
      throw MultiTargetFailureException(message)
    }

    val successfulResponse = results.firstNotNullOfOrNull { (_, res) ->
      val r = res.getOrNull()
      if (r != null && r !is Response.Error) r else null
    }
    successfulResponse ?: Response.Error(id = command.id, message = "All targets failed to execute command: $command")
  }

  override suspend fun relaunchApp() = coroutineScope {
    val targetDrivers = getTargetDrivers()
    targetDrivers.map { driver ->
      async(Dispatchers.IO) {
        driver.relaunchApp()
      }
    }.awaitAll()
    Unit
  }

  override suspend fun reset() = coroutineScope {
    val targetDrivers = getTargetDrivers()
    targetDrivers.map { driver ->
      async(Dispatchers.IO) {
        driver.reset()
      }
    }.awaitAll()
    Unit
  }

  override suspend fun close() = coroutineScope {
    val targetDrivers = getTargetDrivers()
    targetDrivers.map { driver ->
      async(Dispatchers.IO) {
        driver.close()
      }
    }.awaitAll()
    Unit
  }

  override fun updateVirtualCursor(x: Double, y: Double) {
    val targetDrivers = getTargetDrivers()
    targetDrivers.forEach { driver ->
      driver.updateVirtualCursor(x, y)
    }
  }

  override fun resolveArtifactPath(relativePath: String): String {
    return drivers.firstOrNull()?.resolveArtifactPath(relativePath)
      ?: "build/parikshan/${relativePath.trimStart('/', '\\')}"
  }
}
