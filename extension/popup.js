// Popup: shows the lock state and lets you turn it off (with a typed confirmation).
(function () {
  'use strict';

  var CONFIRM_PHRASE = 'I choose distraction';

  var statusEl = document.getElementById('status');
  var countEl = document.getElementById('count');
  var turnOffBtn = document.getElementById('turn-off');
  var turnOnBtn = document.getElementById('turn-on');
  var onControls = document.getElementById('on-controls');
  var confirmBox = document.getElementById('confirm');
  var confirmInput = document.getElementById('confirm-input');
  var confirmBtn = document.getElementById('confirm-off');
  var cancelBtn = document.getElementById('cancel');

  function render(state) {
    var enabled = state.enabled !== false;
    statusEl.textContent = enabled ? 'Lock is ON' : 'Lock is OFF';
    statusEl.className = 'status ' + (enabled ? 'on' : 'off');
    countEl.textContent = String(state.blockCount || 0);
    turnOffBtn.classList.toggle('hidden', !enabled);
    onControls.classList.toggle('hidden', enabled);
    confirmBox.classList.add('hidden');
    confirmInput.value = '';
    confirmBtn.disabled = true;
  }

  // Gaps the extension can't close itself, only point out: Chrome runs extensions in
  // Incognito only when the user allows it, and site access can be narrowed to "on click".
  function checkCoverage() {
    try {
      chrome.extension.isAllowedIncognitoAccess(function (allowed) {
        document.getElementById('incognito-warn').classList.toggle('hidden', allowed === true);
      });
    } catch (e) {
      // Not available: leave the warning hidden.
    }
    try {
      chrome.permissions.contains({ origins: ['*://*.facebook.com/*'] }, function (granted) {
        document.getElementById('access-warn').classList.toggle('hidden', granted === true);
      });
    } catch (e) {
      // Not available.
    }
  }
  checkCoverage();

  function load() {
    chrome.storage.local.get({ enabled: true, blockCount: 0 }, render);
  }

  turnOffBtn.addEventListener('click', function () {
    confirmBox.classList.remove('hidden');
    confirmInput.focus();
  });

  confirmInput.addEventListener('input', function () {
    confirmBtn.disabled = confirmInput.value.trim() !== CONFIRM_PHRASE;
  });

  confirmBtn.addEventListener('click', function () {
    if (confirmInput.value.trim() !== CONFIRM_PHRASE) return;
    chrome.storage.local.set({ enabled: false }, load);
  });

  cancelBtn.addEventListener('click', function () {
    confirmBox.classList.add('hidden');
    confirmInput.value = '';
    confirmBtn.disabled = true;
  });

  turnOnBtn.addEventListener('click', function () {
    chrome.storage.local.set({ enabled: true }, load);
  });

  load();
})();
