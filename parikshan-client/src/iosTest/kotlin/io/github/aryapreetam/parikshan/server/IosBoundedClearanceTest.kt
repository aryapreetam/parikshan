package io.github.aryapreetam.parikshan.server

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosBoundedClearanceTest {

  @Test
  fun clearsNormalPopulatedFieldUntilEmpty() {
    var buffer = "parikshan-type-filter"
    var steps = 0
    var clock = 0L

    val stallReason = IosSemanticsAccessor.clearBoundedUiKeyInput(
      initialLength = buffer.length,
      hasText = { buffer.isNotEmpty() },
      currentLength = { buffer.length },
      deleteBackward = {
        steps++
        if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
      },
      afterDeleteStep = { clock += 20L },
      nowMs = { clock }
    )

    assertNull(stallReason)
    assertEquals("", buffer)
    assertEquals(21, steps)
  }

  @Test
  fun clearsEvenWhenHasTextAlwaysReturnsTrue() {
    var buffer = "abc"
    var clock = 0L

    val stallReason = IosSemanticsAccessor.clearBoundedUiKeyInput(
      initialLength = buffer.length,
      hasText = { true },
      currentLength = { buffer.length },
      deleteBackward = {
        if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
      },
      afterDeleteStep = { clock += 10L },
      nowMs = { clock }
    )

    assertNull(stallReason)
    assertEquals("", buffer)
  }

  @Test
  fun abortsQuicklyWhenDeletionMakesNoProgressAtCursorZero() {
    val buffer = "parikshan-type-filter"
    var steps = 0
    var clock = 0L

    val stallReason = IosSemanticsAccessor.clearBoundedUiKeyInput(
      initialLength = buffer.length,
      hasText = { true },
      currentLength = { buffer.length },
      deleteBackward = { steps++ },
      afterDeleteStep = { clock += 20L },
      nowMs = { clock },
      maxConsecutiveStalls = 5
    )

    assertNotNull(stallReason)
    assertEquals(5, steps)
    assertTrue(stallReason.contains("no progress"))
    assertTrue(stallReason.contains("remaining length=21"))
  }

  @Test
  fun abortsAtWallClockDeadlineWhenLengthUnavailableAndHasTextStuck() {
    var clock = 0L
    var steps = 0

    val stallReason = IosSemanticsAccessor.clearBoundedUiKeyInput(
      initialLength = 10,
      hasText = { true },
      currentLength = { null },
      deleteBackward = { steps++ },
      afterDeleteStep = { clock += 500L },
      nowMs = { clock },
      deadlineMs = 2_000L
    )

    assertNotNull(stallReason)
    assertEquals(4, steps)
    assertTrue(stallReason.contains("deadline"))
  }

  @Test
  fun abortsAtMaxIterationsWhenLengthUnavailableAndClockStatic() {
    var steps = 0

    val stallReason = IosSemanticsAccessor.clearBoundedUiKeyInput(
      initialLength = 5,
      hasText = { true },
      currentLength = { null },
      deleteBackward = { steps++ },
      nowMs = { 0L },
      maxIterations = 10
    )

    assertNotNull(stallReason)
    assertEquals(10, steps)
    assertTrue(stallReason.contains("max iterations"))
  }
}
