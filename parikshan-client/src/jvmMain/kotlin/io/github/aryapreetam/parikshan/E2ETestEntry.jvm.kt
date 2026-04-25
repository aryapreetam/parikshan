package io.github.aryapreetam.parikshan

import kotlinx.coroutines.runBlocking

actual fun e2eTest(
  config: E2ETestConfig,
  block: suspend E2ETestScope.() -> Unit
) {
  runBlocking {
    val driver =
      when (val target = System.getProperty("parikshan.target")?.lowercase()) {
        "android" -> AndroidRemoteDriver.connect()
        "desktop", null, "" ->
          DesktopDriver(
            host = System.getProperty("parikshan.host") ?: "127.0.0.1",
            port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9877
          )
        "ios" ->
          IosRemoteDriver.connect(
            IosDriverConfig(
              host = System.getProperty("parikshan.host") ?: "127.0.0.1",
              port = System.getProperty("parikshan.port")?.toIntOrNull() ?: 9878
            )
          )
        "wasm", "web" -> WasmDriver.connect()
        else -> error("Unsupported Parikshan target '$target'")
      }

    e2eTest(
      driver = driver,
      config = config,
      block = block
    )
  }
}
