// Block page: shows a motivational quote and a tip, a Marketplace search box and the attempt counter.
(function () {
  'use strict';

  var R = self.LockRules;
  var quoteEl = document.getElementById('quote');
  var tipEl = document.getElementById('tip');
  var countEl = document.getElementById('count');
  var form = document.getElementById('search-form');
  var input = document.getElementById('search-input');

  // Storage can be unavailable in some contexts; the page still works without it.
  async function loadAndCount() {
    var state = { blockCount: 0, lastQuote: -1, lastTip: -1 };
    try {
      state = await chrome.storage.local.get({ blockCount: 0, lastQuote: -1, lastTip: -1 });
    } catch (e) {
      // fall through with defaults
    }

    var quoteIndex = R.pickQuoteIndex(state.lastQuote, Math.random);
    var tipIndex = R.pickTipIndex(state.lastTip, Math.random);
    quoteEl.textContent = '“' + R.QUOTES[quoteIndex] + '”';
    tipEl.textContent = R.TIPS[tipIndex];

    var count = (state.blockCount || 0) + 1;
    countEl.textContent = String(count);

    try {
      await chrome.storage.local.set({ blockCount: count, lastQuote: quoteIndex, lastTip: tipIndex });
    } catch (e) {
      // ignore: counter is best-effort
    }
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    window.location.href = R.marketplaceSearchUrl(input.value);
  });

  loadAndCount();
})();
