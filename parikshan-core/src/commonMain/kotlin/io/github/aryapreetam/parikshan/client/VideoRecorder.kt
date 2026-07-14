package io.github.aryapreetam.parikshan.client

interface VideoRecorder {
  suspend fun start(sessionName: String, outputDirectory: String)
  suspend fun stop(): String? // Returns the absolute path of the generated MP4/webm file
}
