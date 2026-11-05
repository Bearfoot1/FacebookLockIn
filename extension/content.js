// Content script for facebook.com (isolated world).
// The declarativeNetRequest rules catch full page loads. Facebook is a single-page app, so
// in-app navigation is checked here: main-world.js announces every history change and
// popstate covers back/forward.
(function () {
  'use strict';

  var R = self.LockRules;
  if (!R) return;

  // null until the stored flag has been read. Until then we do not redirect, so a lock that is
  // off is never briefly enforced.
  var enabled = null;
  var redirectRequested = false;

  // Web pages cannot navigate to extension pages unless they are web-accessible, and we do not
  // want Facebook to be able to probe for the extension. So the background worker moves the tab.
  function check() {
    if (enabled !== true || redirectRequested) return;
    if (R.isLockedUrl(location.href)) {
      redirectRequested = true;
      chrome.runtime.sendMessage({ type: 'mli-block-tab' });
    }
  }

  try {
    chrome.storage.local.get({ enabled: true }, function (stored) {
      enabled = stored.enabled !== false;
      check();
    });
    chrome.storage.onChanged.addListener(function (changes, areaName) {
      if (areaName === 'local' && changes.enabled) {
        enabled = changes.enabled.newValue !== false;
        check();
      }
    });
  } catch (e) {
    // Storage unavailable: fail closed, keep the lock on. DNR rules still cover full loads.
    enabled = true;
    check();
  }

  window.addEventListener('mli-navigation', check);
  window.addEventListener('popstate', check);
})();
