package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.Response
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

      driver to async {
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
      async {
        driver.relaunchApp()
      }
    }.awaitAll()
    Unit
  }

  override suspend fun reset() = coroutineScope {
    val targetDrivers = getTargetDrivers()
    targetDrivers.map { driver ->
      async {
        driver.reset()
      }
    }.awaitAll()
    Unit
  }

  override suspend fun close() = coroutineScope {
    val targetDrivers = getTargetDrivers()
    targetDrivers.map { driver ->
      async {
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
