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
 * are locked on the prototype where they really live, the unlock rule is put
 * at the very end of the document so a page rule of the same weight cannot
 * win, and the style element is rebuilt if the page throws it away.
 *
 * The guard stays out of the way of the page as much as it can. It only looks
 * at the parts of the page that changed, the first sweep waits for a quiet
 * moment, and a page that keeps throwing the style element away is left to
 * do it rather than being fought over and over again.
 *
 * Nothing is left in the page that gives the game away: the marker lives on a
 * javascript property of the injected style element instead of an id, a data
 * attribute or a global name, it has no fixed name to look for, and it is
 * different on every run.
 */
object UnlockScript {

    /** A name that does not give the game away, and is different on every run. */
    private val token: String = UUID.randomUUID().toString().replace("-", "").substring(0, 12)

    /** The mark that lets the browser find its own style element again. */
    private val marker: String = "__" + token

    /** Where the tricks the guard caught are written down. */
    private const val CODES_SUFFIX = "c"

    /** Where the observer is kept, so only one is ever watching. */
    private const val OBSERVER_SUFFIX = "o"

    /** Where the event stopper is kept, so it is only ever registered once. */
    private const val STOP_SUFFIX = "s"

    /** Where the native listener is kept, so the guard never builds on itself. */
    private const val ADD_SUFFIX = "a"

    /** Where the native preventDefault is kept. */
    private const val PREVENT_SUFFIX = "v"

    /** Where the native toString is kept. */
    private const val TOSTRING_SUFFIX = "t"

    /** Where the list of dressed up functions is kept. */
    private const val WRAPPED_SUFFIX = "w"

    /** Where the dressed up toString is kept. */
    private const val SPOOF_SUFFIX = "f"

    /** How many times the style element may be thrown away before the guard gives up. */
    private const val MAX_REBUILDS = 25

    /** Events that matter wherever the text is. */
    private val textEvents = listOf(
        "copy", "beforecopy", "cut", "beforecut", "paste",
        "contextmenu", "selectstart", "select", "selectionchange", "dragstart"
    )

    /** Events that only matter in the page the user is looking at. */
    private val browserEvents = listOf(
        "visibilitychange", "focus", "blur", "pagehide", "pageshow",
        "freeze", "resume", "beforeunload", "unload"
    )

    /** Events the page must never hear about. */
    private val hiddenEvents = textEvents + browserEvents

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
    fun forBypassed(bypassed: Set<String>, light: Boolean = false): String {
        val cached = cached
        if (cached != null && cached.light == light && cached.hosts == bypassed) {
            return cached.script
        }
        val script = buildScript(bypassed, light)
        cached = Cached(bypassed, light, script)
        return script
    }

    /** The last script that was built, so the same one is not built twice. */
    private class Cached(val hosts: Set<String>, val light: Boolean, val script: String)

    private var cached: Cached? = null

    private fun buildScript(bypassed: Set<String>, light: Boolean): String {
        val events = hiddenEvents.joinToString(",") { "'$it'" }
        val hosts = bypassed.sorted().joinToString(",") { "'${it.replace("'", "")}'" }
        return """
(function(){
  var MARK = '$marker';
  var CODES = MARK + '$CODES_SUFFIX';
  var OBSERVER = MARK + '$OBSERVER_SUFFIX';
  var STOP = MARK + '$STOP_SUFFIX';
  var ADD = MARK + '$ADD_SUFFIX';
  var PREVENT = MARK + '$PREVENT_SUFFIX';
  var TOSTRING = MARK + '$TOSTRING_SUFFIX';
  var WRAPPED = MARK + '$WRAPPED_SUFFIX';
  var SPOOF = MARK + '$SPOOF_SUFFIX';
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

  // Only the page the user is looking at is worth watching closely. A page
  // full of frames would pay for the whole guard in every one of them.
  var TOP = true;
  try { TOP = (window.top === window); } catch (e) { TOP = true; }

  // The page may try to take the tools away. The originals are kept here,
  // before the page gets a chance to change them.
  var nativeStop = null;
  var nativeDefine = null;
  try { nativeStop = Event.prototype.stopImmediatePropagation; } catch (e) {}
  try { nativeDefine = Object.defineProperty; } catch (e) {}

  // The rule is heavy on purpose, so a page rule of the same weight cannot
  // outrank it, and it goes in last, so a tie is won here as well.
  var css = 'html,html body,html body *,:root body *,:root:root body *{-webkit-user-select:text !important;' +
            'user-select:text !important;-webkit-touch-callout:default !important;}';

  var style = null;
  var rebuilds = 0;
  var wrapped = [];

  var findStyle = function () {
    // our own element is preferred, a copy made by the page is not
    try {
      if (style && style[MARK] && style.parentNode) return style;
    } catch (e) {}
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

  // Our rule goes to the very end of the document, so every rule the page
  // wrote comes before it and cannot win a tie.
  var place = function () {
    try {
      var parent = document.documentElement;
      if (!parent) return;
      if (parent.lastChild === style) return;
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
          // everything the guard kept moves across to the new element
          try { if (old[CODES]) found[CODES] = old[CODES]; } catch (e) {}
          try { if (old[OBSERVER]) found[OBSERVER] = old[OBSERVER]; } catch (e) {}
          try { if (old[STOP]) found[STOP] = old[STOP]; } catch (e) {}
          try { if (old[ADD]) found[ADD] = old[ADD]; } catch (e) {}
          try { if (old[PREVENT]) found[PREVENT] = old[PREVENT]; } catch (e) {}
          try { if (old[TOSTRING]) found[TOSTRING] = old[TOSTRING]; } catch (e) {}
          try { if (old[WRAPPED]) found[WRAPPED] = old[WRAPPED]; } catch (e) {}
          try { if (old[SPOOF]) found[SPOOF] = old[SPOOF]; } catch (e) {}
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

  ensure();

  // The originals are kept across runs, so the guard never builds on itself.
  var nativeAdd = null;
  var nativePrevent = null;
  var nativeToString = null;
  try {
    nativeAdd = (style && style[ADD]) ? style[ADD] : EventTarget.prototype.addEventListener;
  } catch (e) {}
  try {
    nativePrevent = (style && style[PREVENT]) ? style[PREVENT] : Event.prototype.preventDefault;
  } catch (e) {}
  try {
    nativeToString = (style && style[TOSTRING]) ? style[TOSTRING] : Function.prototype.toString;
  } catch (e) {}
  if (style) {
    style[ADD] = nativeAdd;
    style[PREVENT] = nativePrevent;
    style[TOSTRING] = nativeToString;
    try { if (style[WRAPPED]) wrapped = style[WRAPPED]; } catch (e) {}
    style[WRAPPED] = wrapped;
  }

  // The events the page must never hear about, and must never be able to
  // cancel.
  var guarded = { copy: 1, beforecopy: 1, cut: 1, beforecut: 1, paste: 1,
                  contextmenu: 1, select: 1, selectstart: 1,
                  selectionchange: 1, dragstart: 1 };

  // A dressed up function that cannot be told apart from the browser's own,
  // not even by the careful way of reading it.
  var nativeLooking = function (name, fn) {
    try {
      nativeDefine.call(Object, fn, 'name', { value: name, configurable: true });
    } catch (e) {}
    try {
      nativeDefine.call(Object, fn, 'toString', {
        value: function () { return 'function ' + name + '() { [native code] }'; },
        writable: true,
        configurable: true
      });
    } catch (e) {}
    wrapped.push(fn);
    return fn;
  };

  try {
    // Function.prototype.toString reads the real source of a function, so the
    // careful way of reading it is dressed up as well.
    var spoof = null;
    try { spoof = style ? style[SPOOF] : null; } catch (e) {}
    if (!spoof && nativeDefine && nativeToString) {
      spoof = function () {
        try {
          if (this === spoof) return 'function toString() { [native code] }';
          for (var i = 0; i < wrapped.length; i++) {
            if (this === wrapped[i]) {
              var name = '';
              try { name = this.name || ''; } catch (e) {}
              return 'function ' + name + '() { [native code] }';
            }
          }
        } catch (e) {}
        return nativeToString.call(this);
      };
      nativeDefine.call(Object, Function.prototype, 'toString', {
        value: spoof, writable: true, enumerable: false, configurable: true
      });
      wrapped.push(spoof);
    }
    if (style && spoof) style[SPOOF] = spoof;
  } catch (e) {}

  try {
    // A page that hides a handler in a shadow tree, where the copy event
    // never reaches the window, is not allowed to register one at all.
    if (nativeAdd && nativeDefine) {
      var patchedAdd = function (type, listener, options) {
        try {
          if (guarded[String(type).toLowerCase()]) return;
        } catch (e) {}
        return nativeAdd.call(this, type, listener, options);
      };
      nativeDefine.call(Object, EventTarget.prototype, 'addEventListener', {
        value: patchedAdd, writable: true, enumerable: true, configurable: true
      });
      wrapped.push(patchedAdd);
    }
  } catch (e) {}

  try {
    // And a handler that is already there cannot cancel the copy either.
    if (nativePrevent && nativeDefine) {
      var patchedPrevent = function () {
        try {
          if (guarded[String(this.type).toLowerCase()]) return;
        } catch (e) {}
        return nativePrevent.call(this);
      };
      nativeDefine.call(Object, Event.prototype, 'preventDefault', {
        value: patchedPrevent, writable: true, enumerable: true, configurable: true
      });
      wrapped.push(patchedPrevent);
    }
  } catch (e) {}

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
      // The capture phase always starts at the window, so one place is
      // enough, and every frame gets the same cover. The original is used,
      // because the guard has taught the page not to register these events.
      var names = [$events];
      for (var n = 0; n < names.length; n++) {
        if (nativeAdd) {
          nativeAdd.call(window, names[n], fn, true);
        } else {
          window.addEventListener(names[n], fn, true);
        }
      }
    } catch (e) {}
  };

  // Inline handlers are taken off, and written down as evidence.
  var takeHandler = function (el) {
    try {
      if (!el || el.nodeType !== 1) return false;
      var hit = false;
      if (el.hasAttribute('oncopy')) { record('oncopy'); el.removeAttribute('oncopy'); hit = true; }
      if (el.hasAttribute('oncut')) { record('oncut'); el.removeAttribute('oncut'); hit = true; }
      if (el.hasAttribute('oncontextmenu')) { record('oncontextmenu'); el.removeAttribute('oncontextmenu'); hit = true; }
      return hit;
    } catch (e) { return false; }
  };

  // The work is done on the node that changed, not on the whole page. The
  // whole subtree is looked at, so a handler further down is found as well.
  var takeHandlers = function (node) {
    try {
      if (!node || node.nodeType !== 1) return;
      takeHandler(node);
      if (!node.querySelectorAll) return;
      var inner = node.querySelectorAll('[oncopy],[oncut],[oncontextmenu]');
      for (var k = 0; k < inner.length; k++) takeHandler(inner[k]);
    } catch (e) {}
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

  // One sweep over the page, for the tricks that were already there.
  var sweep = function () {
    try {
      var nodes = document.querySelectorAll('[oncopy],[oncut],[oncontextmenu]');
      for (var k = 0; k < nodes.length; k++) takeHandler(nodes[k]);
      var styled = document.querySelectorAll('[style]');
      var limit = styled.length < 800 ? styled.length : 800;
      for (var m = 0; m < limit; m++) takeStyle(styled[m]);
    } catch (e) {}
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

  // The page threw the style element away, so it is built again. A page that
  // keeps throwing it away is left to it, after a while.
  var lost = function () {
    var still = false;
    try { still = document.documentElement.contains(style); } catch (e) { still = false; }
    if (still) return;
    rebuilds++;
    if (rebuilds > $MAX_REBUILDS) return;
    ensure();
  };

  try {
    ensure();
    guard();

    // One observer for the whole page. It watches for the tricks and for the
    // moment the page throws our style element away. Only the page the user
    // is looking at is watched; the frames are covered by the events.
    if (TOP && style && window.MutationObserver && document.documentElement) {
      if (!style[OBSERVER]) {
        var observer = new MutationObserver(function (records) {
          try {
            for (var i = 0; i < records.length; i++) {
              var entry = records[i];
              if (entry.target === style) continue;
              if (entry.type === 'childList') {
                if (entry.addedNodes) {
                  for (var a = 0; a < entry.addedNodes.length; a++) {
                    takeHandlers(entry.addedNodes[a]);
                  }
                }
                if (entry.removedNodes) {
                  for (var d = 0; d < entry.removedNodes.length; d++) {
                    if (entry.removedNodes[d] === style) lost();
                  }
                }
                continue;
              }
              if (entry.attributeName === 'style') {
                takeStyle(entry.target);
                continue;
              }
              takeHandlers(entry.target);
            }
          } catch (e) {}
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
    if (!LIGHT && TOP) idle(sweep);
  } catch (e) {}

  try {
    // The official screen capture surfaces are taken away, and locked so the
    // page cannot put them back. The lock goes on the prototype, where the
    // real method lives, so there is no way around it.
    var fail = function (what) {
      try { return Promise.reject(new DOMException(what, 'NotAllowedError')); }
      catch (e) { return Promise.reject(new Error(what)); }
    };

    var lock = function (host, name, value, enumerable) {
      if (!host) return false;
      try {
        if (nativeDefine) {
          nativeDefine.call(Object, host, name, {
            value: value, writable: false, enumerable: !!enumerable, configurable: false
          });
          return true;
        }
      } catch (e) {}
      try { host[name] = value; return true; } catch (e) {}
      return false;
    };

    // The method is looked for on the prototype first, because that is where
    // the browser keeps it. A shadow of its own is locked as well, so there
    // is no way around the lock.
    var lockMethod = function (obj, name, value) {
      if (!obj) return false;
      var proto = null;
      var locked = false;
      try { proto = Object.getPrototypeOf(obj); } catch (e) { proto = null; }
      try {
        if (proto && typeof proto[name] === 'function') {
          locked = lock(proto, name, value, true);
        }
      } catch (e) {}
      try {
        if (Object.prototype.hasOwnProperty.call(obj, name) &&
            typeof obj[name] === 'function') {
          locked = lock(obj, name, value, false) || locked;
        }
      } catch (e) {}
      if (locked) return true;
      try {
        if (typeof obj[name] === 'function') return lock(obj, name, value, false);
      } catch (e) {}
      return false;
    };

    if (navigator.mediaDevices) {
      lockMethod(
        navigator.mediaDevices,
        'getDisplayMedia',
        nativeLooking('getDisplayMedia', function () {
          return fail('Screen capture is not available');
        })
      );
      // A screen can also be asked for through the camera.
      if (navigator.mediaDevices.getUserMedia) {
        var realGetUserMedia = navigator.mediaDevices.getUserMedia;
        lockMethod(
          navigator.mediaDevices,
          'getUserMedia',
          nativeLooking('getUserMedia', function (constraints) {
            try {
              var video = constraints ? constraints.video : null;
              if (video && (video.displaySurface || video.monitorSurfaceSurfaces ||
                            video.surfaceSwitching)) {
                return fail('Screen capture is not available');
              }
            } catch (e) {}
            return realGetUserMedia.call(navigator.mediaDevices, constraints);
          })
        );
      }
    }
    if (window.getScreenDetails) {
      lockMethod(
        window,
        'getScreenDetails',
        nativeLooking('getScreenDetails', function () {
          return fail('Screen capture is not available');
        })
      );
    }
    // A page must not be able to read what the user just copied.
    if (navigator.clipboard) {
      lockMethod(
        navigator.clipboard,
        'readText',
        nativeLooking('readText', function () {
          return fail('Clipboard reading is not available');
        })
      );
      if (navigator.clipboard.read) {
        lockMethod(
          navigator.clipboard,
          'read',
          nativeLooking('read', function () {
            return fail('Clipboard reading is not available');
          })
        );
      }
    }
  } catch (e) {}

  try {
    // The page must not be able to see the browser leave the screen. Every
    // realm gets the mask, so a same origin frame cannot read the truth out
    // of the page that holds it. Each mask is set on its own, so one that
    // cannot be set does not stop the others.
    var docProto = window.Document ? window.Document.prototype : null;
    if (docProto && nativeDefine) {
      try {
        nativeDefine.call(Object, docProto, 'visibilityState', {
          get: function () { return 'visible'; }, configurable: false
        });
      } catch (e) {}
      try {
        nativeDefine.call(Object, docProto, 'hidden', {
          get: function () { return false; }, configurable: false
        });
      } catch (e) {}
      try {
        nativeDefine.call(Object, docProto, 'hasFocus', {
          value: function () { return true; }, writable: true, configurable: false
        });
      } catch (e) {}
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
