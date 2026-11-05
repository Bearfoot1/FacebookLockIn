# Marketplace Lock-In

A Chrome / Edge extension that locks **facebook.com** down to Facebook Marketplace.

When it is on, the only parts of Facebook you can use are:

- **Marketplace**: browsing, item pages, selling, and Marketplace search (`/marketplace/...`)
- **Log-in pages**: `/login`, `/login.php`, `/checkpoint`, `/recover`, `/two_step_verification`
- **Legal and help pages**: `/help`, `/policies`, `/privacy`, `/terms`, `/cookies`

Everything else on facebook.com is blocked, including the home feed, Reels, Watch, Groups, Messenger, search, and profiles. In-app navigation (Facebook's single-page routing) is caught too, not just full page loads.

When something is blocked you get a block page with:

- a motivational quote (never the same one twice in a row)
- a button to open Marketplace
- a Marketplace search box
- a running count of blocked attempts

## Install (Chrome or Edge)

1. Open `chrome://extensions` (or `edge://extensions`).
2. Turn on **Developer mode**.
3. Click **Load unpacked** and choose the `extension/` folder in this repo.
4. Pin **Marketplace Lock-In** from the puzzle-piece menu if you want the popup one click away.

## Turning it off

Open the popup, click **Turn the lock off**, and type exactly `I choose distraction`. The confirm button stays disabled until the phrase matches. Turn it back on from the same popup.

## How it works

| Piece | File | Job |
| --- | --- | --- |
| Rules | `extension/rules.js` | The allowlist, the quotes, and the Chrome rule builder. No Chrome APIs, so it is unit tested in Node. |
| Network rules | `extension/background.js` | Installs `declarativeNetRequest` rules. Every main-frame load to facebook.com is redirected to the block page, except allowed paths. |
| In-app navigation | `extension/main-world.js`, `extension/content.js` | Announces `pushState` / `replaceState` from the page, then moves the tab to the block page if the new route is locked. |
| Block page | `extension/blocked.html`, `blocked.js`, `blocked.css` | Quote, Marketplace button and search, and the counter. |
| Popup | `extension/popup.html`, `popup.js` | Shows the state, opens Marketplace, and handles on/off with the typed confirmation. |

The allow rules are one `urlFilter` per prefix, for example `||facebook.com/marketplace^`. The `^` separator means `/marketplacefake` is not allowed. Chrome limits regex rules to 2 KB each, which is why `urlFilter` is used.

Privacy: nothing leaves your browser. The extension makes no network requests of its own, has no analytics, and stores only `enabled`, `blockCount` and `lastQuote` in `chrome.storage.local`.

## Known limits

- **Not the Facebook mobile app.** Chrome extensions only control the browser. The app on a phone is not covered.
- **Turning off the extension in `chrome://extensions` still works.** A browser extension cannot stop its own user from disabling it. The typed confirmation is a speed bump, not a lock.
- **Messenger is blocked everywhere**, on `facebook.com/messages` and on `messenger.com`. Marketplace buyer chats live under `/messages`, so you cannot reply to buyers through the web. If you need that, add `messages` to `ALLOWED_PATH_PREFIXES` in `extension/rules.js`. That also opens every other chat.
- **Logged-out visitors get the block page on `facebook.com/`**, because the login form is on the home page. Going to Marketplace still works, and it redirects to the login page.
- **Log in with Facebook on other sites is blocked** because it uses `facebook.com/dialog/oauth`. Turn the lock off while you do that.
- **Only the browser is covered.** Other browsers and profiles need the extension installed separately.

## Tests

```bash
npm install          # installs Playwright (the browser itself is already on this machine)
npm test             # unit tests for the rules: allowlist, DNR rules, quotes
npm run e2e          # loads the extension into Chromium and drives a fake facebook.com
```

`npm run e2e` binds port 443 on 127.0.0.1 and maps facebook.com to it, so it needs root or equivalent. It also needs `openssl`. Facebook is HSTS-preloaded, so the fake server uses HTTPS with a throwaway self-signed certificate.

## Layout

```
extension/          the Chrome/Edge extension (load this folder unpacked)
tests/rules.test.mjs  unit tests (node:test)
tests/e2e.mjs         end-to-end tests (Playwright + Chromium)
```
