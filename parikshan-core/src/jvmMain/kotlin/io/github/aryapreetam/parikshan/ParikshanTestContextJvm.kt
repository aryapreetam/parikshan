package io.github.aryapreetam.parikshan

import java.io.File

/**
 * Convenience factory function creating a [ParikshanTestContext] with a Java [File] for [dataDir].
 */
fun ParikshanTestContext(
  instanceId: String,
  host: String = "127.0.0.1",
  port: Int,
  sessionToken: String,
  dataDir: File?,
  windowPosition: Pair<Int, Int>? = null
): ParikshanTestContext = ParikshanTestContext(
  instanceId = instanceId,
  host = host,
  port = port,
  sessionToken = sessionToken,
  dataDir = dataDir?.absolutePath,
  windowPosition = windowPosition
)

/**
 * Returns [ParikshanTestContext.dataDir] resolved as a Java [File], or null if [dataDir] was not specified.
 */
val ParikshanTestContext.dataDirFile: File?
  get() = dataDir?.let { File(it) }
