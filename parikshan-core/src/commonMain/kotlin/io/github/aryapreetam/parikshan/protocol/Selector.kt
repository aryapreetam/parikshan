package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Selector {
  val raw: String

  @Serializable
  @SerialName("auto")
  data class Auto(
    override val raw: String
  ) : Selector

  @Serializable
  @SerialName("tag")
  data class Tag(
    val value: String
  ) : Selector {
    override val raw: String = value
  }

  @Serializable
  @SerialName("text")
  data class Text(
    val value: String
  ) : Selector {
    override val raw: String = value
  }
}