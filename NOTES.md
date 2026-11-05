# Handover notes: facebook-lock-in

Last updated: session of 2026-11-05. Read this first. The README is the user guide; this file is the status and decisions.

## What the project is

One product, "Marketplace Lock-In": lock Facebook down to Marketplace (browsing, item pages, search, selling) and the pages needed to log in, recover an account, or read legal/help pages. Everything else is blocked. Blocked screens show a motivational quote and a Marketplace tip. The owner wants it over-zealous: no easy bypass in the app.

Three surfaces:

| Surface | Folder | State |
| --- | --- | --- |
| Chrome / Edge extension (desktop) | `extension/` | Built, tested in Chromium |
| Shared quotes and tips | `shared/motivation.json` → `scripts/generate-motivation.mjs` | Built, generates both platforms |
| Android core library (pure Kotlin/JVM, the rules) | `core/` | Built, 163 JVM tests pass |
| Android app (AccessibilityService, overlay, UI) | `android/` | Source written; compile-checked with kotlinc against framework stubs; NOT built with Gradle or run on a device |
| Phone browsers (Chrome, Firefox, Samsung, etc. on Android) | `android/` (URL-bar reading in the accessibility service) | Same status as the Android app |

Phone web is handled by the Android app reading the URL bar of known browsers. Android Chrome has no extension support, so the extension cannot run on the phone.

## How to run and test

```bash
# Extension
npm install            # installs Playwright (dev dependency only)
npm test               # 19 unit tests for extension/rules.js
npm run e2e            # loads the extension into Chromium; needs root (port 443) and openssl

# Quotes and tips (after editing shared/motivation.json)
node scripts/generate-motivation.mjs

# Core (Android rules, pure Kotlin)
cd core && gradle test # 163 tests
```

Load the extension in Chrome/Edge: `chrome://extensions` → Developer mode → Load unpacked → `extension/`.

## Verified

- Extension: `npm test` 19/19 pass. `npm run e2e` 14/14 pass in Chromium (fake facebook.com over HTTPS, host-mapped; the sandbox proxy is disabled for the test browser).
- Core: `gradle test` 163/163 pass (AppRules 36, FailClosed 3, LockState 1, QuoteRotator 10, TipRotator 8, WebRules 105).
- Extension e2e re-run after the quote change: 14/14 pass.
- Fail-closed on Android: unparseable text that mentions facebook.com, fb.com or messenger.com is locked (closes a raw-space bypass found by the core agent). Covered by `FailClosedTest`.
- Independent code review of the extension (read-only agent) found 3 medium and 5 low issues. All fixed except the ones listed under "Open".

## Not verified

- **No APK was built.** The Android SDK cannot be downloaded here (dl.google.com is blocked by egress policy). Android code has not been compiled against the real SDK.
- **Nothing has run on a phone.** Accessibility events, the overlay, and the URL bar reading are untested on any device or emulator.
- Facebook's own Android screens are not known in detail. Marketplace detection in the Facebook app is a text heuristic (`AppRules.isMarketplaceScreen`) and will be wrong sometimes.

## Key decisions (and why)

- **Browser lock = `declarativeNetRequest` with `urlFilter`, not regex.** Chrome caps each regex rule at 2 KB compiled; the allow-list regex hit that limit. `||facebook.com/marketplace^` uses the `^` separator, so `/marketplacefake` is not allowed.
- **Trailing-dot hosts** (`www.facebook.com.`) are treated as the same host in both the JS check and the DNR rules.
- **Messenger is always blocked**: `facebook.com/messages` and `messenger.com` (and the Messenger app `com.facebook.orca` on Android). Marketplace buyer chats are under `/messages`, so buyers can't be answered through the web. Adding `messages` to `ALLOWED_PATH_PREFIXES` opens every chat.
- **In-app navigation** (Facebook is a single-page app): an isolated content script cannot see the page's `pushState`, so `main-world.js` runs in the page world and dispatches an event. The content script then asks the background worker to move the tab. Extension pages are not web-accessible, so the redirect cannot come from the page itself.
- **Fail closed**: if storage can't be read, the lock stays on. The content script waits for the stored flag before enforcing, so an "off" lock is never briefly enforced.
- **Turning off** needs the exact phrase `I choose distraction`. This is a speed bump, not security: a user can still disable the extension in `chrome://extensions` or uninstall the app.
- **Quotes and tips are generated** from one JSON file (49 quotes, 32 tips), so the extension and Android stay identical. Quotes are original, with no attributions to real people. Eight of the quotes name Marketplace explicitly; the rest are general motivation.
- `extension/motivation.js` is a generated file; the core agent regenerated it too. It is identical to the output of the script, so nothing is out of step.
- **Core library is pure Kotlin** (no Android imports) so its rules can be unit-tested on the JVM.

## Open issues and limits

- Logged-out visitors get the block page on `facebook.com/` (the login form is on the home page). Going to `/marketplace/` redirects to login, so they can still log in. Deliberate tradeoff.
- The block counter counts page loads, so a reload adds one. The label says "so far", not "today".
- Log in with Facebook on other sites (`facebook.com/dialog/oauth`) is blocked. Turn the lock off to use it.
- The allow-list paths (legal/recovery) were chosen from memory. Check the real Facebook URLs before relying on them: e.g. `/legal/`, `/communitystandards/`, `/hacked/` are not allowed.
- The extension does not cover the Facebook mobile app or other browsers.
- Marketplace detection on the Facebook Android app is heuristic.
- Accessibility-based blocking on Android needs the user to grant the Accessibility permission. Google Play review for that permission was not considered.

## Android status

Source is in `android/` (20 files, committed). Verification so far:

- **Compile check (partial, done):** Kotlin 2.1.20 compiler from Maven Central, run against `org.robolectric:android-all` (API 35), compiling `core/` and `android/` together. Exit 0, no errors. The androidx and Material classes are stubs (not on Maven Central), and `R.kt` was generated from the res files by a script, not by aapt.
- **Not done:** Gradle/AGP build, aapt, lint, manifest merge, any run on a device or emulator. There is no Gradle wrapper; README gives the command.
- **Version note:** core and android pin Kotlin 2.1.20 (not 2.0.x), because two Kotlin Gradle plugin versions in one build fail to configure. Reasoned, not tested with Gradle.
- **Behaviour choices:** "Go to Marketplace" opens `https://www.facebook.com/marketplace/` inside the Facebook app (a bare launch intent lands on the feed, which would be blocked again), with fallbacks to the Facebook app launch intent, then the browser. The overlay is focusable so the hardware back key goes to "Go back". Dark theme is in `values-night/themes.xml`. Tips and quotes come from core only; no quotes are hard-coded in Android.
- **lastTip:** kept in `LockPrefs` (key `last_tip`, default -1), written with `lastQuote` by `recordBlock`. It is not a field of `LockState`.

**Needs a real device before it can be called working:**
- Whether the Accessibility event fires on each Facebook screen, and whether Marketplace is detected. The "starts with Marketplace" heuristic may block item pages.
- URL-bar text per browser and per phone maker. Chrome may show only the host in its simplified URL.
- Overlay behaviour: touch blocking, the back key, re-showing on navigation, removal on leaving the app.
- Gradle sync with JDK 21 and core's `jvmToolchain(21)`.
- Google Play policy for accessibility use (not reviewed).

## Next steps

1. Get the Android app compiled. Options: install the SDK via a route the egress policy allows, or use a CI runner with network access (`./gradlew assembleDebug`).
2. Run the Android app on a device or emulator. Check: the Facebook app is blocked off Marketplace; Marketplace search works; the browser URL bar is read on Chrome and at least one other browser; the overlay blocks touches; turning off and on works.
3. Check the Marketplace heuristic against real Facebook screens and adjust `AppRules.isMarketplaceScreen`.
4. Confirm the allowed legal and recovery URLs on facebook.com.
5. Decide whether to allow `/messages` for Marketplace buyers.
6. Commit the repo (no commit has been made since the initial README scaffold). Use the session's attribution trailers.
7. Pushed to https://github.com/Bearfoot1/FacebookLockIn (branch main). Keep pushing from this local repo; remote: origin.

## Files

```
extension/        Chrome/Edge extension (load unpacked)
shared/           motivation.json (quotes and tips, single source)
scripts/          generate-motivation.mjs
core/             Kotlin library: WebRules, AppRules, QuoteRotator, TipRotator, Motivation (generated), LockState
android/          Android app source (unverified build)
tests/            rules.test.mjs (unit), e2e.mjs (Chromium)
README.md         user guide
NOTES.md          this file
```
