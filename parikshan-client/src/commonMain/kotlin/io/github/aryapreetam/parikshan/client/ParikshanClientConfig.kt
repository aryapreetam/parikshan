package io.github.aryapreetam.parikshan.client

data class ParikshanClientConfig(
  val host: String = "127.0.0.1",
  val port: Int = 9877,
  val path: String = "/parikshan",
  val connectRetries: Int = 30,
  val connectRetryDelayMs: Long = 1_000L,
  val requestTimeoutMs: Long = 20_000L
)
