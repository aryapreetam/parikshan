package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProtocolJson {
  val instance: Json =
    Json {
      ignoreUnknownKeys = true
      classDiscriminator = "type"
      encodeDefaults = true
    }

  fun encodeCommand(command: Command): String =
    instance.encodeToString(command)

  fun decodeCommand(payload: String): Command =
    instance.decodeFromString(payload)

  fun encodeResponse(response: Response): String =
    instance.encodeToString(response)

  fun decodeResponse(payload: String): Response =
    instance.decodeFromString(payload)
}
