package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ScrollDirection {
  @SerialName("up")
  Up,

  @SerialName("down")
  Down,

  @SerialName("left")
  Left,

  @SerialName("right")
  Right
}

@Serializable
sealed class Command {
  abstract val id: String
  abstract var token: String

  @Serializable
  @SerialName("click")
  data class Click(
    override val id: String,
    val tag: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("input")
  data class Input(
    override val id: String,
    val tag: String,
    val text: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("scroll")
  data class Scroll(
    override val id: String,
    val tag: String,
    val direction: ScrollDirection,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("assertvisible")
  data class AssertVisible(
    override val id: String,
    val tag: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("asserttext")
  data class AssertText(
    override val id: String,
    val tag: String,
    val expected: String,
    override var token: String = ""
  ) : Command()

  @Serializable
  @SerialName("waitfor")
  data class WaitFor(
    override val id: String,
    val tag: String,
    val timeoutMs: Long,
    override var token: String = ""
  ) : Command()

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
  @SerialName("ping")
  data class Ping(
    override val id: String,
    override var token: String = ""
  ) : Command()
}
