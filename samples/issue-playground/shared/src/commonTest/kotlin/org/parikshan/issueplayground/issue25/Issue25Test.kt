package org.parikshan.issueplayground.issue25

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.tag
import kotlin.test.Test
import kotlin.test.assertFailsWith

class Issue25Test : E2ETestLifecycle {

  override suspend fun E2ETestScope.afterEach() {
    runCatching {
      click(tag("back_to_issue_index"))
    }
  }

  @Test
  fun testClickDisabledControlFailsAndDoesNotInvokeCallback() = e2eTest {
    assertVisible("Issue #25")
    click("Issue #25")

    assertVisible("Issue #25 Reproducer")
    assertVisible("Disabled action")

    // In buggy code: performClick invokes the semantic callback despite enabled = false,
    // catches the exception, falls back to native AWT Robot click, and returns Ok (true).
    // Expected behavior: Click on disabled control must fail and never invoke callback.
    assertFailsWith<AssertionError> {
      click(tag("disabled_action"))
    }

    assertVisible("Callback Invoked: false")
  }
}
