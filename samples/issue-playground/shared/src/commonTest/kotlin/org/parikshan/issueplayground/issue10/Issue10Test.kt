package org.parikshan.issueplayground.issue10

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.tag
import kotlin.test.Test

class Issue10Test : E2ETestLifecycle {

  override suspend fun E2ETestScope.afterEach() {
    runCatching {
      click(tag("back_to_issue_index"))
    }
  }

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
