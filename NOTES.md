# Handover notes: facebook-lock-in

Last updated: 2026-11-05 (phone test session). Read this first. The README is the user guide; this file is the status and decisions.

## What the project is

One product, "Marketplace Lock-In": lock Facebook down to Marketplace (browsing, item pages, search, selling) and the pages needed to log in, recover an account, or read legal/help pages. Everything else is blocked. Blocked screens show a motivational quote and a Marketplace tip. The owner wants it over-zealous: no easy bypass in the app.

Three surfaces:

| Surface | Folder | State |
| --- | --- | --- |
| Chrome / Edge extension (desktop) | `extension/` | Built, tested in Chromium |
| Shared quotes and tips | `shared/motivation.json` → `scripts/generate-motivation.mjs` | Built, generates both platforms |
| Android core library (pure Kotlin/JVM, the rules) | `core/` | Built, 173 JVM tests pass |
| Android app (AccessibilityService, overlay, UI) | `android/` | Built with Gradle; tested on a real phone (see "Phone test") |
| Phone browsers (Chrome, Firefox, Samsung, etc. on Android) | `android/` (URL-bar reading in the accessibility service) | Chrome and Samsung Internet tested; Firefox not yet |

## Phone test (2026-11-05)

Galaxy S9+ (SM-G965U1), Android 10, debug APK installed over adb. BlockSite and ReelBlock accessibility services were also on.

| Check | Result |
| --- | --- |
| Main screen, service status | Works |
| Facebook app: Home feed, Reels tab | Blocked |
| Block screen "Go to Marketplace" | Opens Marketplace in the Facebook app, overlay goes away |
| Marketplace tab, scrolling | Allowed |
| Marketplace item page (no tab bar) | Allowed when opened from the Marketplace tab |
| Reopening Facebook straight onto an item page | Blocked (last tab is forgotten on leaving Facebook); Go to Marketplace recovers |
| Messenger app | Blocked |
| Samsung Internet, any Facebook page | Allowed (host-only URL bar, owner's choice, see decisions) |
| Chrome: `m.facebook.com/watch/`, `www.facebook.com/` | Blocked (Chrome shows the full path) |
| Chrome: `www.facebook.com/marketplace/` | Allowed |
| Marketplace search, search results, result items | Allowed |
| Sell (Selling page), Marketplace chat inbox | Allowed |
| Lock off with "I choose distraction", back on | Works |

Bugs found and fixed in that test:

- **Every Facebook screen counted as Marketplace.** The bottom tab bar is always on screen with the description "Marketplace, tab 4 of 6" (plus ", 1 new" sometimes), which matched the text heuristic. Now the selected tab decides (`AppRules.tabLabel`, `selectedTab`). Screens without a tab bar follow the last selected tab (`lastSelectedTab`), which is reset when another app comes to the front.
- **The overlay never went away.** It takes focus (for the back key), so `rootInActiveWindow` was the overlay and the Facebook window under it was never checked again. The service now reads the topmost `TYPE_APPLICATION` window (`flagRetrieveInteractiveWindows`, `typeWindowsChanged`).
- **"Go to Marketplace" left the overlay up.** It now hides the overlay before opening Marketplace; the service re-shows it if the screen is still blocked.
- **Marketplace search was blocked.** Its "Recent, tab 1 of 2" strip was taken for the main tab bar. Only a tab group that contains a Marketplace tab counts now (`AppRules.selectedMainTab`).
- **Chrome page not blocked behind a popup.** Chrome's "Open tabs" tip bubble is its own app window on top; only the topmost window was read, it had no URL bar, so the page failed open. The service now reads every window of the front app.
- **Samsung Internet URL bar text starts with U+200E** (left-to-right mark). The URL failed to parse and was allowed. `WebRules` now strips Unicode format characters, and host-less input that mentions Facebook fails closed.
- Build fixes: the accessibility flags lacked the `flag` prefix (aapt error); core declared the Kotlin plugin version a second time (fails inside the android build, so the version moved to `core/settings.gradle.kts`); Java and Kotlin JVM targets differed (now 17 everywhere, `jvmToolchain(17)`).

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
cd core && gradle test # 173 tests

# Android app (JDK 17; android/local.properties needs sdk.dir)
cd android && ./gradlew :core:test :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Load the extension in Chrome/Edge: `chrome://extensions` → Developer mode → Load unpacked → `extension/`.

## Verified

- Extension: `npm test` 19/19 pass. `npm run e2e` 14/14 pass in Chromium (fake facebook.com over HTTPS, host-mapped; the sandbox proxy is disabled for the test browser).
- Core: `gradle test` 163/163 pass (AppRules 36, FailClosed 3, LockState 1, QuoteRotator 10, TipRotator 8, WebRules 105).
- Extension e2e re-run after the quote change: 14/14 pass.
- Fail-closed on Android: unparseable text that mentions facebook.com, fb.com or messenger.com is locked (closes a raw-space bypass found by the core agent). Covered by `FailClosedTest`.
- Independent code review of the extension (read-only agent) found 3 medium and 5 low issues. All fixed except the ones listed under "Open".

## Not verified

- Firefox and other phone browsers.
- Marketplace screens reached from other routes (notifications, shared links).
- Other phones, other Android versions, other Facebook app versions. The tab bar description format ("X, tab N of M") is from one phone.

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
- **Facebook app: the selected bottom tab decides.** A screen with no tab bar (item pages, full-screen posts) follows the last tab seen selected, and that memory is cleared when another app comes to the front. The old text heuristic is only the last fallback.
- **Browser URL bars that show only a host are allowed** (owner's choice, 2026-11-05). Samsung Internet shows "m.facebook.com" on every Facebook page, so the page can't be judged. The alternative, blocking every Facebook page in that browser, was turned down. This means the full Facebook site is reachable in Samsung Internet.

## Open issues and limits

- Logged-out visitors get the block page on `facebook.com/` (the login form is on the home page). Going to `/marketplace/` redirects to login, so they can still log in. Deliberate tradeoff.
- The block counter counts page loads, so a reload adds one. The label says "so far", not "today".
- Log in with Facebook on other sites (`facebook.com/dialog/oauth`) is blocked. Turn the lock off to use it.
- The allow-list paths (legal/recovery) were chosen from memory. Check the real Facebook URLs before relying on them: e.g. `/legal/`, `/communitystandards/`, `/hacked/` are not allowed.
- The extension does not cover the Facebook mobile app or other browsers.
- Marketplace detection on the Facebook Android app relies on the tab bar (see decisions).
- Accessibility-based blocking on Android needs the user to grant the Accessibility permission. Google Play review for that permission was not considered.
- Samsung Internet (and any browser that hides the path) is an open way to the whole Facebook site, by choice.
- A notification that opens a Facebook post while the last tab was Marketplace (the shade does not reset the last tab) would be allowed.

## Android status

Builds with Gradle 8.11.1, AGP 8.7.3, Kotlin 2.1.20, JDK 17 (`./gradlew :app:assembleDebug` from `android/`). The Gradle wrapper is committed. `android/local.properties` (not committed) needs `sdk.dir`. Tested on a phone: see "Phone test".

- **Behaviour choices:** "Go to Marketplace" opens `https://www.facebook.com/marketplace/` inside the Facebook app (a bare launch intent lands on the feed, which would be blocked again), with fallbacks to the Facebook app launch intent, then the browser. The overlay is focusable so the hardware back key goes to "Go back". Dark theme is in `values-night/themes.xml`. Tips and quotes come from core only; no quotes are hard-coded in Android.
- **lastTip:** kept in `LockPrefs` (key `last_tip`, default -1), written with `lastQuote` by `recordBlock`. It is not a field of `LockState`.
- **Debug log:** the service logs each Facebook decision under the tag `LockIn` (`adb logcat -s LockIn`): package, selected tab, last tab, blocked. No screen text is logged.
- Google Play policy for accessibility use is not reviewed.

## Next steps

1. Test Firefox URL bar reading.
2. Test opening a Marketplace item from a notification or shared link.
3. Confirm the allowed legal and recovery URLs on facebook.com.
4. Decide whether to allow `/messages` for Marketplace buyers on the web. (In the Facebook app, the Marketplace chat inbox is reachable from Selling > Inbox and allowed.)
5. Repo: https://github.com/Bearfoot1/FacebookLockIn (branch main, remote origin). Local clone on the owner's PC: `Documents\GitHub\FacebookLockIn`.

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
