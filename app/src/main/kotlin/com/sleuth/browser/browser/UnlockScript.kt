package com.sleuth.browser.browser

/**
 * The scripts that keep a hostile page from locking the browser down.
 *
 * Pages block copying with inline handlers, with capture phase listeners and
 * with `user-select: none`, and they hide themselves with print stylesheets.
 * The unlock script is injected before the page runs so the page never gets
 * the chance to put the lock on in the first place.
 */
object UnlockScript {

    /** Injected at document start, and again whenever a capture is taken. */
    val UNLOCK: String = """
(function(){
  try {
    var css = '*{-webkit-user-select:text !important;user-select:text !important;' +
              '-webkit-touch-callout:default !important;}';
    var style = document.getElementById('__sleuth_unlock');
    if (!style) {
      style = document.createElement('style');
      style.id = '__sleuth_unlock';
      style.type = 'text/css';
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = css;
  } catch (e) {}

  try {
    if (!window.__sleuthUnlocked) {
      window.__sleuthUnlocked = true;
      var stop = function (event) { event.stopImmediatePropagation(); };
      var names = ['copy','cut','paste','contextmenu','selectstart','select'];
      for (var i = 0; i < names.length; i++) {
        document.addEventListener(names[i], stop, true);
      }
      var timer = null;
      var scrub = function () {
        if (timer) { clearTimeout(timer); timer = null; }
        timer = setTimeout(function () {
          timer = null;
          try {
            var nodes = document.querySelectorAll('[oncopy],[oncut],[oncontextmenu]');
            for (var n = 0; n < nodes.length; n++) {
              nodes[n].removeAttribute('oncopy');
              nodes[n].removeAttribute('oncut');
              nodes[n].removeAttribute('oncontextmenu');
            }
          } catch (e) {}
        }, 250);
      };
      window.__sleuthScrub = scrub;
      scrub();
      if (window.MutationObserver && document.documentElement) {
        var observer = new MutationObserver(function () { scrub(); });
        observer.observe(document.documentElement, {
          childList: true,
          subtree: true,
          attributes: true,
          attributeFilter: ['oncopy','oncut','oncontextmenu']
        });
      }
      document.addEventListener('DOMContentLoaded', function () {
        try {
          var style = document.getElementById('__sleuth_unlock');
          if (style) { style.textContent = css; } else {
            style = document.createElement('style');
            style.id = '__sleuth_unlock';
            style.type = 'text/css';
            style.textContent = css;
            (document.head || document.documentElement).appendChild(style);
          }
        } catch (e) {}
        scrub();
      });
    }
  } catch (e) {}
})();
""".trimIndent()

    /** Puts the page back into a readable state right before a capture. */
    val REASSERT: String = """
(function(){
  try {
    if (window.__sleuthScrub) { window.__sleuthScrub(); }
    var style = document.getElementById('__sleuth_unlock');
    if (!style) {
      style = document.createElement('style');
      style.id = '__sleuth_unlock';
      style.type = 'text/css';
      (document.head || document.documentElement).appendChild(style);
    }
    style.textContent = '*{-webkit-user-select:text !important;user-select:text !important;' +
                        '-webkit-touch-callout:default !important;}';
  } catch (e) {}
})();
""".trimIndent()

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
}
