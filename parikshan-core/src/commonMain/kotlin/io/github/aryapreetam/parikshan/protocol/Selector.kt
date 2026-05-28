package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Selector {
  val raw: String
  val index: Int?

  @Serializable
  @SerialName("auto")
  data class Auto(
    override val raw: String,
    override val index: Int? = null
  ) : Selector

  @Serializable
  @SerialName("tag")
  data class Tag(
    val value: String,
    override val index: Int? = null
  ) : Selector {
    override val raw: String = value
  }

  @Serializable
  @SerialName("text")
  data class Text(
    val value: String,
    override val index: Int? = null
  ) : Selector {
    override val raw: String = value
  }
}

fun Selector.atIndex(index: Int): Selector = when (this) {
  is Selector.Auto -> copy(index = index)
  is Selector.Tag -> copy(index = index)
  is Selector.Text -> copy(index = index)
}

fun Selector.first(): Selector = atIndex(0)
fun Selector.last(): Selector = atIndex(-1)

fun text(value: String): Selector.Text = Selector.Text(value)
fun tag(value: String): Selector.Tag = Selector.Tag(value)
fun auto(value: String): Selector.Auto = Selector.Auto(value)