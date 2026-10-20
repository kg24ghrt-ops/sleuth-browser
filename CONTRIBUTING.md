# Contributing to Sleuth

Thanks for helping out. Sleuth is a small Kotlin/Android app and it stays that way on purpose.

## Ground rules

1. **Sleuth never talks.** No assistant, no chat, no voice, no tutorial popups, no "did you
   mean". If a feature would require the app to address the user, find a silent way to do it.
2. **The record stays local.** No telemetry, no remote services, no third party SDKs.
3. **Plain Kotlin.** No clever frameworks, no reflection, no annotation processors. The app must
   build on a clean runner with nothing but the wrapper.

## Workflow

```bash
git checkout -b feature/short-description
./gradlew testDebugUnitTest lintDebug assembleDebug
git commit -am "Short description of the change"
git push origin feature/short-description
```

CI runs the unit tests, lint and both build types for every pull request, and it uploads the APKs
as artifacts. A failing lint check blocks the merge.

## Before you open a pull request

- Add or update unit tests for anything in `browser/`, `data/` or `ui/ReportExporter.kt` that is
  pure logic. Those are plain JVM tests — no device needed.
- Keep the dark noir palette (`res/values/colors.xml`) and the Material 3 theme.
- Do not add a permission without explaining it in the pull request body.

## Reporting bugs

Open an issue with the device model, Android version, what you did, what you expected and what
happened instead. If it involves a specific site, say which one — WebView behaviour differs a lot
between sites.

## Code of conduct

Be decent. See [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
