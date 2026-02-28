package io.github.aryapreetam.parikshan.server

import java.awt.Robot
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

internal class DesktopEventInjector {
  private val robot: Robot = Robot()

  fun screenshot(
    image: BufferedImage,
    path: String
  ) {
    val output = File(path)
    output.parentFile?.mkdirs()
    ImageIO.write(image, "png", output)
  }

  fun createScreenCapture(bounds: java.awt.Rectangle): BufferedImage =
    robot.createScreenCapture(bounds)
}
