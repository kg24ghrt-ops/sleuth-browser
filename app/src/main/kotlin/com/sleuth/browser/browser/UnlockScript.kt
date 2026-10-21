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
 * A page that fights back is handled as well. The originals of the tools the
 * guard needs are kept before the page can change them, the capture surfaces
 * are locked so they cannot be put back, the unlock rule is moved to the end
 * of the document so a page rule of the same weight cannot win, and the style
 * element is rebuilt if the page throws it away.
 *
 * Nothing is left in the page that gives the game away: the marker lives on a
 * javascript property of the injected style element instead of an id, a data
 * attribute or a global name, and it is different on every run.
 */
object UnlockScript {

    /** A name that does not give the game away. */
    private val token: String = UUID.randomUUID().toString().replace("-", "").substring(0, 10)

    /** The mark that lets the browser find its own style element again. */
    private val marker: String = "__sl_" + token

    /** Where the tricks the guard caught are written down. */
    private const val CODES_SUFFIX = "c"

    /** Where the observer is kept, so only one is ever watching. */
    private const val OBSERVER_SUFFIX = "o"

    /** Where the event stopper is kept, so it is only ever registered once. */
    private const val STOP_SUFFIX = "p"

    /** Events the page must never hear about. */
    private val hiddenEvents = listOf(
        "copy", "cut", "paste", "contextmenu", "selectstart", "select",
        "selectionchange",
        "visibilitychange", "focus", "blur", "pagehide", "pageshow",
        "freeze", "resume", "beforeunload", "unload"
    )

    /** The unlock script, injected before the page runs its own code. */
    val UNLOCK: String = buildScript(emptySet(), light = false)

    /**
     * The same script, used again right before a capture. It is written so it
     * can run as often as needed: it re-asserts the styles, scrubs the inline
     * handlers and takes the capture surfaces away again.
     */
    val REASSERT: String = buildScript(emptySet(), light = true)

    /**
     * The script for a set of sites that are allowed to protect themselves.
     * The sites are written into the script, so the guard stands down on its
     * own the moment it lands on one of them.
     */
    fun forBypassed(bypassed: Set<String>, light: Boolean = false): String =
        buildScript(bypassed, light)

    /**
     * Reports what the page is doing to protect itself. The answer is a short
     * list of codes, one per trick. The tricks the guard caught are read back
     * from the page, and the rest is looked for in the page's own stylesheets.
     */
    val PROBE: String = buildProbe()

    private fun buildScript(bypassed: Set<String>, light: Boolean): String {
        val events = hiddenEvents.joinToString(",") { "'$it'" }
        val hosts = bypassed.sorted().joinToString(",") { "'${it.replace("'", "")}'" }
        return """
(function(){
  var MARK = '$marker';
  var CODES = MARK + '$CODES_SUFFIX';
  var OBSERVER = MARK + '$OBSERVER_SUFFIX';
  var STOP = MARK + '$STOP_SUFFIX';
  var LIGHT = $light;
  var BYPASS = [$hosts];

  // A site the user left alone is not touched at all.
  try {
    var host = String(location.hostname || '').toLowerCase();
    for (var b = 0; b < BYPASS.length; b++) {
      var name = BYPASS[b];
      if (!name) continue;
      if (host === name ||
          (host.length > name.length && host.slice(-(name.length + 1)) === '.' + name)) {
        return;
      }
    }
  } catch (e) {}

  // The page may try to take the tools away. The originals are kept here,
  // before the page gets a chance to change them.
  var nativeStop = null;
  var nativeDefine = null;
  try { nativeStop = Event.prototype.stopImmediatePropagation; } catch (e) {}
  try { nativeDefine = Object.defineProperty; } catch (e) {}

  var css = 'html,html body,html body *,:root body *{-webkit-user-select:text !important;' +
            'user-select:text !important;-webkit-touch-callout:default !important;}';

  var style = null;

  var findStyle = function () {
    var list = document.getElementsByTagName('style');
    for (var i = 0; i < list.length; i++) { if (list[i][MARK]) return list[i]; }
    return null;
  };

  var record = function (code) {
    try {
      if (!style) return;
      var current = style[CODES] || '';
      var parts = String(current).split(',');
      for (var i = 0; i < parts.length; i++) {
        if (parts[i] === code) return;
      }
      style[CODES] = current ? current + ',' + code : code;
    } catch (e) {}
  };

  // Our rule goes in last, so a page rule of the same weight cannot win.
  var place = function () {
    try {
      var parent = style.parentNode;
      if (!parent) return;
      if (parent.lastChild === style) return;
      parent.removeChild(style);
      parent.appendChild(style);
    } catch (e) {}
  };

  // Finds the style element, or builds a new one when the page threw it away.
  var ensure = function () {
    var found = findStyle();
    if (!found) {
      var old = style;
      try {
        found = document.createElement('style');
        found.type = 'text/css';
        found[MARK] = true;
        if (old) {
          // the record and the watcher move across to the new element
          try { if (old[CODES]) found[CODES] = old[CODES]; } catch (e) {}
          try { if (old[OBSERVER]) found[OBSERVER] = old[OBSERVER]; } catch (e) {}
          try { if (old[STOP]) found[STOP] = old[STOP]; } catch (e) {}
        }
        (document.head || document.documentElement).appendChild(found);
      } catch (e) { found = null; }
    }
    if (!found) return null;
    style = found;
    try { if (style.textContent !== css) style.textContent = css; } catch (e) {}
    place();
    return style;
  };

  var stop = function (event) {
    try {
      if (nativeStop) { nativeStop.call(event); return; }
      if (event && event.stopImmediatePropagation) event.stopImmediatePropagation();
    } catch (e) {}
  };

  var guard = function () {
    try {
      // The same stopper is used every time, so a page that is asked twice
      // does not end up with a pile of listeners.
      var fn = (style && style[STOP]) ? style[STOP] : stop;
      if (style) style[STOP] = fn;
      var names = [$events];
      for (var n = 0; n < names.length; n++) {
        window.addEventListener(names[n], fn, true);
        document.addEventListener(names[n], fn, true);
      }
    } catch (e) {}
  };

  // Inline handlers are taken off, and written down as evidence.
  var takeHandler = function (el) {
    try {
      if (!el || el.nodeType !== 1) return;
      if (el.hasAttribute('oncopy')) { record('oncopy'); el.removeAttribute('oncopy'); }
      if (el.hasAttribute('oncut')) { record('oncut'); el.removeAttribute('oncut'); }
      if (el.hasAttribute('oncontextmenu')) { record('oncontextmenu'); el.removeAttribute('oncontextmenu'); }
    } catch (e) {}
  };

  var scrubHandlers = function () {
    try {
      var nodes = document.querySelectorAll('[oncopy],[oncut],[oncontextmenu]');
      for (var k = 0; k < nodes.length; k++) takeHandler(nodes[k]);
    } catch (e) {}
  };

  // The work is done on the nodes that changed, not on the whole page.
  var scrubNodes = function (nodes) {
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (!el || el.nodeType !== 1) continue;
      takeHandler(el);
      try {
        var inner = el.querySelectorAll('[oncopy],[oncut],[oncontextmenu]');
        for (var k = 0; k < inner.length; k++) takeHandler(inner[k]);
      } catch (e) {}
    }
  };

  // A page can also lock the text down with an inline style.
  var takeStyle = function (el) {
    try {
      if (!el || el.nodeType !== 1) return;
      var value = (el.getAttribute('style') || '').toLowerCase();
      if (value.indexOf('user-select') === -1) return;
      record('select');
      el.style.removeProperty('user-select');
      el.style.removeProperty('-webkit-user-select');
    } catch (e) {}
  };

  var scrubStyles = function () {
    try {
      var all = document.querySelectorAll('[style]');
      var limit = all.length < 800 ? all.length : 800;
      for (var m = 0; m < limit; m++) takeStyle(all[m]);
    } catch (e) {}
  };

  var handlerTimer = null;
  var styleTimer = null;
  var needFull = false;
  var needFullStyles = false;
  var pending = [];
  var pendingStyled = [];

  var scheduleHandlers = function () {
    if (handlerTimer) clearTimeout(handlerTimer);
    handlerTimer = setTimeout(function () {
      handlerTimer = null;
      try {
        if (needFull) { needFull = false; scrubHandlers(); }
        if (pending.length) { scrubNodes(pending); pending = []; }
      } catch (e) {}
    }, 200);
  };

  var scheduleStyles = function () {
    if (styleTimer) clearTimeout(styleTimer);
    styleTimer = setTimeout(function () {
      styleTimer = null;
      try {
        if (needFullStyles) { needFullStyles = false; scrubStyles(); }
        if (pendingStyled.length) {
          for (var i = 0; i < pendingStyled.length; i++) takeStyle(pendingStyled[i]);
          pendingStyled = [];
        }
      } catch (e) {}
    }, 400);
  };

  // Work that is not urgent waits for a quiet moment, so a page that is still
  // loading is never slowed down by the guard.
  var idle = function (work) {
    try {
      if (window.requestIdleCallback) {
        window.requestIdleCallback(work, { timeout: 1500 });
        return;
      }
    } catch (e) {}
    setTimeout(work, 200);
  };

  var scrub = function () {
    ensure();
    needFull = true;
    needFullStyles = true;
    if (LIGHT) {
      scheduleHandlers();
      scheduleStyles();
    } else {
      idle(function () { scheduleHandlers(); scheduleStyles(); });
    }
  };

  try {
    ensure();
    guard();

    // One observer for the whole page. It watches for the tricks and for the
    // moment the page throws our style element away.
    if (style && window.MutationObserver && document.documentElement) {
      if (!style[OBSERVER]) {
        var observer = new MutationObserver(function (records) {
          var added = [];
          var styled = [];
          var lost = false;
          for (var i = 0; i < records.length; i++) {
            var entry = records[i];
            if (entry.target === style) continue;
            if (entry.type === 'childList') {
              if (entry.addedNodes) {
                for (var a = 0; a < entry.addedNodes.length; a++) {
                  var node = entry.addedNodes[a];
                  if (node !== style && node && node.nodeType === 1) added.push(node);
                }
              }
              if (entry.removedNodes) {
                for (var d = 0; d < entry.removedNodes.length; d++) {
                  if (entry.removedNodes[d] === style) lost = true;
                }
              }
              continue;
            }
            if (entry.attributeName === 'style') {
              var value = '';
              try { value = (entry.target.getAttribute('style') || '').toLowerCase(); } catch (e) {}
              if (value.indexOf('user-select') !== -1) styled.push(entry.target);
              continue;
            }
            added.push(entry.target);
          }
          if (lost) {
            // the page threw the style element away, so it is built again
            var still = false;
            try { still = document.documentElement.contains(style); } catch (e) { still = false; }
            if (!still) scrub();
          }
          if (added.length) {
            for (var p = 0; p < added.length; p++) pending.push(added[p]);
            scheduleHandlers();
          }
          if (styled.length) {
            for (var q = 0; q < styled.length; q++) pendingStyled.push(styled[q]);
            scheduleStyles();
          }
        });
        observer.observe(document.documentElement, {
          childList: true,
          subtree: true,
          attributes: true,
          attributeFilter: ['oncopy','oncut','oncontextmenu','style']
        });
        style[OBSERVER] = observer;
      }
    }
    // A light run only puts the lock back, it does not go looking for tricks.
    if (!LIGHT) scrub();
  } catch (e) {}

  try {
    // The official screen capture surfaces are taken away, and locked so the
    // page cannot put them back.
    var refuse = function (what) {
      return function () { return Promise.reject(new Error(what)); };
    };
    var lock = function (host, name, value) {
      if (!host) return false;
      try {
        if (nativeDefine) {
          nativeDefine.call(Object, host, name, {
            value: value, writable: false, enumerable: false, configurable: false
          });
          return true;
        }
      } catch (e) {}
      try { host[name] = value; return true; } catch (e) {}
      return false;
    };
    if (navigator.mediaDevices) {
      lock(navigator.mediaDevices, 'getDisplayMedia', refuse('Screen capture is not available'));
    }
    if (window.getScreenDetails) {
      lock(window, 'getScreenDetails', refuse('Screen capture is not available'));
    }
    // A page must not be able to read what the user just copied.
    if (navigator.clipboard) {
      lock(navigator.clipboard, 'readText', refuse('Clipboard reading is not available'));
      if (navigator.clipboard.read) {
        lock(navigator.clipboard, 'read', refuse('Clipboard reading is not available'));
      }
    }
  } catch (e) {}

  try {
    // The page must not be able to see the browser leave the screen.
    var docProto = window.Document ? window.Document.prototype : null;
    if (docProto && nativeDefine) {
      nativeDefine.call(Object, docProto, 'visibilityState', {
        get: function () { return 'visible'; }, configurable: true
      });
      nativeDefine.call(Object, docProto, 'hidden', {
        get: function () { return false; }, configurable: true
      });
      nativeDefine.call(Object, docProto, 'hasFocus', {
        value: function () { return true; }, writable: true, configurable: true
      });
    }
  } catch (e) {}
})();
""".trimIndent()
    }

    private fun buildProbe(): String {
        return """
(function(){
  var found = [];
  var MARK = '$marker';
  var CODES = MARK + '$CODES_SUFFIX';
  try {
    // The tricks the guard caught while the page was running.
    var list = document.getElementsByTagName('style');
    for (var i = 0; i < list.length; i++) {
      var recorded = list[i][CODES];
      if (!recorded) continue;
      var parts = String(recorded).split(',');
      for (var p = 0; p < parts.length; p++) {
        var code = parts[p].trim();
        if (code && found.indexOf(code) === -1) found.push(code);
      }
    }
  } catch (e) {}
  try {
    // Any inline handler the guard has not caught yet.
    var handlers = [['oncopy','oncopy'],['oncut','oncut'],['oncontextmenu','oncontextmenu']];
    for (var h = 0; h < handlers.length; h++) {
      if (document.querySelector('[' + handlers[h][0] + ']') &&
          found.indexOf(handlers[h][1]) === -1) {
        found.push(handlers[h][1]);
      }
    }
  } catch (e) {}
  try {
    var sheets = document.styleSheets;
    for (var s = 0; sheets && s < sheets.length; s++) {
      var rules = null;
      try { rules = sheets[s].cssRules; } catch (e) { continue; }
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
              if (found.indexOf('print') === -1) found.push('print');
            }
          }
        }
        if (rule.style) {
          var select = '';
          try {
            select = rule.style.getPropertyValue('-webkit-user-select') ||
                     rule.style.getPropertyValue('user-select');
          } catch (e) { select = ''; }
          if (select && String(select).toLowerCase().indexOf('none') !== -1) {
            if (found.indexOf('select') === -1) found.push('select');
          }
        }
      }
    }
  } catch (e) {}
  return found.join(',');
})();
""".trimIndent()
    }
}
