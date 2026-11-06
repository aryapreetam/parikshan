package org.parikshan.issueplayground.issue29

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isWasm
import io.github.aryapreetam.parikshan.protocol.tag
import kotlin.test.Test

class Issue29Test : E2ETestLifecycle {

  override suspend fun E2ETestScope.afterEach() {
    runCatching {
      click(tag("back_to_issue_index"))
    }
  }

  @Test
  fun testDirectoryPickerTreeMetadataAndRecursiveStaging() = e2eTest {
    assertVisible("Issue #29")
    click("Issue #29")

    assertVisible("Issue #29 Reproducer")
    assertVisible("Choose Directory")

    withFilePicker("fixtures/issue29_dir") {
      click(tag("choose_directory_button"))
    }

    assertVisible("Is Directory: true")
    if (isWasm()) {
      // Browser webkitdirectory only reports File objects; empty directories are omitted by design in web standards.
      assertVisible("Child Count: 2")
      assertVisible("Files: nested,report.txt")
    } else {
      assertVisible("Child Count: 3")
      assertVisible("Files: empty_dir,nested,report.txt")
      assertVisible("Empty Dir Children: 0")
    }
    assertVisible("Nested Files: notes.txt")
    assertVisible("Report Content: Synthetic Android picker verification")
    assertVisible("Nested Notes Content: Synthetic nested content")
  }
}
