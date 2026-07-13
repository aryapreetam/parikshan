package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Response {
  val id: String

  @Serializable
  @SerialName("ok")
  data class Ok(
    override val id: String
  ) : Response

  @Serializable
  @SerialName("error")
  data class Error(
    override val id: String,
    val message: String
  ) : Response

  @Serializable
  @SerialName("tree")
  data class Tree(
    override val id: String,
    val nodes: List<NodeSnapshot>
  ) : Response

  @Serializable
  @SerialName("node_info")
  data class NodeInfo(
    override val id: String,
    val bounds: Bounds,
    val visible: Boolean,
    val text: String? = null
  ) : Response

}

@Serializable
data class Bounds(
  val left: Double,
  val top: Double,
  val right: Double,
  val bottom: Double
) {
  val width: Double get() = right - left
  val height: Double get() = bottom - top
  val centerX: Double get() = left + width / 2.0
  val centerY: Double get() = top + height / 2.0
}

@Serializable
data class NodeSnapshot(
  val tag: String,
  val text: String? = null,
  val visible: Boolean = true,
  val bounds: Bounds,
  val zOrder: Int = 0
) {
  val width: Double get() = bounds.width
  val height: Double get() = bounds.height
  val area: Double get() = width * height
}
