package com.sleuth.browser.browser

import java.util.UUID

/**
 * The scripts that keep a hostile page from locking the browser down, and from
 * watching what the browser is doing.
 *
 * Pages block copying with inline handlers, with capture phase listeners and
 * with `user-select: none`, and they hide themselves with print stylesheets.
 * The unlock script is injected before the page runs, so the page never gets
 * the chance to put the lock on in the first place.
 *
 * The same script takes the page's eyes off the browser: visibility, focus,
 * clipboard and unload events are stopped before the page can hear them, and
 * the official screen capture surfaces are taken away, so a page cannot tell
 * that a picture was taken or that something was copied.
 *
 * Nothing is left in the page that gives the game away: the marker lives on a
 * javascript property of the injected style element instead of an id, a data
 * attribute or a global name.
 */
object UnlockScript {

    /** A name that does not give the game away. */
    private val token: String = UUID.randomUUID().toString().replace("-", "").substring(0, 10)

    private const val MARK = "__sl__TOKEN__"

    /** Events the page must never hear about. */
    private val hiddenEvents = listOf(
        "copy", "cut", "paste", "contextmenu", "selectstart", "select",
        "visibilitychange", "focus", "blur", "pagehide", "pageshow",
        "freeze", "resume", "beforeunload", "unload"
    )

    /** The unlock script, injected before the page runs its own code. */
    val UNLOCK: String = buildUnlock()

    /**
     * The same script, used again right before a capture. It is written so it
     * can run as often as needed: it re-asserts the styles, scrubs the inline
     * handlers and takes the capture surfaces away again.
     */
    val REASSERT: String = UNLOCK

    /**
     * Reports what the page is doing to protect itself. The answer is a short
     * list of codes, one per trick.
     */
    val PROBE: String = """
(function(){
  var found = [];
  try {
    var body = document.body;
    if (body) {
      var style = window.getComputedStyle(body);
      var select = style.getPropertyValue('-webkit-user-select') ||
                   style.getPropertyValue('user-select');
      if (select && select.indexOf('none') !== -1) found.push('select');
    }
    if (document.querySelector('[oncopy]')) found.push('oncopy');
    if (document.querySelector('[oncut]')) found.push('oncut');
    if (document.querySelector('[oncontextmenu]')) found.push('oncontextmenu');
    var sheets = document.styleSheets;
    for (var i = 0; sheets && i < sheets.length; i++) {
      var rules = null;
      try { rules = sheets[i].cssRules; } catch (e) { continue; }
      if (!rules) continue;
      for (var j = 0; j < rules.length; j++) {
        var rule = rules[j];
        if (rule.type === 4 && rule.media &&
            String(rule.media.mediaText).toLowerCase().indexOf('print') !== -1) {
          var inner = rule.cssRules;
          for (var k = 0; inner && k < inner.length; k++) {
            var rule2 = inner[k];
            if (rule2.style &&
                (rule2.style.display === 'none' || rule2.style.visibility === 'hidden')) {
              found.push('print');
            }
          }
        }
      }
    }
  } catch (e) {}
  return found.join(',');
})();
""".trimIndent()

    private fun buildUnlock(): String {
        val events = hiddenEvents.joinToString(",") { "'$it'" }
        return """
(function(){
  try {
    var MARK = '$MARK';
    var find = function () {
      var list = document.getElementsByTagName('style');
      for (var i = 0; i < list.length; i++) { if (list[i][MARK]) return list[i]; }
      return null;
    };
    var css = '*{-webkit-user-select:text !important;user-select:text !important;' +
              '-webkit-touch-callout:default !important;}';
    var style = find();
    if (!style) {
      style = document.createElement('style');
      style.type = 'text/css';
      (document.head || document.documentElement).appendChild(style);
      style[MARK] = true;
    }
    style.textContent = css;

    if (!style[MARK + 'i']) {
      style[MARK + 'i'] = true;
      var stop = function (event) { event.stopImmediatePropagation(); };
      var names = [$events];
      for (var n = 0; n < names.length; n++) {
        window.addEventListener(names[n], stop, true);
        document.addEventListener(names[n], stop, true);
      }
      var timer = null;
      var scrub = function () {
        if (timer) { clearTimeout(timer); timer = null; }
        timer = setTimeout(function () {
          timer = null;
          try {
            var nodes = document.querySelectorAll('[oncopy],[oncut],[oncontextmenu]');
            for (var k = 0; k < nodes.length; k++) {
              nodes[k].removeAttribute('oncopy');
              nodes[k].removeAttribute('oncut');
              nodes[k].removeAttribute('oncontextmenu');
            }
          } catch (e) {}
        }, 250);
      };
      style[MARK + 's'] = scrub;
      if (window.MutationObserver && document.documentElement) {
        var observer = new MutationObserver(function () { scrub(); });
        observer.observe(document.documentElement, {
          childList: true,
          subtree: true,
          attributes: true,
          attributeFilter: ['oncopy','oncut','oncontextmenu']
        });
      }
    }
    if (style[MARK + 's']) { style[MARK + 's'](); }
  } catch (e) {}

  try {
    // the official screen capture surfaces are taken away
    if (navigator.mediaDevices && navigator.mediaDevices.getDisplayMedia) {
      navigator.mediaDevices.getDisplayMedia = function () {
        return Promise.reject(new Error('Screen capture is not available'));
      };
    }
  } catch (e) {}

  try {
    if (window.getScreenDetails) {
      window.getScreenDetails = function () {
        return Promise.reject(new Error('Screen capture is not available'));
      };
    }
  } catch (e) {}

  try {
    // a page must not be able to read what the user just copied
    if (navigator.clipboard && navigator.clipboard.readText) {
      navigator.clipboard.readText = function () {
        return Promise.reject(new Error('Clipboard reading is not available'));
      };
    }
  } catch (e) {}
})();
""".trimIndent().replace("__TOKEN__", token)
    }
}
