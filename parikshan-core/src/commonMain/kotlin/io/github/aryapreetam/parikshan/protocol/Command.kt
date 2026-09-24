package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Specifies the directional orientation for UI scroll gestures (e.g. scrolling lists or lazy layouts).
 *
 * ### Example Usage
 * ```kotlin
 * // Scroll down a container to reveal lower elements
 * scroll("feed_container", ScrollDirection.Down)
 *
 * // Scroll right in a horizontal row
 * scroll("carousel_container", ScrollDirection.Right)
 * ```
 */
@Serializable
enum class ScrollDirection {
  /** Scrolls towards the top of the container. */
  @SerialName("up")
  Up,

  /** Scrolls towards the bottom of the container. */
  @SerialName("down")
  Down,

  /** Scrolls towards the left edge of the container. */
  @SerialName("left")
  Left,

  /** Scrolls towards the right edge of the container. */
  @SerialName("right")
  Right
}

/**
 * @suppress
 */
@Serializable
sealed class Command {
  abstract val id: String
  abstract var token: String

  /** Implemented by commands that target a UI element. */
  interface HasSelector {
    val selector: Selector?
    val tag: String
  }

  @Serializable
  @SerialName("click")
  data class Click(
    override val id: String,
    override val tag: String,
    override val selector: Selector? = null,
    override var token: String = ""
  ) : Command(), HasSelector

  @Serializable
  @SerialName("input")
  data class Input(
    override val id: String,
    override val tag: String,
    val text: String,
    override val selector: Selector? = null,
    override var token: String = ""
  ) : Command(), HasSelector

  @Serializable
  @SerialName("scroll")
  data class Scroll(
    override val id: String,
    override val tag: String,
    val direction: ScrollDirection,
    override val selector: Selector? = null,
    override var token: String = ""
  ) : Command(), HasSelector

  @Serializable
  @SerialName("assert_visible")
  data class AssertVisible(
    override val id: String,
    override val tag: String,
    override val selector: Selector? = null,
    override var token: String = ""
  ) : Command(), HasSelector

  @Serializable
  @SerialName("assert_text")
  data class AssertText(
    override val id: String,
    override val tag: String,
    val expected: String,
    override val selector: Selector? = null,
    override var token: String = ""
  ) : Command(), HasSelector

  @Serializable
  @SerialName("waitfor")
  data class WaitFor(
    override val id: String,
    override val tag: String,
    val timeoutMs: Long = 10000L,
    override val selector: Selector? = null,
    override var token: String = ""
  ) : Command(), HasSelector

  @Serializable
  @SerialName("screenshot")
  data class Screenshot(
    override val id: String,
    val devicePath: String,
    val hostPath: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("gettree")
  data class GetTree(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("shutdown")
  data class Shutdown(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("startrecording")
  data class StartRecording(
    override val id: String,
    val sessionName: String,
    val path: String,
    val fps: Int = 1,
    val showCursor: Boolean = true,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("stoprecording")
  data class StopRecording(
    override val id: String,
    val sessionName: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("pressback")
  data class PressBack(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("presshome")
  data class PressHome(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("relaunchapp")
  data class RelaunchApp(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("ping")
  data class Ping(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("reset")
  data class Reset(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("hidekeyboard")
  data class HideKeyboard(
    override val id: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("drag")
  data class Drag(
    override val id: String,
    val fromX: Double,
    val fromY: Double,
    val toX: Double,
    val toY: Double,
    val durationMs: Long = 300L,
    override var token: String = ""
  ) : Command()

}

/**
 * Resolves the effective [Selector] from a [Command].
 * If the command implements [Command.HasSelector], returns the explicit selector
 * or falls back to [Selector.Auto] wrapping [Command.HasSelector.tag].
 * Returns `null` for commands that don't target a UI element (e.g., Ping, GetTree).
 */
fun Command.resolvedSelector(): Selector? =
  (this as? Command.HasSelector)?.let { it.selector ?: Selector.Auto(it.tag) }
