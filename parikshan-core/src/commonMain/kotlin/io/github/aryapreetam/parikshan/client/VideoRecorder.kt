package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver

interface VideoRecorder {
  suspend fun start(sessionName: String, outputDirectory: String)
  suspend fun stop(): String? // Returns the absolute path of the generated MP4/webm file
  fun pause() {}
  fun resume() {}
  fun updateDriver(newDriver: TestDriver) {}
}

