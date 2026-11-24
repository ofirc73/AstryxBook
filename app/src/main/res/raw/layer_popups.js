// Messages layer: Facebook's desktop popups (a message's "Show more emojis", the message box's emoji,
// sticker and GIF pickers) on a phone. Read on the emulator 2026-11-24, after the user's report that
// a tap on a reaction's "+" showed nothing:
// - They focus their search box as they open. On a phone that brings up the keyboard, and Facebook
//   closes the popup at once (the reactions picker was drawn and removed 0.3 s later). A box a popup
//   focuses by itself is let go, so the popup stays; a tap on the box focuses it as usual (and the
//   keyboard still closes it: Facebook's, not held by holding the page's resize events).
// - Facebook places a popup by an inline top, as its desktop layout has room for: the message box's
//   emoji picker went below the screen's bottom. One that runs off the screen is moved back onto it.
// The story creator's popups are story_layout.js's. ES5, like the PiP scripts.
(function () {
  if (window.__astryxLayerPopups) return;
  window.__astryxLayerPopups = true;
  var TAP_MS = 1000;
  var MARGIN = 8;
  var tapped = null;
  var tappedAt = 0;
  document.addEventListener('pointerdown', function (event) {
    tapped = event.target;
    tappedAt = Date.now();
  }, true);

  function tappedOn(box) {
    if (!tapped || Date.now() - tappedAt >= TAP_MS) return false;
    return tapped === box || box.contains(tapped) || (typeof tapped.contains === 'function' && tapped.contains(box));
  }

  document.addEventListener('focusin', function (event) {
    var box = event.target;
    if (!box || box.tagName !== 'INPUT' || !box.closest('[role="dialog"]') || tappedOn(box)) return;
    box.blur();
  }, true);

  // A dialog's host Facebook placed by an inline top, moved by as much as it runs off the screen.
  function fit(dialog) {
    var host = dialog;
    while (host && !(host.style && host.style.position === 'absolute' && host.style.top)) host = host.parentElement;
    if (!host) return;
    // The dialog may be taller than its host (the emoji picker: 313 px in a host of 283).
    var hostBox = host.getBoundingClientRect();
    var dialogBox = dialog.getBoundingClientRect();
    var top = Math.min(hostBox.top, dialogBox.top);
    var bottom = Math.max(hostBox.bottom, dialogBox.bottom);
    if (bottom - top <= 0 || bottom - top > window.innerHeight - 2 * MARGIN) return;
    var off = 0;
    if (bottom > window.innerHeight - MARGIN) off = window.innerHeight - MARGIN - bottom;
    else if (top < MARGIN) off = MARGIN - top;
    if (off) host.style.top = (parseFloat(host.style.top) + off) + 'px';
  }

  function fitAll() {
    var dialogs = document.querySelectorAll('[role="dialog"]');
    for (var i = 0; i < dialogs.length; i++) fit(dialogs[i]);
  }

  // Back (messages_layer_nav.js __astryxLayerBack): an open popup (a dialog, the reactions bar's menu)
  // is closed by Escape, as on the desktop site, before anything else; one that stayed after an
  // Escape is passed over, so Back never stops on it (until it's closed: Facebook may show it again).
  var escaped = [];
  function onScreen(popup) {
    var box = popup.getBoundingClientRect();
    return popup.isConnected && box.width > 0 && box.height > 0 && box.bottom > 0 && box.top < window.innerHeight;
  }
  window.__astryxClosePopup = function () {
    escaped = escaped.filter(onScreen);
    var open = [].filter.call(document.querySelectorAll('[role="dialog"], [role="menu"]'), function (popup) {
      return escaped.indexOf(popup) < 0 && onScreen(popup);
    });
    var popup = open[open.length - 1];
    if (!popup) return false;
    escaped.push(popup);
    ['keydown', 'keyup'].forEach(function (type) {
      popup.dispatchEvent(new KeyboardEvent(type, { key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true, cancelable: true }));
    });
    return true;
  };

  var pending = false;
  new MutationObserver(function () {
    if (pending) return;
    pending = true;
    requestAnimationFrame(function () { pending = false; fitAll(); });
  }).observe(document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['style'] });
})();
