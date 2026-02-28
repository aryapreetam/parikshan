package io.github.aryapreetam.parikshan.server

data class ParikshanServerConfig(
  val host: String = "127.0.0.1",
  val port: Int = 9877,
  val path: String = "/parikshan",
  val waitPollIntervalMs: Long = 120L
)

interface ParikshanServerHandle {
  fun stop()
}
