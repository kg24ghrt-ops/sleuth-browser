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
- Tabs with a switcher sheet, back/forward per tab, home, reload
- History and bookmarks with instant filtering
- Downloads through the system download manager
- Find on page with in-page highlighting
- Share, desktop mode, per-site blocking, incognito (cookies wiped on toggle)
- Deep links: `http`/`https` links open straight in Sleuth
- File chooser support, popup windows, `mailto:`/`tel:` handed to the system
- Dark pages, text zoom, tracker blocking, mixed content control

### Pages cannot lock the browser down

Copy blocks, selection blocks, hidden print stylesheets — the browser does not accept them.

- The unlock script is injected **before the page runs**, so a site never gets the chance to put
  the lock on: `copy`, `cut`, `paste`, `contextmenu`, `selectstart` and `select` events are stopped
  in the capture phase, inline `oncopy`/`oncut`/`oncontextmenu` handlers are removed as they appear,
  and `user-select: none` is overruled with `!important`.
- **Before every capture the page is put back into a readable state** and given a frame to repaint,
  so a capture is never taken of a page that just hid itself.
- **A flat picture is treated as a failure.** If the capture comes back as one single colour, the
  page is rendered again by hand and only a picture with real content is filed as evidence.
- Every trick a page uses is **detected and filed**: when a case is armed, "this page blocked
  copying" is written into the case as evidence, and a small shield appears next to the address bar.
- There is **no bridge into the page** — the browser never exposes an object to the site, so a
  hostile page cannot call back into the app or fill the case file with noise.

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

1. It never talks to you. No assistant, no chat, no voice, no narration, no upsell.
2. It never asks questions it can answer itself. Cases are created with a code, not a form.
3. Everything you type is a destination or a search. `"hey sleuth, who is on my wifi"` becomes a
   plain web search — it is never answered.
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
