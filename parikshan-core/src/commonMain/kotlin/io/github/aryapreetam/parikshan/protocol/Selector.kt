package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents a query used to locate a specific node in the Compose Multiplatform UI tree.
 */
@Serializable
sealed interface Selector {
  /** The raw query string. */
  val raw: String
  /** Optional index to pick a specific node if multiple match the query. */
  val index: Int?

  /**
   * Smart selector that attempts to match by [Modifier.testTag] first, 
   * and falls back to matching by visible text substrings.
   */
  @Serializable
  @SerialName("auto")
  data class Auto(
    override val raw: String,
    override val index: Int? = null
  ) : Selector

  /**
   * Strict selector that only matches nodes with the exact [Modifier.testTag] specified.
   */
  @Serializable
  @SerialName("tag")
  data class Tag(
    val value: String,
    override val index: Int? = null
  ) : Selector {
    override val raw: String = value
  }

  /**
   * Selector that matches nodes containing the specified visible [value].
   */
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