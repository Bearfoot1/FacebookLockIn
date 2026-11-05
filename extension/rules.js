// Shared rules for Marketplace Lock-In.
// Loaded as a classic script by the background worker (importScripts),
// the content script, the blocked page and the popup. Pure logic only:
// no chrome.* calls in here, so it can be unit tested in Node.
(function (global) {
  'use strict';

  // Any host ending in facebook.com (www, m, web, mbasic, etc.).
  var FACEBOOK_HOST_RE = /^([a-z0-9-]+\.)*facebook\.com$/i;

  // Paths that stay usable. Everything else on facebook.com is locked.
  //  - marketplace: browsing, item pages, search, selling
  //  - login / checkpoint / recover / two-factor: you cannot log in without them
  //  - help / policies / privacy / terms / cookies: legal and support pages
  var ALLOWED_PATH_PREFIXES = [
    'marketplace',
    'login',
    'login.php',
    'checkpoint',
    'recover',
    'two_step_verification',
    'help',
    'policies',
    'privacy',
    'terms',
    'cookies'
  ];

  // DNR rules used by the background worker, written as urlFilter patterns (not regexes).
  // Chrome caps each regex at 2KB compiled, so urlFilter is used instead.
  //  - "||facebook.com^" matches facebook.com and every subdomain (www, m, web, ...).
  //    It does not match lookalikes such as notfacebook.com.
  //  - "^" is a separator: it matches "/", "?", "#" or the end of the URL. So
  //    "||facebook.com/marketplace^" matches /marketplace, /marketplace/ and
  //    /marketplace?query=, but not /marketplacefake.
  // Each filter comes in a normal and a trailing-dot form ("facebook.com." is the same host).
  var FACEBOOK_URL_FILTERS = ['||facebook.com^', '||facebook.com.^'];
  var MESSENGER_URL_FILTERS = ['||messenger.com^', '||messenger.com.^'];
  var ALLOW_URL_FILTERS = [];
  ALLOWED_PATH_PREFIXES.forEach(function (prefix) {
    ALLOW_URL_FILTERS.push('||facebook.com/' + prefix + '^');
    ALLOW_URL_FILTERS.push('||facebook.com./' + prefix + '^');
  });

  function parseUrl(input) {
    try {
      return new URL(input);
    } catch (e) {
      return null;
    }
  }

  // "www.facebook.com." (trailing dot) is the same host as "www.facebook.com".
  function normalizeHost(hostname) {
    return (hostname || '').toLowerCase().replace(/\.$/, '');
  }

  function isFacebookHost(hostname) {
    return FACEBOOK_HOST_RE.test(normalizeHost(hostname));
  }

  // Messenger has its own domain. It is always locked, whatever the path.
  function isMessengerHost(hostname) {
    var host = normalizeHost(hostname);
    return host === 'messenger.com' || /\.messenger\.com$/.test(host);
  }

  // Returns true if the URL may be viewed while the lock is on.
  function isAllowedUrl(input) {
    var url = parseUrl(input);
    if (!url) return true; // unparseable input is not a Facebook page we can lock
    if (url.protocol !== 'http:' && url.protocol !== 'https:') return true; // not ours to lock
    if (isMessengerHost(url.hostname)) return false;
    if (!isFacebookHost(url.hostname)) return true; // not Facebook
    var first = url.pathname.split('/').filter(Boolean)[0];
    if (!first) return false; // the home feed
    return ALLOWED_PATH_PREFIXES.indexOf(first.toLowerCase()) !== -1;
  }

  // Returns true if the URL is a facebook.com page that should be locked.
  function isLockedUrl(input) {
    return !isAllowedUrl(input);
  }

  // Marketplace search URL for a query string.
  function marketplaceSearchUrl(query) {
    var base = 'https://www.facebook.com/marketplace/search/';
    var q = (query || '').trim();
    return q ? base + '?query=' + encodeURIComponent(q) : base;
  }

  var MARKETPLACE_URL = 'https://www.facebook.com/marketplace/';

  // Quotes and tips come from shared/motivation.json (generated into motivation.js).
  // motivation.js must be loaded before this file; see blocked.html and the tests.
  var MOTIVATION = global.LockMotivation || { QUOTES: [], TIPS: [] };
  var QUOTES = MOTIVATION.QUOTES;
  var TIPS = MOTIVATION.TIPS;

  // Pick an index into a list of the given length, never the same index as lastIndex
  // (when the list has more than one item).
  function pickIndex(length, lastIndex, random) {
    if (!length || length <= 1) return 0;
    var r = typeof random === "function" ? random() : Math.random();
    var index = Math.floor(r * length);
    if (index >= length) index = length - 1;
    if (index < 0) index = 0;
    if (index === lastIndex) index = (index + 1) % length;
    return index;
  }

  function pickQuoteIndex(lastIndex, random) {
    return pickIndex(QUOTES.length, lastIndex, random);
  }

  function pickTipIndex(lastIndex, random) {
    return pickIndex(TIPS.length, lastIndex, random);
  }

  // Declarative net request rules: block everything, then allow the allowed paths.
  // The allow rule has a higher priority, so it wins for allowed URLs.
  function buildDnrRules(blockPageExtensionPath) {
    var target = blockPageExtensionPath || '/blocked.html';
    var rules = [];
    var nextId = 1;
    // Block rules (priority 1): redirect every main-frame load of Facebook or Messenger.
    FACEBOOK_URL_FILTERS.concat(MESSENGER_URL_FILTERS).forEach(function (filter) {
      rules.push({
        id: nextId++,
        priority: 1,
        action: { type: 'redirect', redirect: { extensionPath: target } },
        condition: { urlFilter: filter, resourceTypes: ['main_frame'] }
      });
    });
    // Allow rules (priority 2) beat the block rules, one per allowed prefix.
    ALLOW_URL_FILTERS.forEach(function (filter) {
      rules.push({
        id: nextId++,
        priority: 2,
        action: { type: 'allow' },
        condition: { urlFilter: filter, resourceTypes: ['main_frame'] }
      });
    });
    return rules;
  }

  // Rule ids the background worker owns, so it can remove them before re-adding.
  function dnrRuleIds() {
    return buildDnrRules('/blocked.html').map(function (rule) {
      return rule.id;
    });
  }

  var api = {
    ALLOWED_PATH_PREFIXES: ALLOWED_PATH_PREFIXES,
    FACEBOOK_URL_FILTERS: FACEBOOK_URL_FILTERS,
    MESSENGER_URL_FILTERS: MESSENGER_URL_FILTERS,
    ALLOW_URL_FILTERS: ALLOW_URL_FILTERS,
    isMessengerHost: isMessengerHost,
    dnrRuleIds: dnrRuleIds,
    MARKETPLACE_URL: MARKETPLACE_URL,
    QUOTES: QUOTES,
    TIPS: TIPS,
    pickIndex: pickIndex,
    pickTipIndex: pickTipIndex,
    isFacebookHost: isFacebookHost,
    isAllowedUrl: isAllowedUrl,
    isLockedUrl: isLockedUrl,
    marketplaceSearchUrl: marketplaceSearchUrl,
    pickQuoteIndex: pickQuoteIndex,
    buildDnrRules: buildDnrRules
  };

  global.LockRules = api;
})(typeof globalThis !== 'undefined' ? globalThis : this);
