package org.parikshan.issueplayground.issue10

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.e2eTest
import kotlin.test.Test

class Issue10Test : E2ETestLifecycle {

  @Test
  fun testInputOnHostLabel() = e2eTest {
    // Reproducer for Issue #10: Target input field by visible label text ("Host")
    assertVisible("Issue #10")
    click("Issue #10")

    assertVisible("Issue #10 Reproducer")
    assertVisible("Host")

    input("Host", "192.168.1.1")
  }
}
