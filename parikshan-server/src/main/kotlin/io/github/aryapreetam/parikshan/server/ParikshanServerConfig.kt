package io.github.aryapreetam.parikshan.server

data class ParikshanServerConfig(
  val host: String = "127.0.0.1",
  val port: Int = 9877,
  val path: String = "/parikshan",
  val waitPollIntervalMs: Long = 120L
) {
  companion object {
    fun fromSystemProperties(defaults: ParikshanServerConfig = ParikshanServerConfig()): ParikshanServerConfig {
      val host =
        System.getProperty("parikshan.host")
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?: defaults.host
      val port = System.getProperty("parikshan.port")?.toIntOrNull() ?: defaults.port
      val path =
        System.getProperty("parikshan.path")
          ?.trim()
          ?.takeIf { it.isNotEmpty() }
          ?: defaults.path

      return defaults.copy(
        host = host,
        port = port,
        path = path
      )
    }
  }
}

interface ParikshanServerHandle {
  fun stop()
}
