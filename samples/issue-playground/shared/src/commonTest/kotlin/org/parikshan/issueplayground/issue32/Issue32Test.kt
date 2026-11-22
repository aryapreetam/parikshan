package org.parikshan.issueplayground.issue32

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isWasm
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.tag
import kotlin.test.Test

class Issue32Test : E2ETestLifecycle {

    override suspend fun E2ETestScope.afterEach() {
        runCatching {
            click(tag("back_to_issue_index"))
            assertVisible("Issue Playground Index")
        }
    }

    @Test
    fun testDirectoryPickerDestinationFileCreationAndWrite() = e2eTest {
        scrollUntilVisible("issue_index_list", "Issue #32")
        assertVisible("Issue #32")
        click("Issue #32")

        assertVisible("Issue #32 Reproducer")

        // Select source directory and confirm ready.txt (25 UTF-8 bytes) is readable
        withFilePicker("build/parikshan-fixtures/issue32/picker-write-source") {
            click(Selector.Tag("choose_source"))
        }
        assertVisible("Source Ready: true (25 bytes)")
        assertVisible("Source Text: Android matrix readiness")

        // Web webkitdirectory is read-only by browser standard; verify native write targets directly
        if (!isWasm()) {
            withFilePicker("build/parikshan-fixtures/issue32/picker-write-destination") {
                click(Selector.Tag("choose_destination"))
            }
            assertVisible("Destination Initial Count: 0")

            // Execute FileKit copy: PlatformFile(dest, "ready.txt").write(PlatformFile(src, "ready.txt"))
            click(Selector.Tag("sync_action"))

            assertVisible("Sync Status: Sync succeeded")
            assertVisible("Destination Bytes: 25")
            assertVisible("Bytes Match: true")
            assertVisible("Source Unchanged: true")
            assertVisible("Destination Files: ready.txt")
            assertVisible("Destination Text: Android matrix readiness")

            // Verify overwrite truncation ("wt"), subdirectory creation, and deletion
            click(Selector.Tag("saf_extended_action"))
            assertVisible("Extended Status: Extended succeeded")
            assertVisible("Overwrite Truncated: true")
            assertVisible("Subdir Created: true")
            assertVisible("Deleted Cleanly: true")
        }
    }
}
