package io.github.aryapreetam.parikshan.client

import io.github.aryapreetam.parikshan.TestDriver
import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.Response
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class VideoRecorderFactoryTest {

  private val mockDriver = object : TestDriver {
    override val targetPlatform: String = "desktop"
    override suspend fun send(command: Command): Response = Response.Ok(command.id)
    override suspend fun close() {}
  }

  private val config = ParikshanVideoConfig(
    enabled = true,
    outputDir = "build/parikshan/videos",
    fps = 10,
    showCursor = true,
    postRollMs = 1000L
  )

  @Test
  fun testCreateDesktopRecorder() {
    val recorder = VideoRecorderFactory.create("desktop", mockDriver, config)
    assertNotNull(recorder, "Desktop recorder must be created")
    assertTrue(recorder is DesktopVideoRecorder, "Expected DesktopVideoRecorder instance")
  }

  @Test
  fun testCreateWasmRecorder() {
    val recorder = VideoRecorderFactory.create("wasm", mockDriver, config)
    assertNotNull(recorder, "Wasm recorder must be created")
    assertTrue(recorder is WasmVideoRecorder, "Expected WasmVideoRecorder instance")
  }

  @Test
  fun testCreateAndroidRecorder() {
    val recorder = VideoRecorderFactory.create("android", mockDriver, config)
    assertNotNull(recorder, "Android recorder must be created")
    assertTrue(recorder is AndroidVideoRecorder, "Expected AndroidVideoRecorder instance")
  }

  @Test
  fun testCreateIosRecorder() {
    val recorder = VideoRecorderFactory.create("ios", mockDriver, config)
    assertNotNull(recorder, "iOS recorder must be created")
    assertTrue(recorder is IosVideoRecorder, "Expected IosVideoRecorder instance")
  }

  @Test
  fun testCreateUnknownTargetThrowsIllegalArgumentException() {
    assertFailsWith<IllegalArgumentException> {
      VideoRecorderFactory.create("unknown_target", mockDriver, config)
    }
  }
}
