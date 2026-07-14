package io.github.aryapreetam.parikshan.server

data class TestServerConfig(
  val host: String = "127.0.0.1",
  val port: Int = 9877,
  val path: String = "/",
  val waitPollIntervalMs: Long = 120L
) {
  companion object {
    fun fromSystemProperties(defaults: TestServerConfig = TestServerConfig()): TestServerConfig {
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

interface TestServerHandle {
  fun stop()
}
