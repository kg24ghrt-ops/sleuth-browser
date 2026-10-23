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
 * where it cannot be outranked, and the style element is rebuilt if the page
 * throws it away.
 *
 * The guard tries hard not to be seen. Its rule is put in an adopted style
 * sheet, so it does not show up among the page's own style sheets; its notes
 * are kept under symbols, so nothing readable is left on anything; and every
 * thing it touches keeps the shape the browser gave it, so a page that looks
 * for a patched browser finds nothing out of the ordinary.
 *
 * The guard also stays out of the way of the page as much as it can. It only
 * looks at the parts of the page that changed, the first sweep waits for a
 * quiet moment, and a page that keeps throwing the style element away is
 * left to do it rather than being fought over and over again.
 */
object UnlockScript {

    /** A name that does not give the game away, and is different on every run. */
    private val token: String = UUID.randomUUID().toString().replace("-", "").substring(0, 12)

    /** The keys the guard keeps its notes under. Symbols, so nothing is readable. */
    private const val MARK_KEY = "m"
    private const val CODES_KEY = "c"
    private const val OBSERVER_KEY = "o"
    private const val STOP_KEY = "s"
    private const val ADD_KEY = "a"
    private const val PREVENT_KEY = "v"
    private const val TOSTRING_KEY = "t"
    private const val WRAPPED_KEY = "w"
    private const val SPOOF_KEY = "f"
    private const val TEXT_KEY = "x"

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
     * Reports what the page is doing to protect itself. The answer is a short
     * list of codes, one per trick. The tricks the guard caught are read back
     * from the page, and the rest is looked for in the page's own stylesheets.
     */
    val PROBE: String = buildProbe()

    /**
     * The script for a set of sites that are allowed to protect themselves.
     * The sites are written into the script, so the guard stands down on its
     * own the moment it lands on one of them.
     */
    fun forBypassed(bypassed: Set<String>, light: Boolean = false): String {
        val last = cached
        if (last != null && last.light == light && last.hosts == bypassed) {
            return last.script
        }
        val script = buildScript(bypassed, light)
        cached = Cached(bypassed, light, script)
        return script
    }

    /** The last script that was built, so the same one is not built twice. */
    private class Cached(val hosts: Set<String>, val light: Boolean, val script: String)

    private var cached: Cached? = null

    private fun symbol(suffix: String): String = "__${token}__$suffix"

    private fun buildScript(bypassed: Set<String>, light: Boolean): String {
        val events = hiddenEvents.joinToString(",") { "'$it'" }
        val hosts = bypassed.sorted().joinToString(",") { "'${it.replace("'", "")}'" }
        return """
(function(){
  // Everything the guard keeps its notes under is a symbol with a name that
  // is different on every run, so there is nothing to read off the page.
  var MARK = Symbol.for('${symbol(MARK_KEY)}');
  var CODES = Symbol.for('${symbol(CODES_KEY)}');
  var OBSERVER = Symbol.for('${symbol(OBSERVER_KEY)}');
  var STOP = Symbol.for('${symbol(STOP_KEY)}');
  var ADD = Symbol.for('${symbol(ADD_KEY)}');
  var PREVENT = Symbol.for('${symbol(PREVENT_KEY)}');
  var TOSTRING = Symbol.for('${symbol(TOSTRING_KEY)}');
  var WRAPPED = Symbol.for('${symbol(WRAPPED_KEY)}');
  var SPOOF = Symbol.for('${symbol(SPOOF_KEY)}');
  var TEXT = Symbol.for('${symbol(TEXT_KEY)}');
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
  // outrank it.
  var css = 'html,html body,html body *,:root body *,:root:root body *{-webkit-user-select:text !important;' +
            'user-select:text !important;-webkit-touch-callout:default !important;}';

  var state = null;
  var sheet = null;
  var element = null;
  var rebuilds = 0;
  var wrapped = [];

  // The state is the adopted style sheet when the browser has them, and a
  // style element when it has not. Both carry the notes under symbols.
  var findState = function () {
    try {
      // the sheet only counts while the page still has it
      if (sheet && sheet[MARK] && document.adoptedStyleSheets.indexOf(sheet) !== -1) {
        return sheet;
      }
    } catch (e) {}
    try {
      var list = document.adoptedStyleSheets;
      for (var i = 0; list && i < list.length; i++) {
        if (list[i] && list[i][MARK]) { sheet = list[i]; return sheet; }
      }
    } catch (e) {}
    try {
      var styles = document.getElementsByTagName('style');
      for (var j = 0; j < styles.length; j++) {
        if (styles[j][MARK]) { element = styles[j]; return styles[j]; }
      }
    } catch (e) {}
    return null;
  };

  var record = function (code) {
    try {
      if (!state) return;
      var current = state[CODES] || '';
      var parts = String(current).split(',');
      for (var i = 0; i < parts.length; i++) {
        if (parts[i] === code) return;
      }
      state[CODES] = current ? current + ',' + code : code;
    } catch (e) {}
  };

  // The rule goes into an adopted style sheet, so it does not show up among
  // the page's own style sheets. A style element is only used when the
  // browser has no adopted sheets, and it goes to the very end of the
  // document, so every rule the page wrote comes before it.
  var build = function () {
    var old = state;
    var made = null;
      try {
        if (window.CSSStyleSheet && document.adoptedStyleSheets) {
          var fresh = new CSSStyleSheet();
          fresh[MARK] = true;
          fresh.replaceSync(css);
          var list = document.adoptedStyleSheets;
          var next = [];
          for (var i = 0; list && i < list.length; i++) {
            // an older sheet of ours goes, so there is only ever one
            if (list[i] && list[i][MARK]) continue;
            next.push(list[i]);
          }
          next.push(fresh);
          document.adoptedStyleSheets = next;
          sheet = fresh;
          made = fresh;
        }
      } catch (e) { made = null; }
    if (!made) {
      try {
        var el = document.createElement('style');
        el.type = 'text/css';
        el[MARK] = true;
        el.textContent = css;
        (document.head || document.documentElement).appendChild(el);
        element = el;
        made = el;
      } catch (e) { made = null; }
    }
    if (!made) return null;
    if (old) {
      // everything the guard kept moves across to the new state
      try { if (old[CODES]) made[CODES] = old[CODES]; } catch (e) {}
      try { if (old[OBSERVER]) made[OBSERVER] = old[OBSERVER]; } catch (e) {}
      try { if (old[STOP]) made[STOP] = old[STOP]; } catch (e) {}
      try { if (old[ADD]) made[ADD] = old[ADD]; } catch (e) {}
      try { if (old[PREVENT]) made[PREVENT] = old[PREVENT]; } catch (e) {}
      try { if (old[TOSTRING]) made[TOSTRING] = old[TOSTRING]; } catch (e) {}
      try { if (old[WRAPPED]) made[WRAPPED] = old[WRAPPED]; } catch (e) {}
      try { if (old[SPOOF]) made[SPOOF] = old[SPOOF]; } catch (e) {}
    }
    state = made;
    return made;
  };

  // Finds the state, or builds a new one when the page threw it away.
  var ensure = function () {
    var found = findState();
    if (!found) {
      found = build();
      if (!found) return null;
    }
    state = found;
    try {
      if (state[TEXT] !== css) {
        state[TEXT] = css;
        if (sheet) {
          sheet.replaceSync(css);
        } else if (element) {
          if (element.textContent !== css) element.textContent = css;
          var parent = document.documentElement;
          if (parent && parent.lastChild !== element) parent.appendChild(element);
        }
      }
    } catch (e) {}
    return state;
  };

  ensure();

  // The originals are kept across runs, so the guard never builds on itself.
  var nativeAdd = null;
  var nativePrevent = null;
  var nativeToString = null;
  try {
    nativeAdd = (state && state[ADD]) ? state[ADD] : EventTarget.prototype.addEventListener;
  } catch (e) {}
  try {
    nativePrevent = (state && state[PREVENT]) ? state[PREVENT] : Event.prototype.preventDefault;
  } catch (e) {}
  try {
    nativeToString = (state && state[TOSTRING]) ? state[TOSTRING] : Function.prototype.toString;
  } catch (e) {}
  if (state) {
    state[ADD] = nativeAdd;
    state[PREVENT] = nativePrevent;
    state[TOSTRING] = nativeToString;
    try { if (state[WRAPPED]) wrapped = state[WRAPPED]; } catch (e) {}
    state[WRAPPED] = wrapped;
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
    try { spoof = state ? state[SPOOF] : null; } catch (e) {}
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
    if (state && spoof) state[SPOOF] = spoof;
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
      nativeDefine.call(Object, patchedAdd, 'name', { value: 'addEventListener', configurable: true });
      nativeDefine.call(Object, EventTarget.prototype, 'addEventListener', {
        value: patchedAdd, writable: true, enumerable: true, configurable: true
      });
      wrapped.push(patchedAdd);
    }
  } catch (e) {}

  try {
    // And a handler that is already there cannot cancel the copy either. Only
    // a real event is stopped, so a page that pokes at the event with one of
    // its own sees nothing out of the ordinary.
    if (nativePrevent && nativeDefine) {
      var patchedPrevent = function () {
        try {
          if (guarded[String(this.type).toLowerCase()] && this.isTrusted) return;
        } catch (e) {}
        return nativePrevent.call(this);
      };
      nativeDefine.call(Object, patchedPrevent, 'name', { value: 'preventDefault', configurable: true });
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
      var fn = (state && state[STOP]) ? state[STOP] : stop;
      if (state) state[STOP] = fn;
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

  // The page threw the rule away, so it is built again. A page that keeps
  // throwing it away is left to it, after a while.
  var lost = function () {
    var still = false;
    try {
      if (sheet) {
        still = (document.adoptedStyleSheets.indexOf(sheet) !== -1);
      } else if (state) {
        still = document.documentElement.contains(state);
      }
    } catch (e) { still = false; }
    if (still) return;
    rebuilds++;
    if (rebuilds > $MAX_REBUILDS) return;
    ensure();
  };

  try {
    ensure();
    guard();

    // One observer for the whole page. It watches for the tricks and for the
    // moment the page throws the rule away. Only the page the user is
    // looking at is watched; the frames are covered by the events.
    if (TOP && state && window.MutationObserver && document.documentElement) {
      if (!state[OBSERVER]) {
        var observer = new MutationObserver(function (records) {
          try {
            // the page may have dropped the rule without touching the page
            if (sheet && document.adoptedStyleSheets.indexOf(sheet) === -1) lost();
            for (var i = 0; i < records.length; i++) {
              var entry = records[i];
              if (entry.target === state) continue;
              if (entry.type === 'childList') {
                if (entry.addedNodes) {
                  for (var a = 0; a < entry.addedNodes.length; a++) {
                    takeHandlers(entry.addedNodes[a]);
                  }
                }
                if (entry.removedNodes) {
                  for (var d = 0; d < entry.removedNodes.length; d++) {
                    if (entry.removedNodes[d] === state) lost();
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
        state[OBSERVER] = observer;
      }
    }

    // A light run only puts the lock back, it does not go looking for tricks.
    if (!LIGHT && TOP) idle(sweep);
  } catch (e) {}

try {
      // The page must not know it is being driven. The webdriver flag is the
      // first thing a detection script checks, so it goes away first.
      if (nativeDefine) {
        try {
          nativeDefine.call(Object, navigator, 'webdriver', {
            get: nativeLooking('get webdriver', function () { return false; }),
            set: undefined, enumerable: true, configurable: true
          });
        } catch (e) {}
      }

      // A page that looks for window.chrome finds a realistic object. The
      // real browser keeps chrome.app, chrome.runtime, and chrome.webstore.
      if (nativeDefine && !window.chrome) {
        try {
          var chrome = { app: {}, runtime: {}, webstore: {} };
          nativeDefine.call(Object, window, 'chrome', {
            value: chrome, writable: true, enumerable: true, configurable: true
          });
        } catch (e) {}
      }

      // The permissions API can reveal automation. The guard makes the query
      // look like the page has not asked, and hides the 'automation' state.
      if (navigator.permissions && nativeDefine) {
        try {
          var realQuery = navigator.permissions.query;
          var patchedQuery = nativeLooking('query', function (permission) {
            try {
              if (permission && permission.name === 'automation') {
                return Promise.resolve({ state: 'prompt', onchange: null });
              }
            } catch (e) {}
            return realQuery.call(navigator.permissions, permission);
          });
          nativeDefine.call(Object, navigator.permissions, 'query', {
            value: patchedQuery, writable: true, enumerable: true, configurable: true
          });
          wrapped.push(patchedQuery);
        } catch (e) {}
      }

      // Headless browsers often have empty plugins and mimeTypes. A real
      // browser has at least a few entries. The guard seeds them with
      // plausible defaults when they are missing.
      if (nativeDefine) {
        try {
          if (!navigator.plugins || navigator.plugins.length === 0) {
            var fakePlugins = [
              { name: 'Chrome PDF Plugin', filename: 'internal-pdf-viewer', description: 'Portable Document Format' },
              { name: 'Chrome PDF Viewer', filename: 'mhjfbmdgcfjbbpaeojofohoefgiehjai', description: '' },
              { name: 'Native Client', filename: 'internal-nacl-plugin', description: '' }
            ];
            nativeDefine.call(Object, navigator, 'plugins', {
              value: fakePlugins, writable: true, enumerable: true, configurable: true
            });
          }
        } catch (e) {}
        try {
          if (!navigator.mimeTypes || navigator.mimeTypes.length === 0) {
            var fakeMimes = [
              { type: 'application/pdf', suffixes: 'pdf', description: 'Portable Document Format' },
              { type: 'application/x-google-chrome-pdf', suffixes: 'pdf', description: 'Portable Document Format' }
            ];
            nativeDefine.call(Object, navigator, 'mimeTypes', {
              value: fakeMimes, writable: true, enumerable: true, configurable: true
            });
          }
        } catch (e) {}
      }

      // The official screen capture surfaces are taken away, and locked so the
      // page cannot put them back. The lock goes on the prototype, where the
      // real method lives, so there is no way around it.
      var fail = function (what) {
        try { return Promise.reject(new DOMException(what, 'NotAllowedError')); }
        catch (e) { return Promise.reject(new Error(what)); }
      };

      // The shape the browser gave the method is kept, so a page that looks at
      // the shape finds nothing out of the ordinary. The original stays in the
      // guard, so a page cannot get it back by writing its own.
      var lock = function (host, name, value) {
      if (!host) return false;
      try {
        if (nativeDefine) {
          nativeDefine.call(Object, host, name, {
            value: value, writable: true, enumerable: true, configurable: true
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
          locked = lock(proto, name, value);
        }
      } catch (e) {}
      try {
        if (Object.prototype.hasOwnProperty.call(obj, name) &&
            typeof obj[name] === 'function') {
          locked = lock(obj, name, value) || locked;
        }
      } catch (e) {}
      if (locked) return true;
      try {
        if (typeof obj[name] === 'function') return lock(obj, name, value);
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
    // of the page that holds it. The shape the browser gave the property is
    // kept, and the truth is out of reach anyway, because the mask is in
    // every realm there is.
    var docProto = window.Document ? window.Document.prototype : null;
    if (docProto && nativeDefine) {
      try {
        var visible = nativeLooking('get visibilityState', function () { return 'visible'; });
        nativeDefine.call(Object, docProto, 'visibilityState', {
          get: visible, set: undefined, enumerable: true, configurable: true
        });
      } catch (e) {}
      try {
        var hidden = nativeLooking('get hidden', function () { return false; });
        nativeDefine.call(Object, docProto, 'hidden', {
          get: hidden, set: undefined, enumerable: true, configurable: true
        });
      } catch (e) {}
      try {
        var focus = nativeLooking('hasFocus', function () { return true; });
        nativeDefine.call(Object, docProto, 'hasFocus', {
          value: focus, writable: true, enumerable: true, configurable: true
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
  var MARK = Symbol.for('${symbol(MARK_KEY)}');
  var CODES = Symbol.for('${symbol(CODES_KEY)}');
  var states = [];
  try {
    var list = document.adoptedStyleSheets;
    for (var i = 0; list && i < list.length; i++) { if (list[i]) states.push(list[i]); }
  } catch (e) {}
  try {
    var styles = document.getElementsByTagName('style');
    for (var j = 0; j < styles.length; j++) { states.push(styles[j]); }
  } catch (e) {}
  try {
    // The tricks the guard caught while the page was running.
    for (var s = 0; s < states.length; s++) {
      var recorded = states[s][CODES];
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
