package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.Selector
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SelectorResolutionTest {
  @Test
  fun auto_prefers_exact_tag_over_text() {
    val resolved =
      Selector.Auto("Submit").resolveNode(
        nodes =
          listOf(
            node(tag = "Submit", text = "Tag Target"),
            node(tag = "submit_button", text = "Submit")
          )
      )

    assertEquals(ResolvedSelector.MatchType.Tag, resolved.matchType)
    assertEquals("Submit", resolved.tag)
  }

  @Test
  fun auto_falls_back_to_unique_visible_text() {
    val resolved =
      Selector.Auto("Submit").resolveNode(
        nodes = listOf(node(tag = "submit_button", text = "Submit"))
      )

    assertEquals(ResolvedSelector.MatchType.Text, resolved.matchType)
    assertEquals("submit_button", resolved.tag)
  }

  @Test
  fun auto_ignores_hidden_text_matches() {
    val error =
      assertFailsWith<IllegalArgumentException> {
        Selector.Auto("Submit").resolveNode(
          nodes = listOf(node(tag = "submit_button", text = "Submit", visible = false))
        )
      }

    assertContains(error.message.orEmpty(), "checked exact tag first")
  }

  @Test
  fun auto_picks_first_of_multiple_visible_text_matches() {
    val resolved =
      Selector.Auto("Submit").resolveNode(
        nodes =
          listOf(
            node(tag = "submit_primary", text = "Submit", bounds = Bounds(0.0, 0.0, 100.0, 40.0)),
            node(tag = "submit_secondary", text = "Submit", bounds = Bounds(0.0, 50.0, 100.0, 90.0))
          )
      )

    assertEquals("submit_primary", resolved.tag)
    assertEquals(2, resolved.allMatches.size)
  }

  @Test
  fun text_selector_uses_trimmed_exact_matching() {
    val resolved =
      Selector.Text("Submit").resolveNode(
        nodes = listOf(node(tag = "submit_button", text = "  Submit  "))
      )

    assertEquals(ResolvedSelector.MatchType.Text, resolved.matchType)
    assertEquals("submit_button", resolved.tag)
  }

  @Test
  fun text_selector_prefers_exact_over_startsWith() {
    val resolved =
      Selector.Text("Login").resolveNode(
        nodes =
          listOf(
            node(tag = "login_button", text = "Login"),
            node(tag = "login_label", text = "Login now")
          )
      )

    assertEquals("login_button", resolved.tag)
  }

  @Test
  fun text_selector_prefers_startsWith_over_contains() {
    val resolved =
      Selector.Text("Login").resolveNode(
        nodes =
          listOf(
            node(tag = "login_button", text = "Login now"),
            node(tag = "other_label", text = "Please Login")
          )
      )

    assertEquals("login_button", resolved.tag)
  }

  @Test
  fun text_selector_prefers_smaller_node_area() {
    val resolved =
      Selector.Text("Login").resolveNode(
        nodes =
          listOf(
            node(tag = "parent_card", text = "Login", bounds = Bounds(0.0, 0.0, 200.0, 100.0)),
            node(tag = "child_text", text = "Login", bounds = Bounds(50.0, 20.0, 150.0, 60.0))
          )
      )

    assertEquals("child_text", resolved.tag)
  }

  @Test
  fun nested_icon_button_and_icon_collapse_into_single_match() {
    val resolved =
      Selector.Auto("Switch to text input mode").resolveNode(
        nodes =
          listOf(
            node(tag = "", text = "Switch to text input mode", bounds = Bounds(0.0, 0.0, 48.0, 48.0)),
            node(tag = "", text = "Switch to text input mode", bounds = Bounds(12.0, 12.0, 36.0, 36.0))
          )
      )

    assertEquals(1, resolved.allMatches.size)
  }

  @Test
  fun nested_tagged_container_and_untagged_child_prefers_tagged_container() {
    val resolved =
      Selector.Auto("Next month").resolveNode(
        nodes =
          listOf(
            node(tag = "next_month_button", text = "Next month", bounds = Bounds(0.0, 0.0, 48.0, 48.0)),
            node(tag = "", text = "Next month", bounds = Bounds(12.0, 12.0, 36.0, 36.0))
          )
      )

    assertEquals("next_month_button", resolved.tag)
    assertEquals(1, resolved.allMatches.size)
  }

  @Test
  fun distinct_sibling_nodes_with_same_text_remain_ambiguous() {
    val resolved =
      Selector.Auto("Delete").resolveNode(
        nodes =
          listOf(
            node(tag = "delete_item_1", text = "Delete", bounds = Bounds(0.0, 0.0, 100.0, 40.0)),
            node(tag = "delete_item_2", text = "Delete", bounds = Bounds(0.0, 100.0, 100.0, 140.0))
          )
      )

    assertEquals(2, resolved.allMatches.size)
  }

  @Test
  fun untagged_container_preferred_over_untagged_leaf() {
    // Simulates CMP 1.11+ DatePicker: outer IconButton (clickable) wraps inner Icon (decorative).
    // Both share the same accessibility text and neither has a testTag.
    val resolved =
      Selector.Auto("previous month").resolveNode(
        nodes =
          listOf(
            node(tag = "", text = "previous month", bounds = Bounds(819.0, 873.0, 963.0, 1017.0)),
            node(tag = "", text = "previous month", bounds = Bounds(855.0, 909.0, 927.0, 981.0))
          )
      )

    assertEquals(1, resolved.allMatches.size)
    // The container (144x144) is kept, not the leaf (72x72)
    assertEquals(144.0 * 144.0, resolved.node.area, 0.1)
  }

  private fun node(
    tag: String,
    text: String?,
    visible: Boolean = true,
    bounds: Bounds = Bounds(left = 0.0, top = 0.0, right = 100.0, bottom = 40.0)
  ): NodeSnapshot =
    NodeSnapshot(
      tag = tag,
      bounds = bounds,
      visible = visible,
      text = text
    )
}