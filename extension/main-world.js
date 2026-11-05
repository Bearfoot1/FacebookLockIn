// Runs in the page's own JavaScript world (manifest "world": "MAIN") so it can see the
// history calls Facebook makes. It only announces navigation; content.js does the checking.
(function () {
  'use strict';
  ['pushState', 'replaceState'].forEach(function (method) {
    var original = history[method];
    history[method] = function () {
      var result = original.apply(this, arguments);
      window.dispatchEvent(new Event('mli-navigation'));
      return result;
    };
  });
})();
