package io.github.aryapreetam.parikshan.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PluginExtensionsTest {

  @Test
  fun `parses position with x separator`() {
    assertEquals(300 to 300, parsePosition("300x300"))
  }

  @Test
  fun `parses position with comma separator`() {
    assertEquals(300 to 300, parsePosition("300,300"))
  }

  @Test
  fun `parses negative position coordinates`() {
    assertEquals(-20 to -40, parsePosition("-20x-40"))
  }

  @Test
  fun `rejects malformed position`() {
    assertNull(parsePosition("300"))
    assertNull(parsePosition("300 by 300"))
  }
}