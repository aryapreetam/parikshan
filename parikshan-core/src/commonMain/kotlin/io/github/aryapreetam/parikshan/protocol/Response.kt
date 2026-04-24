package io.github.aryapreetam.parikshan.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Bounds(
  val left: Double,
  val top: Double,
  val right: Double,
  val bottom: Double
) {
  val centerX: Double
    get() = (left + right) / 2.0

  val centerY: Double
    get() = (top + bottom) / 2.0
}

@Serializable
data class NodeSnapshot(
  val tag: String,
  val bounds: Bounds,
  val visible: Boolean,
  val text: String? = null
)

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
  @SerialName("shutdown")
  data class Shutdown(
    override val id: String
  ) : Response

  @Serializable
  @SerialName("nodeinfo")
  data class NodeInfo(
    override val id: String,
    val bounds: Bounds,
    val visible: Boolean,
    val text: String? = null
  ) : Response

  @Serializable
  @SerialName("tree")
  data class Tree(
    override val id: String,
    val nodes: List<NodeSnapshot>
  ) : Response
}
