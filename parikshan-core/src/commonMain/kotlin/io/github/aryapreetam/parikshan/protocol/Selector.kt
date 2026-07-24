package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents an intent query used to locate target UI nodes in a Compose Multiplatform view tree.
 *
 * Selectors are passed to test scope methods (such as `click()`, `input()`, `waitFor()`, and `assertVisible()`)
 * to resolve specific UI nodes across platforms.
 *
 * ### Types of Selectors
 * - **[Auto]**: Checks explicit `testTag` first, falling back to visible text matching. Created via [auto].
 * - **[Tag]**: Strictly matches explicit `testTag` properties. Created via [tag].
 * - **[Text]**: Strictly matches visible text substrings. Created via [text].
 *
 * ### Indexing Constraints
 * When multiple UI elements match a query, use indexing functions:
 * - [first]: Picks the first visible match (index 0).
 * - [last]: Picks the last visible match (index -1).
 * - [atIndex]: Picks an explicit 0-indexed position.
 *
 * ### Example Usage
 * ```kotlin
 * // Automatic matching (tag first, then text)
 * click("Submit")
 * click(auto("Submit"))
 *
 * // Exact testTag matching
 * click(tag("login_button"))
 *
 * // Target the 2nd matching element
 * click(text("Delete").atIndex(1))
 * ```
 *
 * @see auto
 * @see tag
 * @see text
 */
@Serializable
sealed interface Selector {
  /** The raw query string or value used for matching. */
  val raw: String

  /** Optional 0-indexed constraint to pick a specific node if multiple match the query. */
  val index: Int?

  /**
   * Selector that attempts to match by explicit Compose `testTag` first,
   * falling back to matching by visible text substring if no matching tag is found.
   */
  @Serializable
  @SerialName("auto")
  data class Auto(
    override val raw: String,
    override val index: Int? = null
  ) : Selector

  /**
   * Selector that strictly matches nodes with the exact `testTag` value specified.
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
   * Selector that strictly matches nodes containing the specified visible text [value].
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

/**
 * Returns a copy of this [Selector] constrained to target the element at the specified 0-indexed position [index].
 *
 * ### Example Usage
 * ```kotlin
 * // Target the 3rd "Cancel" button (index 2)
 * click(text("Cancel").atIndex(2))
 * ```
 *
 * @param index The 0-indexed position of the desired element among all matching nodes.
 * @see first
 * @see last
 */
fun Selector.atIndex(index: Int): Selector = when (this) {
  is Selector.Auto -> copy(index = index)
  is Selector.Tag -> copy(index = index)
  is Selector.Text -> copy(index = index)
}

/**
 * Returns a copy of this [Selector] constrained to target the first matching element (index 0).
 *
 * @see last
 * @see atIndex
 */
fun Selector.first(): Selector = atIndex(0)

/**
 * Returns a copy of this [Selector] constrained to target the last matching element (index -1).
 *
 * @see first
 * @see atIndex
 */
fun Selector.last(): Selector = atIndex(-1)

/**
 * Creates a [Selector.Text] that strictly matches nodes containing the specified visible [value].
 *
 * ### Example Usage
 * ```kotlin
 * click(text("Log In"))
 * ```
 */
fun text(value: String): Selector.Text = Selector.Text(value)

/**
 * Creates a [Selector.Tag] that strictly matches nodes with the exact Compose `testTag` specified by [value].
 *
 * ### Example Usage
 * ```kotlin
 * click(tag("submit_button"))
 * ```
 */
fun tag(value: String): Selector.Tag = Selector.Tag(value)

/**
 * Creates a [Selector.Auto] that attempts to match explicit Compose `testTag` first, falling back to visible text substring.
 *
 * ### Example Usage
 * ```kotlin
 * click(auto("Submit"))
 * ```
 */
fun auto(value: String): Selector.Auto = Selector.Auto(value)