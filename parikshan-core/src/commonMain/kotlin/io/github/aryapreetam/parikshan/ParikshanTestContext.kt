package io.github.aryapreetam.parikshan

import kotlinx.serialization.Serializable

/**
 * Immutable context model describing an isolated Parikshan test instance or target session.
 *
 * @property instanceId Unique identifier for the instance (e.g. "desktop", "wasm", "alice", "bob").
 * @property host Host IP or hostname where the application server or CDP endpoint is bound.
 * @property port Network port assigned to this instance.
 * @property sessionToken Secret authorization token required for authenticating commands.
 * @property dataDir Path to the isolated application data directory, or null for default.
 * @property windowPosition Optional (x, y) coordinates for desktop window placement.
 */
@InternalParikshanApi
@Serializable
data class ParikshanTestContext(
  val instanceId: String,
  val host: String = "127.0.0.1",
  val port: Int,
  val sessionToken: String,
  val dataDir: String? = null,
  val windowPosition: Pair<Int, Int>? = null
)
