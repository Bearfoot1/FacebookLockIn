# Handover notes: facebook-lock-in

Last updated: 2026-11-17 (bug hunt). Read this first. The README is the user guide; this file is the status and decisions.

## What the project is

One product, "Marketplace Lock-In": lock Facebook down to Marketplace (browsing, item pages, search, selling) and the pages needed to log in, recover an account, or read legal/help pages. Everything else is blocked. Blocked screens show a motivational quote and a Marketplace tip. The owner wants it over-zealous: no easy bypass in the app.

Three surfaces:

| Surface | Folder | State |
| --- | --- | --- |
| Chrome / Edge extension (desktop) | `extension/` | Built, tested in Chromium |
| Shared quotes and tips | `shared/motivation.json` → `scripts/generate-motivation.mjs` | Built, generates both platforms |
| Android core library (pure Kotlin/JVM, the rules) | `core/` | Built, 190 JVM tests pass |
| Android app (AccessibilityService, overlay, UI) | `android/` | Built with Gradle; tested on a real phone (see "Phone test") |
| Phone browsers (Chrome, Firefox, Samsung, etc. on Android) | `android/` (URL-bar reading in the accessibility service) | Chrome and Samsung Internet tested; Firefox not yet |

## Bug hunt (2026-11-17)

Three review passes (commits 662062d, 468f69d and the one after). Fixed, not yet phone-tested:

- Redirect apps: cooldown retry (only for the focused full-screen app, never PiP); uninstalled/unsafe apps don't use free slots; rows show Redirecting / Paused · Pro only / Can't redirect / Not installed; browsers, home screen and dialer are never redirected (RedirectTargets).
- Picture-in-picture windows are skipped when finding the front app (a floating video used to leave Facebook unjudged).
- Tab bar parsing accepts any language ("Marketplace, pestaña 4 de 6"); the text fallback ignores any "Marketplace, ..." entry, so an unrecognised tab format blocks instead of allowing every screen.
- Browsers: an unreadable URL bar (hidden while scrolling) or a focused one (user typing) keeps the current state instead of hiding the overlay. Any app that opens https links is judged as a browser (Kiwi, Firefox Nightly...). Throttled content changes get a trailing recheck.
- Allowed: logout.php and l.php (outbound link shim; its target is judged on load), in both WebRules.kt and rules.js.
- Extension: background tabs.onUpdated backstop for in-app navigation, plus a sweep of open Facebook tabs on install/startup.
- Reverted: clearing the last tab on Messenger (blocked Message seller -> Back). Known gap kept: a reel/post link opened from Messenger inherits the Marketplace tab.

Round 4 (same day): Facebook wrapper apps (Friendly, Frost, Folio, plus any non-Meta app that opens facebook.com links but not every link) are always blocked; logged as "facebook wrappers: [...]" under LockIn, so check that list on a real phone. The last tab only counts in the app it was seen in. Main screen says NOT PROTECTED when the lock is on but the service is off; service detection accepts the short component name. Backups and phone-to-phone transfer copy nothing (data_extraction_rules.xml). Extension popup warns when Incognito or facebook.com site access isn't allowed (Chrome won't let an extension force either); blocked.html is web-accessible so link-click redirects show the block page.

Not done, needs a phone first: Facebook Lite's tab labels are unknown, so Lite still uses the loose text check (any plain "Marketplace" text allows the screen). Split screen judges only the top app. The block screen ignores system-bar insets (3-button nav may cover the bottom).

Known, not fixed: lapsed Pro is only noticed when the main screen opens (billing not live yet); Chrome Custom Tabs show host only, so they're allowed like Samsung Internet; NOTES' Phone test says the overlay is focusable, but it uses FLAG_NOT_FOCUSABLE now.

## Redirect apps (2026-11-16)

Owner's request: send other distracting apps to Marketplace. The main screen has a "Redirect to Marketplace" card:

- **Common apps**: one switch per app in `RedirectRules.commonApps` (Instagram, TikTok, YouTube, Snapchat, X, Reddit, Threads, Pinterest, Twitch, Netflix, Discord, Tumblr). Only installed ones are listed. All off by default. TikTok has two packages; its switch covers both.
- **Custom apps**: "Add an app" lists launchable apps (`<queries>` MAIN/LAUNCHER in the manifest, not QUERY_ALL_PACKAGES). Added apps get a Remove button.
- Stored as one string set, `LockPrefs.redirectPackages` (key `redirect_packages`).
- **Behaviour**: while the lock is on, a redirect app coming to the front (window state or windows change, never a content change, so a picture-in-picture window can't loop) gets a toast and `MarketplaceLauncher.open`. At most once per 2 s per app. Each redirect adds one to the blocked count. Logged as `redirect <pkg> to Marketplace` under `LockIn`.
- **Never redirect apps** (`RedirectRules.canRedirect`): Facebook apps, browsers (the Marketplace fallback would loop), Settings, System UI, the package installer.
- Toggles and the ✕ (remove, custom apps only) need no confirmation phrase, unlike turning the whole lock off. Custom apps are kept in `custom_packages` so switching one off does not delete it; apps from the first redirect build are folded in on read.
- This is the one place Lock-In acts outside Facebook, and only on apps the owner picked. The 2026-11-06 "only act on Facebook" decision was about browsers judging other sites.
- Tested on the `rbtest` emulator (Android 15, no Facebook app): YouTube switch and a custom app (Calendar) both redirect to Marketplace (Chrome fallback). Not tested on the S9+ yet.

## Lock-In Pro, themes, icon (2026-11-16)

Owner: "For more than 5 redirects add a subscription of 3$ also add any other premium features like other themes. Also make a dope icon black and yellow."

- **Free:** up to 5 redirect apps switched on at once (`RedirectRules.FREE_REDIRECT_LIMIT`; TikTok's two packages count as one app, `redirectUnits`). A sixth toggle snaps back and opens the upgrade screen; a sixth app from the picker is added switched off. The counter reads "3/5 on" without Pro, "3 on" with it.
- **Pro ($3/month, assumed monthly):** unlimited redirects + 6 themes (Blood, Toxic, Ice, Venom, Gold, Ghost). Hazard (yellow) stays free.
- **Lapsed Pro:** the service only honours the first 5 apps (`RedirectRules.activeRedirects`, common apps first, then custom sorted); the theme falls back to Hazard (`LockTheme.current`). Nothing the owner set is deleted.
- **Billing:** `BillingManager`, Play Billing 7.1.1, subscription product id **`lockin_pro_monthly`**. Pro lives in `LockPrefs.pro`; set true on a purchased sub (acknowledged automatically), set false only when Play answers OK with no active sub, so offline never removes Pro. Price on the buttons comes from Play; "$3" until Play answers.
- **NOT live yet. To sell it:** publish the app to Google Play (an internal testing track is enough to test purchases), create the subscription `lockin_pro_monthly` at $2.99 or $3.00/month in the Play Console, add license testers. A sideloaded build shows "Google Play billing isn't available on this install". Play's review of the AccessibilityService use (prominent disclosure, policy declaration) is still open and will be the main hurdle.
- **Debug builds only:** the upgrade screen has "Unlock Pro (debug build only)" / "Lock Pro again" (`LockPrefs.devPro`, never revoked by billing). Tapping the PRO badge opens that screen in debug, and Play's subscription page in release.
- **Themes:** accent is the theme attribute `lockinAccent` (attrs.xml); every layout, drawable and style uses `?attr/lockinAccent` / `?attr/lockinOnAccent`, `Theme.LockIn.<Name>` overlays set it. MainActivity calls `setTheme` before `super.onCreate` and recreates on change; the block overlay is inflated with the current theme.
- **Icon:** adaptive (`mipmap-anydpi-v26/ic_launcher(_round).xml`): near-black background with a yellow hazard-stripe band, square-shouldered yellow padlock with a black lightning bolt (`ic_launcher_foreground.xml`, scaled 0.62 into the safe zone), plus a monochrome layer for Android 13 themed icons.
- Verified on the emulator: locked swatches → upgrade screen, Go Pro on a sideloaded build → "billing not available" toast, debug unlock → PRO badge and "N on", Blood/Toxic themes on the main and block screens, lock Pro again → back to Hazard and "2/5 on", 6th app → upgrade screen, icon in the app drawer. Core tests: 188 pass.

## Sharp redesign and Go to Marketplace fix (2026-11-16)

Owner: "the interface looks terrible", wanted toggles and an intense, sharp, motivational look. Also: Go to Marketplace "stalls and doesn't work if I leave the screen then come back".

- **Look:** no rounded corners anywhere (theme shape overlays, 0dp button radius, square cards with hard 2-3dp edges), `HazardStripeView` (yellow/black diagonal stripes) on the main screen and block screen, condensed bold type, square custom toggles (`switch_track`/`switch_thumb`, tints nulled so Material3 doesn't recolour them). Main screen: LIVE light, huge status, a rotating quote (tap for another), "distractions crushed" score, **Lock-In mode** master toggle (yellow edge when on, red when off), Go to Marketplace, REDIRECTS list with toggle rows. Main screen has `fitsSystemWindows` (Android 15 draws edge to edge).
- **Master toggle:** on is instant; flipping it off snaps it back and opens the "Quitting already?" phrase card. The old "Turn the lock off" text button is gone.
- **Go to Marketplace bug (cause, from reading the code):** when Facebook is already running, the Marketplace link only brings Facebook's current screen (the feed) to the front, the service sees a blocked tab and re-shows the overlay at once. Affects the main-screen button, the overlay button and redirects.
- **Fix:** `MarketplaceLauncher.open` starts a 4 s grace window (`recentlyRequested()`). During it a blocked Facebook screen is not overlaid; instead the service clicks the bottom-bar node labelled "Marketplace, tab N of M" (or its clickable parent, at most every 700 ms). Reaching an allowed screen ends the window (`arrived()`); a recheck runs when it ends, so a still-blocked screen gets the overlay. Logged as `tapped the Marketplace tab`.
- Verified on the emulator: layout, toggles, phrase card, block screen look, Go to Marketplace from the block screen in a warm Chrome. **Not verified:** the tab-tap fix inside the real Facebook app (needs the S9+).

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
| Messenger app | Blocked (before 2026-11-16; now allowed unless the toggle is on) |
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
- **Messenger app (Android) is allowed by default** (owner's request, 2026-11-16: "I don't want it to block messenger app automatically"). A "Block Messenger" toggle on the main screen (`LockPrefs.blockMessenger`, off by default) blocks `com.facebook.orca` on every screen; `AppRules.isBlockedAppScreen(..., blockMessenger)`. An allowed Messenger does not end the Marketplace grace window. **Messenger on the web is never blocked** (owner, 2026-11-16: "Dont block the browser version of messenger at all"): `messages` and `messenger` are allowed path prefixes in both `WebRules` and `extension/rules.js`; messenger.com is no longer in the extension's DNR block rules or the Android fail-closed host check. The Block Messenger toggle covers the app only. Marketplace buyer chats are under `/messages`, so buyers can't be answered through the web. Adding `messages` to `ALLOWED_PATH_PREFIXES` opens every chat.
- **In-app navigation** (Facebook is a single-page app): an isolated content script cannot see the page's `pushState`, so `main-world.js` runs in the page world and dispatches an event. The content script then asks the background worker to move the tab. Extension pages are not web-accessible, so the redirect cannot come from the page itself.
- **Fail closed**: if storage can't be read, the lock stays on. The content script waits for the stored flag before enforcing, so an "off" lock is never briefly enforced.
- **Turning off** needs the exact phrase `I choose distraction`. This is a speed bump, not security: a user can still disable the extension in `chrome://extensions` or uninstall the app.
- **Headlines, quotes and tips are generated** from one JSON file (16 headlines, 49 quotes, 32 tips), so the extension and Android stay identical. The extension does not show headlines yet. Voice (owner's choice, 2026-11-05): bold hustle and money, short and punchy. All original, no attributions to real people.
- `extension/motivation.js` is a generated file; the core agent regenerated it too. It is identical to the output of the script, so nothing is out of step.
- **Core library is pure Kotlin** (no Android imports) so its rules can be unit-tested on the JVM.
- **Facebook app: the selected bottom tab decides.** A screen with no tab bar (item pages, full-screen posts) follows the last tab seen selected, and that memory is cleared when another app comes to the front. The old text heuristic is only the last fallback.
- **Lock-In only ever acts on Facebook** (owner's request, 2026-11-06). In browsers it judges only text whose own host is facebook.com (or fb.com / messenger.com when the text is malformed). Searches or other sites that merely mention facebook.com are never blocked; before, malformed address-bar text that contained "facebook.com" anywhere was blocked, and the owner saw a block in Chrome while visiting another site.
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
- **Look (owner's choice, 2026-11-05): bold.** Always dark (no light theme): near-black `ink`, white text, electric-yellow `accent`, red `danger`. Block screen: random headline, accent bar, quote, seller-tip card, block count, big yellow Go to Marketplace. Main screen: LOCKED IN. / UNLOCKED. headline, blocked-count card, running dot. The turn-off card makes Stay locked in the big button.
- Google Play policy for accessibility use is not reviewed.

## Next steps

00. To charge for Pro: Play Console listing, internal testing track, subscription `lockin_pro_monthly` ($3/month), license testers, accessibility policy declaration. Then a release build signed with an upload key (none exists yet).
0. On the S9+ with the real Facebook app: open Facebook on the feed, leave, come back, press Go to Marketplace (block screen and main screen); check `adb logcat -s LockIn` for "tapped the Marketplace tab". Same for a redirect app.
1. Test Firefox URL bar reading.
2. Test opening a Marketplace item from a notification or shared link.
3. Confirm the allowed legal and recovery URLs on facebook.com.
4. ~~Decide whether to allow `/messages` on the web~~ Done 2026-11-16: allowed. (In the Facebook app, the Marketplace chat inbox is reachable from Selling > Inbox and allowed.)
5. Repo: https://github.com/Bearfoot1/FacebookLockIn (branch main, remote origin). Local clone on the owner's PC: `Documents\GitHub\FacebookLockIn`.

## Files

```
extension/        Chrome/Edge extension (load unpacked)
shared/           motivation.json (quotes and tips, single source)
scripts/          generate-motivation.mjs
core/             Kotlin library: WebRules, AppRules, RedirectRules, QuoteRotator, TipRotator, Motivation (generated), LockState
android/          Android app source (unverified build)
tests/            rules.test.mjs (unit), e2e.mjs (Chromium)
README.md         user guide
NOTES.md          this file
```
