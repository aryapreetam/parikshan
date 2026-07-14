package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver
import io.github.aryapreetam.parikshan.protocol.Command
import java.io.File
import kotlin.random.Random

class WasmVideoRecorder(
  private val driver: TestDriver
) : VideoRecorder {
  private var activePath: String? = null

  override suspend fun start(sessionName: String, outputDirectory: String) {
    val simpleName = sessionName.substringAfterLast('.').replace('$', '_')
    val file = File(outputDirectory, "$simpleName.webm").absoluteFile
    file.parentFile?.mkdirs()
    activePath = file.absolutePath

    driver.send(
      Command.StartRecording(
        id = "wasm-video-start-${Random.nextLong().toString(16)}",
        sessionName = sessionName,
        path = file.absolutePath
      )
    )
  }

  override suspend fun stop(): String? {
    val path = activePath ?: return null
    activePath = null

    driver.send(
      Command.StopRecording(
        id = "wasm-video-stop-${Random.nextLong().toString(16)}",
        sessionName = ""
      )
    )
    return path
  }
}
