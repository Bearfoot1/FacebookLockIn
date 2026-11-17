// Background service worker: keeps the declarativeNetRequest rules in sync with the lock state.
importScripts('rules.js');

var R = self.LockRules;

// Replace all dynamic rules to match the enabled flag.
async function writeRules(enabled) {
  var existing = await chrome.declarativeNetRequest.getDynamicRules();
  var removeRuleIds = existing.map(function (rule) {
    return rule.id;
  });
  // Also remove our own ids in case they were installed but not listed.
  R.dnrRuleIds().forEach(function (id) {
    if (removeRuleIds.indexOf(id) === -1) removeRuleIds.push(id);
  });
  var addRules = enabled ? R.buildDnrRules('/blocked.html') : [];
  await chrome.declarativeNetRequest.updateDynamicRules({
    removeRuleIds: removeRuleIds,
    addRules: addRules
  });
}

// Every sync runs after the previous one, so two toggles can never interleave.
var syncChain = Promise.resolve();
function queue(task) {
  var next = syncChain.then(task, task);
  syncChain = next.catch(function () {});
  return next;
}

async function readEnabled() {
  try {
    var stored = await chrome.storage.local.get({ enabled: true });
    return stored.enabled !== false;
  } catch (e) {
    // If storage is unavailable, fail closed: keep the lock on.
    return true;
  }
}

// Reads the stored flag inside the queue, so it is always the latest value.
function applyCurrentState() {
  return queue(async function () {
    await writeRules(await readEnabled());
  });
}

chrome.runtime.onInstalled.addListener(function () {
  chrome.storage.local.get({ enabled: true, blockCount: 0, lastQuote: -1 }).then(function (defaults) {
    return chrome.storage.local.set(defaults);
  });
  applyCurrentState();
});

chrome.runtime.onStartup.addListener(function () {
  applyCurrentState();
});

chrome.storage.onChanged.addListener(function (changes, areaName) {
  if (areaName === 'local' && changes.enabled) {
    applyCurrentState();
  }
});

// In-app navigation to a locked route: the content script asks us to move that tab to the block page.
chrome.runtime.onMessage.addListener(function (message, sender) {
  if (!message || message.type !== 'mli-block-tab' || !sender.tab || sender.tab.id === undefined) {
    return;
  }
  sendToBlockedPage(sender.tab.id);
});

// Also apply on every worker start, so rules survive worker restarts.
applyCurrentState();

// Backstop for in-app (history.pushState) navigation. The content script is missing or
// orphaned in tabs that were open before an install, update or re-enable, and DNR only
// sees full page loads. host_permissions let us see Facebook tab URLs without "tabs".
var BLOCKED_PAGE = chrome.runtime.getURL('blocked.html');

// The content script and the onUpdated backstop can both report one navigation; send a
// tab to the block page once per BLOCK_DEDUPE_MS so it is counted and stacked only once.
var BLOCK_DEDUPE_MS = 1500;
var lastBlockedAt = {};

function sendToBlockedPage(tabId) {
  var now = Date.now();
  if (lastBlockedAt[tabId] && now - lastBlockedAt[tabId] < BLOCK_DEDUPE_MS) return;
  lastBlockedAt[tabId] = now;
  chrome.tabs.update(tabId, { url: BLOCKED_PAGE }).catch(function () {
    // Tab closed meanwhile.
  });
}

chrome.tabs.onRemoved.addListener(function (tabId) {
  delete lastBlockedAt[tabId];
});

async function blockIfLocked(tabId, url) {
  if (!url || !R.isLockedUrl(url)) return;
  if (!(await readEnabled())) return;
  sendToBlockedPage(tabId);
}

chrome.tabs.onUpdated.addListener(function (tabId, changeInfo) {
  if (changeInfo.url) blockIfLocked(tabId, changeInfo.url);
});

// Facebook tabs already open when the worker starts. Run on every start, not only on
// onInstalled/onStartup: re-enabling the extension fires neither.
async function sweepOpenTabs() {
  try {
    var tabs = await chrome.tabs.query({ url: '*://*.facebook.com/*' });
    tabs.forEach(function (tab) {
      if (tab.id !== undefined) blockIfLocked(tab.id, tab.url);
    });
  } catch (e) {
    // Nothing to sweep.
  }
}
sweepOpenTabs();
