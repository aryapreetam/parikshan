package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
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
  fun auto_reports_ambiguous_visible_text_matches() {
    val error =
      assertFailsWith<IllegalArgumentException> {
        Selector.Auto("Submit").resolveNode(
          nodes =
            listOf(
              node(tag = "submit_primary", text = "Submit"),
              node(tag = "submit_secondary", text = "Submit")
            )
        )
      }

    assertContains(error.message.orEmpty(), "multiple visible text nodes")
    assertContains(error.message.orEmpty(), "submit_primary")
    assertContains(error.message.orEmpty(), "submit_secondary")
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

  private fun node(
    tag: String,
    text: String?,
    visible: Boolean = true
  ): NodeSnapshot =
    NodeSnapshot(
      tag = tag,
      bounds = Bounds(left = 0.0, top = 0.0, right = 100.0, bottom = 40.0),
      visible = visible,
      text = text
    )
}