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
  chrome.tabs.update(sender.tab.id, { url: chrome.runtime.getURL('blocked.html') });
});

// Also apply on every worker start, so rules survive worker restarts.
applyCurrentState();
