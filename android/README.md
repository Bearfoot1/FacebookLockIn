# Marketplace Lock-In (Android)

Locks the Facebook app and browsers on the phone down to Facebook Marketplace.
Allowed: Marketplace (browsing, item pages, search, selling) and the log-in,
account recovery, help and legal pages. Blocked: the feed, Reels, Watch, Groups,
Messenger (always), search, profiles. A blocked screen shows a full-screen overlay
with a quote and a tip that send you back to Marketplace.

The phone-side part is an AccessibilityService. It reads the text of Facebook
screens and the URL bar of browsers. Nothing is sent off the phone: there are no
network calls, no analytics, and no permissions other than Accessibility.

## Requirements

- Android Studio Ladybug (2024.2) or newer, or any Gradle 8.9+ with the Android SDK
- JDK 21 (the `core` library sets `jvmToolchain(21)`; Android Studio's bundled JDK 21 works)
- Android SDK Platform 35 and Build-Tools for AGP 8.7.3
- A phone on Android 8.0 (API 26) or newer

## Build in Android Studio

1. Open the `android/` folder (not the repo root) with *File > Open*.
2. Let Gradle sync. It also uses the `../core` library, so keep the repo layout intact.
3. Run the `app` configuration on a device, or *Build > Build APK(s)*.

Command line (needs a Gradle wrapper, which is not committed yet):

```bash
cd android
gradle wrapper --gradle-version 8.9   # once, with a local Gradle 8.9+
./gradlew :app:assembleDebug
```

The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Install and enable

1. Install the APK (or run from Android Studio).
2. Open **Marketplace Lock-In**. The screen shows the lock status and the blocked count.
3. Tap **Turn on accessibility service**. In Settings, open the service named
   *Marketplace Lock-In* and turn it on. Accept the prompt.
4. Return to the app. It should say *Accessibility service: on*.

The lock works only while the accessibility service is on. Turning it off in Settings
disables the lock. The app shows this on its main screen.

## Turn the lock off (and back on)

- Tap **Turn the lock off**, type exactly `I choose distraction`, then tap
  **Confirm**. The confirm button stays disabled until the text matches (spaces at
  the ends are ignored).
- Tap **Turn the lock back on** at any time.

## Known limits

- **Accessibility permission is required**, and Android shows it as a powerful
  permission. Anyone with access to Settings can turn the service off. The app
  does not defend against that.
- **Marketplace detection in the Facebook app is a heuristic.** A screen counts as
  Marketplace when a visible text starts with the word "Marketplace". Item pages
  and some Marketplace screens may not show that word, so they could be blocked.
  Check this on a real device with your own account and report what you see.
- **Blank or loading screens** of the Facebook app are blocked until their text appears.
- **URL bars differ by browser and OEM.** Chrome, Firefox and Samsung Internet use
  known view ids; other browsers use a scan for an editable URL-like node. If the URL bar
  cannot be read (new tab page, hidden URL bar while scrolling, unusual browser), the page is
  treated as allowed.
- **Chrome may show only the host** (for example `facebook.com`) in its address bar
  with some settings. That blocks Facebook home and Marketplace on the web alike.
- **Facebook in-app browser** pages are judged by the Facebook app's text, not by URL.
- **Only the listed packages are watched:** `com.facebook.katana`, `com.facebook.lite`,
  `com.facebook.orca`, `com.facebook.pages.app`, and the browsers in `AppRules`.
  Other Meta apps and browsers are not covered.
- **No Google Play policy review has been done.** Accessibility services that read
  screen content are restricted by Google Play and need a declaration and review
  before a public release. This build is for personal sideloading.
- Nothing here has been run on a device yet (see the report for what was checked).

## Layout

```
android/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/app/lockin/android/
    LockAccessibilityService.kt   reads screens, decides, shows or hides the overlay
    BlockOverlay.kt               full-screen overlay (TYPE_ACCESSIBILITY_OVERLAY)
    LockPrefs.kt                  SharedPreferences: enabled, blockCount, lastQuote, lastTip
    MarketplaceLauncher.kt        opens Marketplace (Facebook app, then browser)
    MainActivity.kt               status, accessibility check, turn off and on
  app/src/main/res/               layouts, strings, colors, themes, dimens, accessibility config
```

Quotes and tips come from `app.lockin.core` (`QuoteRotator`, `TipRotator`). Nothing is
hard-coded in the Android code.
