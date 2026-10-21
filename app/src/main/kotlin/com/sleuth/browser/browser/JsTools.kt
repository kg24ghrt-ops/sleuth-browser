package com.sleuth.browser.browser

import org.json.JSONObject

/**
 * Small helpers for driving the page from the browser chrome: pulling text out
 * of a document and marking up matches for find on page.
 */
object JsTools {

    /**
     * Pulls the visible text of a page. The walk stops as soon as there is
     * enough, so a huge document never has to travel across the bridge and
     * the page is not kept busy building a string nobody will read.
     */
    fun extractTextScript(): String =
        """
(function(){
  try {
    var limit = 2000;
    var root = document.body || document.documentElement;
    if (!root) return '';
    var walker = document.createTreeWalker(root, 4, null, false);
    var parts = [];
    var total = 0;
    var node;
    while ((node = walker.nextNode())) {
      var value = node.nodeValue;
      if (!value) continue;
      var text = value.replace(/\s+/g, ' ');
      if (!text) continue;
      if (total + text.length > limit) {
        parts.push(text.substring(0, limit - total));
        total = limit;
        break;
      }
      parts.push(text);
      total += text.length;
      if (total >= limit) break;
    }
    if (!parts.length) {
      var raw = (root.textContent || '').replace(/\s+/g, ' ');
      return raw.length > limit ? raw.substring(0, limit) : raw;
    }
    return parts.join('').trim();
  } catch (e) { return ''; }
})();
        """.trimIndent()

    fun findScript(query: String): String {
        val literal = JSONObject.quote(query)
        return """
(function(){
  try {
    var q = $literal;
    if (!q) return '0';
    if (window.__sleuthMarks) {
      for (var i = 0; i < window.__sleuthMarks.length; i++) {
        var m = window.__sleuthMarks[i];
        if (m && m.parentNode) {
          while (m.firstChild) m.parentNode.insertBefore(m.firstChild, m);
          m.parentNode.removeChild(m);
        }
      }
      window.__sleuthMarks = [];
    }
    var marks = [];
    if (!document.body) return '0';
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
    var nodes = [];
    while (walker.nextNode()) nodes.push(walker.currentNode);
    var lowerQ = q.toLowerCase();
    var count = 0;
    var first = null;
    for (var n = 0; n < nodes.length; n++) {
      var node = nodes[n];
      var value = node.nodeValue;
      if (!value) continue;
      var lower = value.toLowerCase();
      if (lower.indexOf(lowerQ) === -1) continue;
      var fragment = document.createDocumentFragment();
      var index = 0;
      var position;
      while ((position = lower.indexOf(lowerQ, index)) !== -1) {
        if (position > index) fragment.appendChild(document.createTextNode(value.substring(index, position)));
        var span = document.createElement('span');
        span.style.backgroundColor = '#E0A43C';
        span.style.color = '#000000';
        span.appendChild(document.createTextNode(value.substr(position, q.length)));
        fragment.appendChild(span);
        marks.push(span);
        if (!first) first = span;
        count++;
        index = position + q.length;
      }
      if (index < value.length) fragment.appendChild(document.createTextNode(value.substring(index)));
      if (node.parentNode) node.parentNode.replaceChild(fragment, node);
    }
    window.__sleuthMarks = marks;
    if (first && first.scrollIntoView) first.scrollIntoView({block: 'center'});
    return String(count);
  } catch (e) {
    return 'error';
  }
})()
        """.trimIndent()
    }

    /** evaluateJavascript hands back a JSON encoded value, so unwrap it. */
    fun decodeString(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        if (!trimmed.startsWith("\"")) return trimmed
        return try {
            JSONObject("{\"v\":$trimmed}").optString("v")
        } catch (error: Throwable) {
            trimmed.trim('"')
        }
    }
}
