@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER")
@file:OptIn(
  androidx.compose.ui.InternalComposeUiApi::class,
  androidx.compose.ui.ExperimentalComposeUiApi::class,
)
package io.github.aryapreetam.parikshan.server

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.PlatformRootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import io.github.aryapreetam.parikshan.protocol.Bounds
import io.github.aryapreetam.parikshan.protocol.NodeSnapshot
import io.github.aryapreetam.parikshan.protocol.ProtocolJson
import io.github.aryapreetam.parikshan.protocol.ScrollDirection
import kotlin.js.JsName
import kotlinx.serialization.builtins.ListSerializer

internal object WasmSemanticsAccessor {
  private val activeOwners = mutableSetOf<SemanticsOwner>()
  private val registeredOwners = mutableSetOf<Owner>()

  internal fun injectOwner(owner: Owner) {
    activeOwners.add(owner.semanticsOwner)
    registeredOwners.add(owner)
  }

  internal fun removeOwner(owner: Owner) {
    activeOwners.remove(owner.semanticsOwner)
    registeredOwners.remove(owner)
  }

  fun findAllNodes(): List<SemanticsNode> {
    val currentOwners = activeOwners.toSet()
    val allNodes = mutableListOf<SemanticsNode>()
    for (owner in currentOwners) {
      try {
        allNodes.addAll(owner.getAllSemanticsNodes(mergingEnabled = true))
        allNodes.addAll(owner.getAllSemanticsNodes(mergingEnabled = false))
      } catch (_: Throwable) {}
    }
    return allNodes.distinctBy { it.id }
  }

  private fun directTextOf(node: SemanticsNode): String? {
    node.config.getOrNull(SemanticsProperties.EditableText)?.text
      ?.takeIf { it.isNotBlank() }
      ?.let { return it }

    val values = node.config.getOrNull(SemanticsProperties.Text).orEmpty()
    val spokenText = if (values.isNotEmpty()) {
      values.joinToString("") { it.text }.takeIf { it.isNotBlank() }.orEmpty()
    } else ""

    val contentDescriptionList = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
    val contentDescription = if (contentDescriptionList.isNotEmpty()) {
      contentDescriptionList.joinToString("").takeIf { it.isNotBlank() }.orEmpty()
    } else ""
    return when {
      contentDescription.isNotBlank() && spokenText.isNotBlank() -> "$contentDescription $spokenText"
      contentDescription.isNotBlank() -> contentDescription
      spokenText.isNotBlank() -> spokenText
      else -> null
    }
  }

  private fun collectDescendantTexts(node: SemanticsNode, result: MutableList<String>) {
    for (child in node.children) {
      val t = directTextOf(child)
      if (!t.isNullOrBlank()) {
        result.add(t)
      }
      collectDescendantTexts(child, result)
    }
  }

  private fun matchesTag(node: SemanticsNode, tag: String): Boolean {
    if (node.config.getOrNull(SemanticsProperties.TestTag) == tag) return true
    try {
      for (entry in node.config) {
        if (entry.value == tag) return true
      }
    } catch (_: Throwable) {}
    return false
  }

  fun findNodeByTag(tag: String): SemanticsNode? {
    val all = findAllNodes()
    all.find { matchesTag(it, tag) }?.let { return it }
    return all.find { node ->
      directTextOf(node)?.contains(tag, ignoreCase = true) == true
    }
  }

  fun snapshotNode(tag: String): NodeSnapshot? {
    return snapshotTree().find {
      it.tag == tag || it.text?.contains(tag, ignoreCase = true) == true
    }
  }

  fun snapshotTree(): List<NodeSnapshot> {
    val explicit = findAllNodes().map { toNodeSnapshot(it) }
    val discovered = discoveredSnapshots()
    
    val merged = mutableMapOf<String, NodeSnapshot>()
    val genericTags = setOf(
      "button", "dialog", "grid", "list", "listitem", "menu", "menuitem", 
      "tab", "textbox", "heading", "checkbox", "progressbar", 
      "combobox", "scrollbar", "slider", "switch", "status", "alert"
    )
    
    // Identity key strategy:
    // 1. If tag is non-empty and not generic, use tag. This handles the majority of Compose nodes.
    // 2. If tag is empty or generic, use fuzzy rounded coordinates (5px grid) to avoid key collision.
    fun identityKey(node: NodeSnapshot): String {
      val b = node.bounds
      val isGeneric = node.tag.isEmpty() || node.tag in genericTags
      return if (!isGeneric) {
        "tag:${node.tag}"
      } else {
        val rx = (b.left / 5).toInt() * 5
        val ry = (b.top / 5).toInt() * 5
        val rw = ((b.right - b.left) / 5).toInt() * 5
        val rh = ((b.bottom - b.top) / 5).toInt() * 5
        "rect:$rx,$ry,$rw,$rh"
      }
    }

    // First pass: add all explicit (Kotlin) nodes
    explicit.forEach { node ->
      merged[identityKey(node)] = node
    }
    
    // Second pass: merge discovered (JS) nodes
    discovered.forEach { node ->
      val key = identityKey(node)
      val existing = merged[key]
      if (existing == null) {
        merged[key] = node
      } else {
        // Merge strategy:
        // 1. Prefer non-empty tag
        // 2. Prefer non-null/non-blank text
        // 3. TRUST Kotlin visibility: if it's in the explicit tree, we trust its visibility 
        //    over the JS bridge which might see 'ghost' nodes during animations.
        merged[key] = existing.copy(
          tag = if (existing.tag.isEmpty()) node.tag else existing.tag,
          text = if (existing.text.isNullOrBlank()) node.text else existing.text,
        )
      }
    }
    
    return merged.values.toList()
  }

  private fun discoveredSnapshots(): List<NodeSnapshot> {
    val discoveredJson = try { discoverA11yNodesJs() } catch (_: Throwable) { null }
    if (discoveredJson.isNullOrEmpty()) return emptyList()
    return try {
      ProtocolJson.instance.decodeFromString(ListSerializer(NodeSnapshot.serializer()), discoveredJson)
    } catch (_: Throwable) {
      emptyList()
    }
  }

  private fun toNodeSnapshot(node: SemanticsNode): NodeSnapshot {
    var tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: ""
    if (tag.isEmpty()) {
      try {
        for (entry in node.config) {
          val v = entry.value
          if (v is String && v.isNotEmpty() && v.length < 100) {
            tag = v
            break
          }
        }
      } catch (_: Throwable) {}
    }

    val rawText = directTextOf(node)
    val text = if (rawText != null) {
      val isInteractive = node.config.getOrNull(SemanticsActions.OnClick) != null ||
        node.config.getOrNull(SemanticsActions.SetText) != null ||
        node.config.getOrNull(SemanticsProperties.Role) != null ||
        node.config.getOrNull(SemanticsProperties.Selected) != null
      if (isInteractive) {
        rawText
      } else {
        val descendantTexts = mutableListOf<String>()
        collectDescendantTexts(node, descendantTexts)
        if (descendantTexts.isNotEmpty() && descendantTexts.none { it.trim() == rawText.trim() }) {
          null
        } else {
          rawText
        }
      }
    } else {
      null
    }
    val bounds = node.boundsInWindow
    val hasArea = bounds.width > 0f && bounds.height > 0f

    val viewportWidth = kotlinx.browser.window.innerWidth
    val viewportHeight = kotlinx.browser.window.innerHeight

    val centerX = bounds.left + (bounds.width / 2f)
    val centerY = bounds.top + (bounds.height / 2f)
    
    var isPhysicallyVisible = hasArea &&
      centerX >= 0 && centerX <= viewportWidth &&
      centerY >= 0 && centerY <= viewportHeight
    
    if (isPhysicallyVisible) {
      var current = node.parent
      var isInsidePopupOrDialog = node.config.getOrNull(SemanticsProperties.IsPopup) != null ||
        node.config.getOrNull(SemanticsProperties.IsDialog) != null ||
        node.config.getOrNull(SemanticsProperties.TestTag)?.contains("dialog") == true

      while (current != null && !isInsidePopupOrDialog) {
        if (current.config.getOrNull(SemanticsProperties.IsPopup) != null ||
            current.config.getOrNull(SemanticsProperties.IsDialog) != null ||
            current.config.getOrNull(SemanticsProperties.TestTag)?.contains("dialog") == true) {
          isInsidePopupOrDialog = true
          break
        }
        val pb = current.boundsInWindow
        if (pb.width > 0 && pb.height > 0) {
           val isScrollable = current.config.getOrNull(SemanticsActions.ScrollBy) != null
           if (isScrollable) {
             val isClippedY = centerY < pb.top - 1 || centerY > pb.bottom + 1
             if (isClippedY) {
               isPhysicallyVisible = false
               break
             }
           }
        }
        current = current.parent
      }

      if (isInsidePopupOrDialog) {
        isPhysicallyVisible = hasArea &&
          centerX >= 0 && centerX <= viewportWidth &&
          centerY >= 0 && centerY <= viewportHeight
      }
    }

    return NodeSnapshot(
      tag = tag,
      text = text,
      visible = isPhysicallyVisible,
      bounds = Bounds(
        left = bounds.left.toDouble(),
        top = bounds.top.toDouble(),
        right = bounds.right.toDouble(),
        bottom = bounds.bottom.toDouble()
      )
    )
  }

  fun findBySelector(selector: io.github.aryapreetam.parikshan.protocol.Selector): SemanticsNode? {
    val all = findAllNodes()
    val candidates = when (selector) {
      is io.github.aryapreetam.parikshan.protocol.Selector.Tag -> all.filter {
        matchesTag(it, selector.value)
      }
      is io.github.aryapreetam.parikshan.protocol.Selector.Text -> all.filter { node ->
        directTextOf(node)?.contains(selector.value, ignoreCase = true) == true
      }
      is io.github.aryapreetam.parikshan.protocol.Selector.Auto -> {
        val tagMatches = all.filter { matchesTag(it, selector.raw) }
        if (tagMatches.isNotEmpty()) tagMatches else all.filter { node ->
          directTextOf(node)?.contains(selector.raw, ignoreCase = true) == true
        }
      }
    }

    if (candidates.isEmpty()) return null
    val targetIndex = when {
      selector.index != null && selector.index!! >= 0 -> selector.index!!
      selector.index != null && selector.index!! < 0 -> candidates.size + selector.index!!
      else -> 0
    }
    return candidates.getOrNull(targetIndex)
  }

  private fun clickTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.OnClick) != null) return current
      current = current.parent
    }
    return null
  }

  private fun inputTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.SetText) != null) return current
      current = current.parent
    }
    return null
  }

  private fun scrollTargetFor(node: SemanticsNode): SemanticsNode? {
    var current: SemanticsNode? = node
    while (current != null) {
      if (current.config.getOrNull(SemanticsActions.ScrollBy) != null) return current
      current = current.parent
    }

    val queue = ArrayDeque<SemanticsNode>()
    queue.addAll(node.children)
    while (queue.isNotEmpty()) {
      val child = queue.removeFirst()
      if (child.config.getOrNull(SemanticsActions.ScrollBy) != null) return child
      queue.addAll(child.children)
    }

    return null
  }

  fun performClick(selector: io.github.aryapreetam.parikshan.protocol.Selector): Boolean {
    val node = findBySelector(selector)
    if (node != null) {
      var current: SemanticsNode? = node
      var textFieldAction: (() -> Boolean)? = null
      var bestAction: (() -> Boolean)? = null

      while (current != null) {
        val action = current.config.getOrNull(SemanticsActions.OnClick)?.action
        if (action != null) {
          val isEditable = current.config.getOrNull(SemanticsProperties.EditableText) != null
          if (!isEditable) {
            bestAction = action
            break
          }
          if (textFieldAction == null) {
            textFieldAction = action
          }
        }
        current = current.parent
      }

      val actionToInvoke = bestAction ?: textFieldAction
      if (actionToInvoke != null && actionToInvoke.invoke()) {
        return true
      }
      if (performPhysicalClick(node)) return true
    }

    return clickA11yNode(selector.raw, selector.index ?: 0)
  }

  private fun performPhysicalClick(node: SemanticsNode): Boolean {
    val bounds = node.boundsInWindow
    val positions = buildList {
      add(Offset(bounds.left + bounds.width / 2f, bounds.top + bounds.height / 2f))
    }
    return positions.any { performPhysicalClickAt(it.x, it.y) }
  }

  private fun performPhysicalClickAt(x: Float, y: Float): Boolean {
    val root = registeredOwners.firstOrNull()?.rootForTest as? PlatformRootForTest ?: return false
    return try {
      dispatchMouseClick(root, Offset(x, y))
      true
    } catch (_: Throwable) {
      false
    }
  }

  private fun dispatchMouseClick(root: PlatformRootForTest, position: Offset) {
    root.sendPointerEvent(
      eventType = PointerEventType.Press,
      position = position,
      type = PointerType.Mouse,
      button = PointerButton.Primary,
    )
    root.sendPointerEvent(
      eventType = PointerEventType.Release,
      position = position,
      type = PointerType.Mouse,
      button = PointerButton.Primary,
    )
    root.measureAndLayoutForTest()
  }

  fun performInput(selector: io.github.aryapreetam.parikshan.protocol.Selector, text: String): Boolean {
    val node = findBySelector(selector) ?: return false
    val target = inputTargetFor(node) ?: return false
    val action = target.config.getOrNull(SemanticsActions.SetText) ?: return false
    return action.action?.invoke(AnnotatedString(text)) ?: false
  }

  fun performScroll(selector: io.github.aryapreetam.parikshan.protocol.Selector, direction: ScrollDirection): Boolean {
    val node = findBySelector(selector) ?: return false
    val target = scrollTargetFor(node) ?: node
    val action = target.config.getOrNull(SemanticsActions.ScrollBy) ?: return false
    val x = if (direction == ScrollDirection.Left) -1000f else if (direction == ScrollDirection.Right) 1000f else 0f
    val y = if (direction == ScrollDirection.Up) -1000f else if (direction == ScrollDirection.Down) 1000f else 0f
    return action.action?.invoke(x, y) ?: false
  }
}

/**
 * Discovers overlay/popup semantics nodes mirrored into the Compose a11y shadow DOM.
 */
private fun discoverA11yNodesJs(): String? = js(
  """
  (function() {
    try {
      const vWidth = window.innerWidth, vHeight = window.innerHeight;
      const results = [];

      function findA11yRoot() {
        function findInTree(node) {
          if (!node) return null;
          if (node.id === 'cmp_a11y_root') return node;
          if (node.querySelector) {
            const direct = node.querySelector('#cmp_a11y_root');
            if (direct) return direct;
          }
          if (node.shadowRoot) {
            const inShadow = findInTree(node.shadowRoot);
            if (inShadow) return inShadow;
          }
          const children = node.children || [];
          for (let i = 0; i < children.length; i++) {
            const found = findInTree(children[i]);
            if (found) return found;
          }
          return null;
        }
        return findInTree(document.body);
      }

      function extractText(el) {
        const aria = el.getAttribute && el.getAttribute('aria-label');
        if (aria && aria.trim()) return aria.trim();
        // Only use innerText for leaf elements or elements with an explicit role (like buttons/menuitems)
        // This prevents giant container elements from reporting the concatenated text of all their children.
        const role = el.getAttribute && el.getAttribute('role');
        const hasElementChildren = el.children && el.children.length > 0;
        if (!hasElementChildren || role) {
          const text = el.innerText || el.textContent;
          if (text && text.trim()) return text.trim();
        }
        return null;
      }

      function collect(el) {
        if (!el) return;
        try {
          const text = extractText(el);
          const rect = el.getBoundingClientRect();
          const w = rect.right - rect.left, h = rect.bottom - rect.top;
          const hasId = !!(el.id && el.id.trim());
          if ((text || hasId) && w > 0 && h > 0) {
            const role = el.getAttribute && el.getAttribute('role');
            const tag = el.id || role || '';
            const cx = rect.left + w / 2, cy = rect.top + h / 2;
            
            let vis = cx >= 0 && cx <= vWidth && cy >= 0 && cy <= vHeight;
            if (vis) {
              // Check if clipped by any parent with overflow
              let p = el.parentElement;
              while (p && p !== document.body) {
                const s = window.getComputedStyle(p);
                if (s.overflow === 'hidden' || s.overflow === 'auto' || s.overflow === 'scroll') {
                  const pr = p.getBoundingClientRect();
                  if (cx < pr.left - 1 || cx > pr.right + 1 || cy < pr.top - 1 || cy > pr.bottom + 1) {
                    vis = false; break;
                  }
                }
                p = p.parentElement;
              }
            }
            
            results.push({
              tag: String(tag || ''),
              text: text,
              visible: vis,
              bounds: { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom }
            });
          }
        } catch (ex) {}
        const children = el.children || [];
        for (let i = 0; i < children.length; i++) collect(children[i]);
      }

      const root = findA11yRoot();
      if (!root) return null;
      collect(root);
      return results.length > 0 ? JSON.stringify(results) : null;
    } catch (e) {
      return null;
    }
  })()
  """
)

internal fun clickA11yNode(query: String, index: Int): Boolean {
  ParikshanA11yGlobals.query = query
  ParikshanA11yGlobals.index = index
  return clickPendingA11yNodeJs()
}

private fun clickPendingA11yNodeJs(): Boolean = js(
  """
  (function() {
    try {
      var query = globalThis.__parikshan_a11y_query;
      var index = globalThis.__parikshan_a11y_index || 0;
      function findA11yRoot() {
        function findInTree(node) {
          if (!node) return null;
          if (node.id === 'cmp_a11y_root') return node;
          if (node.querySelector) {
            const direct = node.querySelector('#cmp_a11y_root');
            if (direct) return direct;
          }
          if (node.shadowRoot) {
            const inShadow = findInTree(node.shadowRoot);
            if (inShadow) return inShadow;
          }
          const children = node.children || [];
          for (let i = 0; i < children.length; i++) {
            const found = findInTree(children[i]);
            if (found) return found;
          }
          return null;
        }
        return findInTree(document.body);
      }

      function extractText(el) {
        const aria = el.getAttribute && el.getAttribute('aria-label');
        if (aria && aria.trim()) return aria.trim();
        // Only use innerText for leaf elements or elements with an explicit role (like buttons/menuitems)
        // This prevents giant container elements from reporting the concatenated text of all their children.
        const role = el.getAttribute && el.getAttribute('role');
        const hasElementChildren = el.children && el.children.length > 0;
        if (!hasElementChildren || role) {
          const text = el.innerText || el.textContent;
          if (text && text.trim()) return text.trim();
        }
        return null;
      }

      function matchScore(el) {
        const text = extractText(el);
        const id = el.id || '';
        if (id === query) return 0;
        if (text === query) return 1;
        if (text && text.toLowerCase() === query.toLowerCase()) return 2;
        if (id && id.toLowerCase().includes(query.toLowerCase())) return 3;
        if (text && text.toLowerCase().includes(query.toLowerCase())) return 4;
        return -1;
      }

      function collectMatches(el, out) {
        if (!el) return;
        const score = matchScore(el);
        if (score >= 0) out.push({ el: el, score: score, len: (extractText(el) || '').length });
        const children = el.children || [];
        for (let i = 0; i < children.length; i++) collectMatches(children[i], out);
      }

      const root = findA11yRoot();
      if (!root) return false;
      const byId = root.querySelector('#' + query.replace(/[^a-zA-Z0-9_-]/g, ''));
      if (byId && byId.id === query) {
        if (byId.click) { byId.click(); return true; }
      }
      const matched = [];
      collectMatches(root, matched);
      matched.sort(function(a, b) {
        if (a.score !== b.score) return a.score - b.score;
        return a.len - b.len;
      });
      const target = (matched[index] || matched[0] || {}).el;
      if (!target) return false;
      const rect = target.getBoundingClientRect();
      const x = rect.left + rect.width / 2;
      const y = rect.top + rect.height / 2;
      function findCanvas(node) {
        if (!node) return null;
        if (node.tagName === 'CANVAS') return node;
        const children = node.children || [];
        for (let i = 0; i < children.length; i++) {
          const found = findCanvas(children[i]);
          if (found) return found;
        }
        if (node.shadowRoot) return findCanvas(node.shadowRoot);
        return null;
      }
      if (target.click) {
        target.click();
        return true;
      }
      const base = {
        bubbles: true,
        cancelable: true,
        composed: true,
        clientX: x,
        clientY: y,
        button: 0
      };
      target.dispatchEvent(new MouseEvent('click', base));
      return true;
    } catch (e) {
      return false;
    }
  })()
  """
)

@JsName("globalThis")
private external object ParikshanA11yGlobals {
  @JsName("__parikshan_a11y_query")
  var query: String

  @JsName("__parikshan_a11y_index")
  var index: Int
}
