<div align="center">

<img src="docs/assets/sleuth-mark.svg" width="96" height="96" alt="Sleuth logo" />

# Sleuth — a browser that never talks back

**An Android WebView browser with real screenshots, a full evidence lab and a local case system.**
Sleuth browses like any other browser. What makes it different is what it can *record* — and the
fact that it will never start a conversation with you.

[![CI](https://github.com/kg24ghrt-ops/sleuth-browser/actions/workflows/ci.yml/badge.svg)](https://github.com/kg24ghrt-ops/sleuth-browser/actions/workflows/ci.yml)
[![Release](https://github.com/kg24ghrt-ops/sleuth-browser/actions/workflows/release.yml/badge.svg)](https://github.com/kg24ghrt-ops/sleuth-browser/actions/workflows/release.yml)
[![CodeQL](https://github.com/kg24ghrt-ops/sleuth-browser/actions/workflows/codeql.yml/badge.svg)](https://github.com/kg24ghrt-ops/sleuth-browser/actions/workflows/codeql.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-amber.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-purple.svg)](https://kotlinlang.org)
[![Min SDK](https://img.shields.io/badge/minSdk-24-green.svg)](https://developer.android.com)
[![Target SDK](https://img.shields.io/badge/targetSdk-36-green.svg)](https://developer.android.com)

</div>

---

## The idea

Most mobile browsers let you take a screenshot. Sleuth lets you **work a case**.

You browse with a real Android WebView — tabs, history, bookmarks, downloads, incognito, find on
page, desktop mode, share. Then you open a **case file**, arm it, and Sleuth starts writing the
record down silently: every page visited, its text, every capture you take. When you are done you
mark up the captures, file them as evidence and export a self contained report.

**Sleuth never talks.** There is no assistant, no chat, no voice, no help bubble, no "did you
mean". Anything you type is either a place to go or a thing to look up. Conversation attempts are
silently rewritten into searches. See [Detective's rules](#detectives-rules).

## Features

### It is a real browser
- Omnibox that understands URLs, search phrases and `localhost:port`
- Six search engines, switchable (DuckDuckGo, Brave, Google, Bing, Startpage, Wikipedia)
- **Say where to go** — the microphone button turns speech into the same query a typed one would be
- Tabs with a switcher sheet, back/forward per tab, home, reload
- History and bookmarks with instant filtering
- Downloads through the system download manager
- Find on page with in-page highlighting
- Share, desktop mode, per-site blocking, incognito (cookies wiped on toggle)
- Deep links: `http`/`https` links open straight in Sleuth
- File chooser support, popup windows, `mailto:`/`tel:` handed to the system
- Dark pages, text zoom, tracker blocking, mixed content control
- Lite mode in the menu: drop images, video, audio and fonts, and pages load faster
- The address bar shows the short form of the address, the whole one when you tap it, with a clear
  button and a one-tap way to speak a destination

### Pages cannot lock the browser down

Copy blocks, selection blocks, hidden print stylesheets — the browser does not accept them.

**And the user is in charge of it.** The guard can be switched off for everybody, or for one site
only, so a page that misbehaves under it never gets in the way of ordinary browsing. When the guard
stands down the shield next to the address bar shows it, and a tap puts the guard back.

- **Off for everybody** — one switch in the menu, or in the settings. The page in front is loaded
  again, so the change is not a promise but a fact.
- **Off for one site** — the same switch, per site. The site is written into the guard itself, so it
  is left alone the moment the guard lands on it, and every page under it is left alone too.
- **Nothing changes behind your back** — the guard only ever stands over the page you are looking
  at, and the switch in front of you always shows what is happening.

- The unlock script is injected **before the page runs**, so a site never gets the chance to put
  the lock on: `copy`, `beforecopy`, `cut`, `beforecut`, `paste`, `contextmenu`, `selectstart`,
  `select`, `selectionchange` and `dragstart` events are stopped in the capture phase, inline
  `oncopy`/`oncut`/`oncontextmenu` handlers are removed as they appear, and `user-select: none` is
  overruled with `!important` — with a rule heavy enough that a page rule of the same weight cannot
  outrank it, and placed last so a tie is won here too.
- **A page that fights back is handled too.** The original event and property tools are taken
  before the page can change them, the capture surfaces are locked shut so they cannot be put
  back, the unlock rule is moved to the end of the document so a page rule of the same weight
  cannot win, and the style element is rebuilt if the page throws it away — up to a point, after
  which a page that is deliberately throwing it away is left to it.
- **Before every capture the page is put back into a readable state** and given a frame to repaint,
  so a capture is never taken of a page that just hid itself. The run before a capture is a light
  one: it puts the lock back and does not go looking for tricks again.
- **A flat picture is treated as a failure.** If the capture comes back as one single colour, the
  page is rendered again by hand and only a picture with real content is filed as evidence.
- Every trick a page uses is **detected and filed**: when a case is armed, "this page blocked
  copying" is written into the case as evidence, and a small shield appears next to the address bar.
- There is **no bridge into the page** — the browser never exposes an object to the site, so a
  hostile page cannot call back into the app or fill the case file with noise.

### The page cannot see what the browser is doing

A page that logs `visibilitychange`, focus and clipboard events can tell when a browser took a
picture or copied something. Sleuth takes those pages blind.

- `visibilitychange`, `focus`, `blur`, `pagehide`, `pageshow`, `freeze`, `resume`, `beforeunload`
  and `unload` are stopped in the capture phase, so the page never hears that the app came to the
  front, went to the back, opened the evidence lab or took a picture.
- The browser does not go dark in the page either: `document.visibilityState` always reads
  `visible` and `document.hasFocus()` always says yes, so a page cannot watch the app walk away —
  and the masks cannot be taken off again by the page.
- The copy, cut, paste and selection events are stopped the same way, so a page cannot log what was
  copied, and it cannot peek at the clipboard afterwards either — `navigator.clipboard.readText`
  and `navigator.clipboard.read` are refused.
- The official surfaces for detecting or performing a screen capture are taken away and locked:
  `navigator.mediaDevices.getDisplayMedia` and `window.getScreenDetails` (the screen capture
  detection API) are replaced with a refusal the page cannot remove, and a screen cannot be asked
  for through the camera either — `getUserMedia` still works for a camera, but a request for a
  display surface is turned down. The refusals are dressed up to look like the browser's own
  functions, so a page cannot tell them apart by reading them.
- **A page full of frames does not pay for the whole guard in every frame.** The frames only get
  the rules about the text; the watcher that hunts for tricks and the masks that hide the app
  stay in the page the user is actually looking at.
- **The injected script leaves nothing obvious behind:** the marker is a javascript property on the
  style element, not an id, a data attribute or a global name, it has no fixed name to look for,
  and it is different on every run.

The one trade off: a page that copies to the clipboard itself, with `execCommand('copy')`, loses
that ability — the browser cannot tell the difference between the page copying and the user
copying, and the user always wins.

### It takes evidence seriously- **Viewport capture** with `PixelCopy` (hardware accurate, API 26+) and a software renderer fallback
- **Full page capture** that stitches the whole document together while you watch
- **Evidence lab**: pen, marker, box, circle, arrow, text labels and true pixel redaction
- Undo, clear, four ink colours, and export to gallery, to a case file, or straight to another app
- Captures are written to the app's private storage, never to a shared album unless you ask

### The detective system
- **Cases** with a generated case code (`CASE-0001`), a title, notes and a stakeout switch
- **Arming** a case makes Sleuth record every page load, its title and its text
- **Evidence types**: screenshots, filed pages, excerpts and notes
- **Timeline** of everything that happened in the case
- **Export** a case as a self contained HTML report (images inlined as base64) or as JSON
- Everything stays on the device: no account, no sync, no upload

### It loads pages fast

- **Engine warm up** — the browser engine is started while the app is still opening, so the first page
  is asked for on a running engine
- **Lite mode** — skips images, video, audio and fonts while keeping the documents, scripts and
  styles that make a page work
- **Nothing blocks the browser thread** — history, cases, evidence, thumbnails, image compression and
  report building all run on one background thread
- **Old loads are dropped** — a new navigation stops the download that is still running
- **Text never travels whole** — page text is cut inside the page, not on the way to the app
- **Background tabs sleep** — pages stop running when the app is not in front
- **Only a tap opens a tab** — a script cannot spawn windows
- **Switching to desktop mode no longer rebuilds every tab** — only the page in front is reloaded
- **Release builds are shrunk and optimised** (R8 plus resource shrinking)

## Detective's rules

Sleuth is a tool, not a companion.

1. It never talks to you. No assistant, no chat, no narration, no upsell. It does not speak
   aloud either — it only listens when you press the microphone button, and what it hears is a
   destination or a search, never a conversation.
2. It never asks questions it can answer itself. Cases are created with a code, not a form.
3. Everything you type or say is a destination or a search. `"hey sleuth, who is on my wifi"`
   becomes a plain web search — it is never answered.
4. Feedback is silent and reversible. Toasts and status lines, never modal nags.
5. Nothing leaves the device unless you export it yourself.

## Install

Grab a release build from the [releases page](https://github.com/kg24ghrt-ops/sleuth-browser/releases)
or let CI build it for you. Debug builds are also published as workflow artifacts on every push.

Minimum Android version is **7.0 (API 24)**; it is built and tested against **API 36**.

## Build it yourself

```bash
git clone https://github.com/kg24ghrt-ops/sleuth-browser.git
cd sleuth-browser
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # JVM unit tests
./gradlew lintDebug              # Android lint
```

Requirements: JDK 17, Android SDK platform 36, build tools 36.0.0. The Gradle wrapper (8.14.4)
pulls everything else, and CI does exactly the same on a clean runner.

### Signed releases

The release build falls back to the debug key when no keystore is configured, so CI never breaks.
For a real signing key, create `keystore.properties` (git ignored):

```properties
RELEASE_STORE_FILE=/path/to/release.jks
RELEASE_STORE_PASSWORD=...
RELEASE_KEY_ALIAS=sleuth
RELEASE_KEY_PASSWORD=...
```

## CI/CD

| Workflow | Trigger | What it does |
| --- | --- | --- |
| [`ci.yml`](.github/workflows/ci.yml) | push, pull request | Unit tests, lint, debug + release APKs, artifacts |
| [`release.yml`](.github/workflows/release.yml) | tag `v*` | Publishes a GitHub release with the APK attached |
| [`nightly.yml`](.github/workflows/nightly.yml) | nightly cron | Fresh build + tests, artifact retained for 7 days |
| [`codeql.yml`](.github/workflows/codeql.yml) | push, cron | CodeQL scan of the Kotlin sources |
| [`docs.yml`](.github/workflows/docs.yml) | push to main | Publishes the site in `docs/` to GitHub Pages |

Dependabot keeps Gradle and the actions up to date.

## Project layout

```
app/src/main/kotlin/com/sleuth/browser/
├── MainActivity.kt            the browser itself
├── Sleuth.kt                  shared singletons
├── browser/                   URL rules, search engines, WebView factory, capture, storage
├── data/                      SQLite record: cases, evidence, history, bookmarks, events
└── ui/                        case screens, evidence lab, history, settings, report exporter
app/src/test/kotlin/           JVM unit tests for the pure logic
```

The parts worth reading first are `browser/UrlNormalizer.kt`, `browser/SilentGuard.kt`,
`data/SleuthDb.kt` and `ui/AnnotationView.kt`.

## Privacy

- No analytics, no telemetry, no crash reporting, no third party SDKs.
- Tracker blocking is a local denylist, not a remote service.
- Captures live in app private storage; only explicit exports leave the app.
- Incognito skips history and case recording and wipes cookies.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md). Issues and ideas are welcome — pull requests are better.

## License

[MIT](LICENSE) © Sleuth contributors.
