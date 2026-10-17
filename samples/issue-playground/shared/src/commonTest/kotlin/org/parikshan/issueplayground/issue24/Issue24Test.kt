package org.parikshan.issueplayground.issue24

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.first
import io.github.aryapreetam.parikshan.protocol.tag
import kotlin.test.Test

class Issue24Test : E2ETestLifecycle {

  override suspend fun E2ETestScope.afterEach() {
    runCatching {
      click(tag("back_to_issue_index"))
    }
  }

  @Test
  fun testReselectFilledFieldByText() = e2eTest {
    assertVisible("Issue #24")
    click("Issue #24")

    assertVisible("Issue #24 Reproducer")

    // Step 1: Input text into the field using its accessibility label / contentDescription "Search"
    input(Selector.Auto("Search").first(), "sample-query")
    hideKeyboard()

    // Step 2: Target the field using the populated editable text.
    // In buggy code: directTextOf prefers contentDescription ("Search") over editableText ("sample-query"),
    // causing Selector.Text("sample-query") to fail with "No node found for selector 'sample-query'".
    input(Selector.Text("sample-query").first(), "updated-query")

    assertVisible("Current Value: updated-query")
  }
}
