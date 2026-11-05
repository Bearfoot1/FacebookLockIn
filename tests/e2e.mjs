// End-to-end test: loads the unpacked extension into real Chromium and drives a fake facebook.com.
// Run with: npm run e2e   (needs root or permission to bind port 443, and openssl)
import { chromium } from 'playwright';
import https from 'node:https';
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import assert from 'node:assert/strict';

const here = path.dirname(fileURLToPath(import.meta.url));
const extensionDir = path.join(here, '..', 'extension');
const EXECUTABLE = '/opt/pw-browsers/chromium-1243/chrome-linux64/chrome';

const tmp = mkdtempSync(path.join(os.tmpdir(), 'mli-e2e-'));
const keyPath = path.join(tmp, 'key.pem');
const certPath = path.join(tmp, 'cert.pem');
execFileSync('openssl', [
  'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '1',
  '-keyout', keyPath, '-out', certPath, '-subj', '/CN=facebook.com',
  '-addext', 'subjectAltName=DNS:facebook.com,DNS:*.facebook.com'
], { stdio: 'ignore' });

// Fake Facebook pages. Each page has a marker so tests can tell which one was served.
const pages = {
  '/': 'FAKE-FEED',
  '/marketplace/': 'FAKE-MARKETPLACE',
  '/marketplace/search/': 'FAKE-MARKETPLACE-SEARCH',
  '/marketplace/item/1/': 'FAKE-MARKETPLACE-ITEM',
  '/reel/1': 'FAKE-REEL',
  '/login/': 'FAKE-LOGIN',
  '/groups/x': 'FAKE-GROUP',
  '/marketplace/spa': 'FAKE-SPA-START',
  '/marketplace/spa-page': 'FAKE-SPA-START'
};
const spaScript = `<script>
  // Simulates Facebook's in-app navigation: pushState to a locked route.
  if (location.pathname === '/marketplace/spa') {
    setTimeout(function () { history.pushState({}, '', '/groups/x'); }, 300);
  }
</script>`;

const server = https.createServer(
  { key: readFileSync(keyPath), cert: readFileSync(certPath) },
  (req, res) => {
    const url = new URL(req.url, 'https://www.facebook.com');
    const marker = pages[url.pathname];
    if (marker === undefined) {
      res.writeHead(404, { 'content-type': 'text/plain' });
      res.end('not found');
      return;
    }
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' });
    res.end(`<!doctype html><html><body><p>${marker}</p>${spaScript}</body></html>`);
  }
);

await new Promise((resolve, reject) => {
  server.once('error', reject);
  server.listen(443, '127.0.0.1', resolve);
});

const profile = mkdtempSync(path.join(os.tmpdir(), 'mli-profile-'));
const context = await chromium.launchPersistentContext(profile, {
  executablePath: EXECUTABLE,
  headless: true,
  ignoreHTTPSErrors: true,
  args: [
    `--disable-extensions-except=${extensionDir}`,
    `--load-extension=${extensionDir}`,
    '--host-resolver-rules=MAP facebook.com 127.0.0.1:443, MAP *.facebook.com 127.0.0.1:443',
    '--ignore-certificate-errors',
    // Talk to the local fake server directly, not through any system proxy.
    '--no-proxy-server'
  ],
  // Playwright passes its own proxy env; force direct connections for the test browser.
  env: { ...process.env, NO_PROXY: '*', no_proxy: '*', HTTPS_PROXY: '', https_proxy: '', HTTP_PROXY: '', http_proxy: '' }
});

let failures = 0;
const results = [];
async function check(name, fn) {
  try {
    await fn();
    results.push(`PASS  ${name}`);
  } catch (err) {
    failures++;
    results.push(`FAIL  ${name}\n      ${err.message.split('\n').join('\n      ')}`);
  }
}

try {
  // Wait for the background worker, then find the extension id.
  let [worker] = context.serviceWorkers();
  if (!worker) worker = await context.waitForEvent('serviceworker', { timeout: 20000 });
  const extensionId = new URL(worker.url()).host;
  const blockedUrl = `chrome-extension://${extensionId}/blocked.html`;

  // Give the background worker a moment to install the DNR rules.
  await new Promise((r) => setTimeout(r, 800));

  const page = await context.newPage();

  async function goto(url) {
    await page.goto(url, { waitUntil: 'domcontentloaded' });
    await page.waitForLoadState('load').catch(() => {});
    // Give content scripts and SPA redirects a moment.
    await page.waitForTimeout(700);
    return page;
  }

  await check('home feed is blocked and shows the block page', async () => {
    await goto('https://www.facebook.com/');
    assert.ok(page.url().startsWith(blockedUrl), `expected block page, got ${page.url()}`);
  });

  await check('block page shows a motivational quote', async () => {
    const quote = (await page.textContent('#quote')).trim();
    assert.ok(quote.startsWith('“') && quote.endsWith('”'), `bad quote: ${quote}`);
    assert.ok(quote.length > 10, 'quote too short');
  });

  await check('block page shows a tip', async () => {
    const tip = (await page.textContent('#tip')).trim();
    assert.ok(tip.length > 10, `bad tip: ${tip}`);
    assert.notEqual(tip, '\u2026', 'tip was never filled in');
  });

  await check('block counter increments on each block', async () => {
    const before = Number(await page.textContent('#count'));
    await goto('https://www.facebook.com/reel/1');
    assert.ok(page.url().startsWith(blockedUrl), 'reel should be blocked');
    const after = Number(await page.textContent('#count'));
    assert.equal(after, before + 1);
  });

  await check('marketplace home is allowed', async () => {
    await goto('https://www.facebook.com/marketplace/');
    assert.equal(await page.textContent('p'), 'FAKE-MARKETPLACE');
    assert.ok(page.url().startsWith('https://www.facebook.com/marketplace/'));
  });

  await check('marketplace item page is allowed', async () => {
    await goto('https://www.facebook.com/marketplace/item/1/');
    assert.equal(await page.textContent('p'), 'FAKE-MARKETPLACE-ITEM');
  });

  await check('marketplace search is allowed', async () => {
    await goto('https://www.facebook.com/marketplace/search/?query=bike');
    assert.equal(await page.textContent('p'), 'FAKE-MARKETPLACE-SEARCH');
  });

  await check('login page is allowed', async () => {
    await goto('https://www.facebook.com/login/');
    assert.equal(await page.textContent('p'), 'FAKE-LOGIN');
  });

  await check('groups, reels and feed are all blocked on full loads', async () => {
    await goto('https://www.facebook.com/groups/x');
    assert.ok(page.url().startsWith(blockedUrl), 'groups should be blocked');
  });

  await check('in-app (SPA) navigation to a locked route is caught by the content script', async () => {
    await goto('https://www.facebook.com/marketplace/spa');
    await page.waitForURL((u) => u.toString().startsWith(blockedUrl), { timeout: 8000 });
    assert.ok(page.url().startsWith(blockedUrl));
  });

  await check('block page search sends you to Marketplace search with the query', async () => {
    await goto('https://www.facebook.com/reel/1');
    assert.ok(page.url().startsWith(blockedUrl));
    await page.fill('#search-input', 'road bike');
    await page.click('#search-form button[type=submit]');
    await page.waitForURL((u) => u.toString().startsWith('https://www.facebook.com/marketplace/search/'), { timeout: 8000 });
    const url = new URL(page.url());
    assert.equal(url.searchParams.get('query'), 'road bike');
  });

  await check('popup shows the lock as ON', async () => {
    const popup = await context.newPage();
    await popup.goto(`chrome-extension://${extensionId}/popup.html`);
    await popup.waitForFunction(() => document.getElementById('status').textContent === 'Lock is ON');
    await popup.close();
  });

  await check('turning the lock off stops blocking', async () => {
    const popup = await context.newPage();
    await popup.goto(`chrome-extension://${extensionId}/popup.html`);
    await popup.waitForFunction(() => document.getElementById('status').textContent === 'Lock is ON');
    await popup.click('#turn-off');
    // The confirm button stays disabled until the exact phrase is typed.
    assert.equal(await popup.isDisabled('#confirm-off'), true);
    await popup.fill('#confirm-input', 'I choose distraction ');
    assert.equal(await popup.isDisabled('#confirm-off'), false, 'exact phrase (trimmed) should enable confirm');
    await popup.click('#confirm-off');
    await popup.waitForFunction(() => document.getElementById('status').textContent === 'Lock is OFF');
    await popup.close();

    await goto('https://www.facebook.com/reel/1');
    assert.equal(await page.textContent('p'), 'FAKE-REEL', 'reel should load while lock is off');
  });

  await check('turning the lock back on blocks again', async () => {
    const popup = await context.newPage();
    await popup.goto(`chrome-extension://${extensionId}/popup.html`);
    await popup.waitForFunction(() => document.getElementById('status').textContent === 'Lock is OFF');
    await popup.click('#turn-on');
    await popup.waitForFunction(() => document.getElementById('status').textContent === 'Lock is ON');
    await popup.close();

    await goto('https://www.facebook.com/reel/1');
    assert.ok(page.url().startsWith(blockedUrl), 'reel should be blocked again');
  });
} catch (err) {
  failures++;
  results.push(`ERROR ${err.stack}`);
} finally {
  await context.close();
  server.close();
  console.log(results.join('\n'));
  console.log(failures === 0 ? '\nE2E: all checks passed' : `\nE2E: ${failures} failure(s)`);
  process.exit(failures === 0 ? 0 : 1);
}
