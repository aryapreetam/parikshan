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
sealed interface Command {
  val id: String

  @Serializable
  @SerialName("click")
  data class Click(
    override val id: String,
    val tag: String
  ) : Command

  @Serializable
  @SerialName("input")
  data class Input(
    override val id: String,
    val tag: String,
    val text: String
  ) : Command

  @Serializable
  @SerialName("scroll")
  data class Scroll(
    override val id: String,
    val tag: String,
    val direction: ScrollDirection
  ) : Command

  @Serializable
  @SerialName("assertVisible")
  data class AssertVisible(
    override val id: String,
    val tag: String
  ) : Command

  @Serializable
  @SerialName("assertText")
  data class AssertText(
    override val id: String,
    val tag: String,
    val expected: String
  ) : Command

  @Serializable
  @SerialName("waitFor")
  data class WaitFor(
    override val id: String,
    val tag: String,
    val timeoutMs: Long
  ) : Command

  @Serializable
  @SerialName("screenshot")
  data class Screenshot(
    override val id: String,
    val path: String
  ) : Command

  @Serializable
  @SerialName("getTree")
  data class GetTree(
    override val id: String
  ) : Command

  @Serializable
  @SerialName("shutdown")
  data class Shutdown(
    override val id: String
  ) : Command

  @Serializable
  @SerialName("startRecording")
  data class StartRecording(
    override val id: String,
    val sessionName: String,
    val path: String,
    val fps: Int = 1,
    val showCursor: Boolean = true
  ) : Command

  @Serializable
  @SerialName("stopRecording")
  data class StopRecording(
    override val id: String,
    val sessionName: String
  ) : Command

  @Serializable
  @SerialName("pressBack")
  data class PressBack(
    override val id: String
  ) : Command

  @Serializable
  @SerialName("pressHome")
  data class PressHome(
    override val id: String
  ) : Command

  // Used by orchestrators to check liveness before test execution.
  @Serializable
  @SerialName("ping")
  data class Ping(
    override val id: String
  ) : Command
}
