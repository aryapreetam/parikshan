package org.parikshan.issueplayground.issue33

import io.github.aryapreetam.parikshan.E2ETestLifecycle
import io.github.aryapreetam.parikshan.E2ETestScope
import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isWasm
import io.github.aryapreetam.parikshan.protocol.Selector
import io.github.aryapreetam.parikshan.protocol.first
import io.github.aryapreetam.parikshan.protocol.tag
import kotlin.test.Test
import kotlin.test.assertTrue

class Issue33Test : E2ETestLifecycle {

    override suspend fun E2ETestScope.afterEach() {
        runCatching {
            click(tag("back_to_issue_index"))
            assertVisible("Issue Playground Index")
        }
    }

    @Test
    fun testClearPopulatedUntaggedSearchFieldAfterFilterInteractions() = e2eTest {
        scrollUntilVisible("issue_index_list", "Issue #33")
        assertVisible("Issue #33")
        click("Issue #33")

        assertVisible("Issue #33 Reproducer")

        val query = "parikshan-type-filter"
        input(Selector.Auto("Search by device name").first(), query)
        hideKeyboard()

        if (!isWasm()) {
            for (type in listOf("Source", "Destination", "Internal", "All")) {
                click(Selector.Auto("Filter by device type"))
                click(Selector.Text(type))
                assertNotVisible(Selector.Text("Source"))
                assertValue(Selector.Text(query).first(), query)
            }

            // Mirrors cleanup sequence from Issue #33: reset filter, then clear populated field
            if (!hasVisibleNode(Selector.Text("All"))) {
                click(Selector.Auto("Filter by device type"))
            }
            click(Selector.Text("All"))
            assertNotVisible(Selector.Text("All"))
        } else {
            // Compose Web retains closed Material3 DropdownMenu items in #cmp_a11y_root (tracked separately).
            // Exercise focus loss via an interactive control before clearing the populated untagged field.
            click(tag("post_clear_action"))
            assertVisible("Post-Clear Count: 1")
            assertValue(Selector.Text(query).first(), query)
        }

        val populatedField = Selector.Text(query).first()
        assertTrue(hasVisibleNode(populatedField), "Expected populated search field to be visible before clearing")
        input(populatedField, "")

        assertVisible("Query Empty: true")
        assertVisible("Query Length: 0")
        assertVisible(Selector.Text("Search by device name"))

        // Confirm driver and app main thread remain responsive after clearing
        click(tag("post_clear_action"))
        assertVisible(if (isWasm()) "Post-Clear Count: 2" else "Post-Clear Count: 1")
    }

    @Test
    fun testReplaceAndClearAcrossCursorPositions() = e2eTest {
        scrollUntilVisible("issue_index_list", "Issue #33")
        assertVisible("Issue #33")
        click("Issue #33")

        assertVisible("Issue #33 Reproducer")

        // Populate untagged cursor field
        input(Selector.Auto("Cursor field placeholder").first(), "cursor-start-val")
        hideKeyboard()
        assertValue(Selector.Text("cursor-start-val").first(), "cursor-start-val")

        // Cursor at start -> replace
        click(tag("cursor_start"))
        assertVisible("Cursor Offset: 0")
        input(Selector.Text("cursor-start-val").first(), "cursor-mid-val")
        hideKeyboard()
        assertValue(Selector.Text("cursor-mid-val").first(), "cursor-mid-val")

        // Cursor at middle -> replace
        click(tag("cursor_middle"))
        input(Selector.Text("cursor-mid-val").first(), "cursor-end-val")
        hideKeyboard()
        assertValue(Selector.Text("cursor-end-val").first(), "cursor-end-val")

        // Cursor at start -> clear
        click(tag("cursor_start"))
        assertVisible("Cursor Offset: 0")
        input(Selector.Text("cursor-end-val").first(), "")
        assertVisible("Cursor Empty: true")
        assertVisible("Cursor Length: 0")

        // Repopulate -> cursor at middle -> clear
        input(Selector.Auto("Cursor field placeholder").first(), "clear-from-mid")
        hideKeyboard()
        click(tag("cursor_middle"))
        input(Selector.Text("clear-from-mid").first(), "")
        assertVisible("Cursor Empty: true")
        assertVisible("Cursor Length: 0")

        // Repopulate -> cursor at end -> clear
        input(Selector.Auto("Cursor field placeholder").first(), "clear-from-end")
        hideKeyboard()
        click(tag("cursor_end"))
        input(Selector.Text("clear-from-end").first(), "")
        assertVisible("Cursor Empty: true")
        assertVisible("Cursor Length: 0")

        // Confirm subsequent command succeeds cleanly
        click(tag("post_clear_action"))
        assertVisible("Post-Clear Count: 1")
    }
}
