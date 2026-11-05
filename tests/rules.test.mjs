// Unit tests for the pure rules in extension/rules.js. Run with: npm test
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const source = readFileSync(path.join(here, '..', 'extension', 'rules.js'), 'utf8');
// A fresh vm context has no URL global, so pass ours in. rules.js stores its API on the global object.
const motivationSource = readFileSync(path.join(here, "..", "extension", "motivation.js"), "utf8");
const sandbox = { URL };
vm.createContext(sandbox);
vm.runInContext(motivationSource, sandbox);
vm.runInContext(source, sandbox);
const R = sandbox.LockRules;
// Plain copy of the rules built in the sandbox realm, so deepStrictEqual compares arrays across realms.
const plain = (value) => JSON.parse(JSON.stringify(value));

test('marketplace home and item pages are allowed', () => {
  assert.equal(R.isAllowedUrl('https://www.facebook.com/marketplace/'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/marketplace'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/marketplace/item/12345/'), true);
  assert.equal(R.isAllowedUrl('https://m.facebook.com/marketplace/category/vehicles'), true);
});

test('marketplace search is allowed, with and without a query', () => {
  assert.equal(R.isAllowedUrl('https://www.facebook.com/marketplace/search/?query=bike'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/marketplace/search/'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/marketplace/?query=sofa'), true);
});

test('login and recovery flow pages are allowed', () => {
  assert.equal(R.isAllowedUrl('https://www.facebook.com/login/'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/login.php?next=x'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/checkpoint/1/'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/recover/initiate/'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/two_step_verification/authentication/'), true);
});

test('home feed, reels, groups, messages, search and profiles are locked', () => {
  assert.equal(R.isLockedUrl('https://www.facebook.com/'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com/reel/123'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com/watch/'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com/groups/abc'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com/messages/t/1'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com/search/top?q=x'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com/someprofile'), true);
  assert.equal(R.isLockedUrl('https://m.facebook.com/'), true);
});

test('prefix matching is by whole path segment', () => {
  // "marketplaceX" must not slip through the "marketplace" allow rule.
  assert.equal(R.isAllowedUrl('https://www.facebook.com/marketplacefake/'), false);
  assert.equal(R.isAllowedUrl('https://www.facebook.com/loginhelp/'), false);
});

test('non-Facebook sites are not locked by this extension', () => {
  assert.equal(R.isLockedUrl('https://example.com/'), false);
  assert.equal(R.isLockedUrl('https://notfacebook.com/'), false);
  assert.equal(R.isLockedUrl('https://facebook.com.evil.test/'), false);
});

test('invalid input is treated as not locked (fail open on garbage, never throws)', () => {
  assert.equal(R.isLockedUrl('not a url'), false);
  assert.equal(R.isLockedUrl(''), false);
});

test('facebook.com hostname matching covers subdomains and the bare domain', () => {
  assert.equal(R.isFacebookHost('facebook.com'), true);
  assert.equal(R.isFacebookHost('www.facebook.com'), true);
  assert.equal(R.isFacebookHost('web.facebook.com'), true);
  assert.equal(R.isFacebookHost('notfacebook.com'), false);
  assert.equal(R.isFacebookHost('facebook.co'), false);
});

test('marketplace search URL encodes the query and trims it', () => {
  assert.equal(R.marketplaceSearchUrl('  road bike  '), 'https://www.facebook.com/marketplace/search/?query=road%20bike');
  assert.equal(R.marketplaceSearchUrl('a&b'), 'https://www.facebook.com/marketplace/search/?query=a%26b');
  assert.equal(R.marketplaceSearchUrl(''), 'https://www.facebook.com/marketplace/search/');
});

test('quotes are non-empty, unique, unpadded strings (at least 30)', () => {
  assert.ok(R.QUOTES.length >= 30, `only ${R.QUOTES.length} quotes`);
  assert.equal(new Set(R.QUOTES).size, R.QUOTES.length);
  for (const q of R.QUOTES) {
    assert.equal(typeof q, 'string');
    assert.equal(q.trim(), q);
    assert.ok(q.length > 0);
  }
});

test('tips are non-empty, unique, unpadded strings (at least 20)', () => {
  assert.ok(R.TIPS.length >= 20, `only ${R.TIPS.length} tips`);
  assert.equal(new Set(R.TIPS).size, R.TIPS.length);
  for (const t of R.TIPS) {
    assert.equal(t.trim(), t);
    assert.ok(t.length > 0);
  }
});

test('pickTipIndex never repeats twice in a row and stays in range', () => {
  for (let last = 0; last < R.TIPS.length; last++) {
    for (let i = 0; i < 50; i++) {
      const next = R.pickTipIndex(last, Math.random);
      assert.notEqual(next, last);
      assert.ok(next >= 0 && next < R.TIPS.length);
    }
  }
  assert.equal(R.pickIndex(0, 5, Math.random), 0);
  assert.equal(R.pickIndex(1, 0, Math.random), 0);
});

test('pickQuoteIndex never returns the same index twice in a row', () => {
  // A random source that always lands on the same index would repeat it, so the function must bump it.
  const fixed = () => 0;
  assert.equal(R.pickQuoteIndex(-1, fixed), 0);
  assert.equal(R.pickQuoteIndex(0, fixed), 1);
  for (let last = 0; last < R.QUOTES.length; last++) {
    for (let i = 0; i < 200; i++) {
      const next = R.pickQuoteIndex(last, Math.random);
      assert.notEqual(next, last);
      assert.ok(next >= 0 && next < R.QUOTES.length);
    }
  }
});

test('pickQuoteIndex stays in range at the top of the random range', () => {
  const almostOne = () => 0.9999999999;
  const i = R.pickQuoteIndex(-1, almostOne);
  assert.ok(i >= 0 && i < R.QUOTES.length);
});

test('DNR block rules redirect main-frame loads to the block page', () => {
  const rules = plain(R.buildDnrRules('/blocked.html'));
  const blocks = rules.filter((r) => r.action.type === 'redirect');
  assert.deepEqual(blocks.map((b) => b.condition.urlFilter), ['||facebook.com^', '||facebook.com.^', '||messenger.com^', '||messenger.com.^']);
  for (const block of blocks) {
    assert.equal(block.action.redirect.extensionPath, '/blocked.html');
    assert.deepEqual(block.condition.resourceTypes, ['main_frame']);
  }
});

test('trailing-dot hosts are treated as the same Facebook host', () => {
  assert.equal(R.isFacebookHost('www.facebook.com.'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com./groups/x'), true);
  assert.equal(R.isLockedUrl('https://www.facebook.com./'), true);
  assert.equal(R.isAllowedUrl('https://www.facebook.com./marketplace/'), true);
  assert.equal(R.isLockedUrl('https://facebook.com.evil.test./groups/x'), false);
});

test('messenger.com is always locked, whatever the path', () => {
  assert.equal(R.isMessengerHost('www.messenger.com'), true);
  assert.equal(R.isMessengerHost('messenger.com.'), true);
  assert.equal(R.isMessengerHost('notmessenger.com'), false);
  assert.equal(R.isLockedUrl('https://www.messenger.com/t/1'), true);
  assert.equal(R.isLockedUrl('https://messenger.com/'), true);
  assert.equal(R.isLockedUrl('https://example.com/messenger.com'), false);
});

// Model of Chrome's urlFilter syntax, used to check the DNR rules without a browser.
//   "||" = start of a domain label, "^" = separator (/, ?, #, or end of URL), "|" not used here.
function urlFilterMatches(filter, url) {
  let body = filter;
  let domainAnchor = false;
  if (body.startsWith('||')) {
    domainAnchor = true;
    body = body.slice(2);
  }
  const escaped = body
    .split('')
    .map((ch) => {
      if (ch === '^') return '(?:[^A-Za-z0-9_\\-.%]|$)';
      return ch.replace(/[.*+?()[\]{}|\\/$]/g, '\\$&');
    })
    .join('');
  const prefix = domainAnchor ? '^https?://([^/?#]*\\.)?' : '';
  return new RegExp(prefix + escaped, 'i').test(url);
}

test('DNR urlFilter rules agree with the JS allowlist on sample URLs', () => {
  const dnrRules = plain(R.buildDnrRules('/blocked.html'));
  const blockFilters = dnrRules.filter((r) => r.action.type === 'redirect').map((r) => r.condition.urlFilter);
  const allowFilters = dnrRules.filter((r) => r.action.type === 'allow').map((r) => r.condition.urlFilter);
  const dnrAllowed = (url) => allowFilters.some((f) => urlFilterMatches(f, url));
  const dnrBlocked = (url) => blockFilters.some((f) => urlFilterMatches(f, url)) && !dnrAllowed(url);

  const samples = [
    ['https://www.facebook.com/', false],
    ['https://www.facebook.com', false],
    ['https://www.facebook.com/?ref=x', false],
    ['https://www.facebook.com/marketplace/', true],
    ['https://www.facebook.com/marketplace', true],
    ['https://www.facebook.com/marketplace/search/?query=x', true],
    ['https://www.facebook.com/marketplace?query=sofa', true],
    ['https://m.facebook.com/marketplace/item/1/', true],
    ['https://www.facebook.com/login/', true],
    ['https://www.facebook.com/login.php?next=/', true],
    ['https://www.facebook.com/checkpoint/1/', true],
    ['https://www.facebook.com/reel/1', false],
    ['https://www.facebook.com/groups/abc', false],
    ['https://www.facebook.com/marketplacefake/', false],
    ['https://www.facebook.com/loginhelp/', false]
  ];
  for (const [url, expectedAllowed] of samples) {
    assert.equal(dnrBlocked(url), !expectedAllowed, `${url} DNR block/allow outcome`);
    assert.equal(R.isAllowedUrl(url), expectedAllowed, `${url} JS allowlist agrees`);
  }

  // Lookalike hosts are not Facebook, so neither rule touches them.
  for (const url of ['https://notfacebook.com/', 'https://facebook.com.evil.test/', 'https://example.com/']) {
    assert.equal(dnrBlocked(url), false, `${url} should not be blocked`);
  }
  // Trailing-dot Facebook hosts: blocked by DNR unless the path is allowed, same as the JS check.
  assert.equal(dnrBlocked('https://www.facebook.com./groups/x'), true);
  assert.equal(dnrBlocked('https://www.facebook.com./marketplace/'), false);
  // Messenger is blocked by DNR on any path.
  assert.equal(dnrBlocked('https://www.messenger.com/t/1'), true);
});

test('DNR allow rules: one per allowed prefix, each with higher priority than the block rule', () => {
  const rules = plain(R.buildDnrRules('/blocked.html'));
  const block = rules.find((r) => r.action.type === 'redirect');
  const allows = rules.filter((r) => r.action.type === 'allow');
  // Each prefix has a normal and a trailing-dot allow rule.
  assert.equal(allows.length, 2 * R.ALLOWED_PATH_PREFIXES.length);
  for (const allow of allows) {
    assert.ok(allow.priority > block.priority);
    assert.deepEqual(allow.condition.resourceTypes, ['main_frame']);
  }
  const ids = rules.map((r) => r.id);
  assert.equal(new Set(ids).size, ids.length, 'rule ids must be unique');
  assert.deepEqual(plain(R.dnrRuleIds()).sort((a, b) => a - b), ids.slice().sort((a, b) => a - b));
});
