// Runs at document start in the main view, before Facebook's own scripts.
//
// m.facebook.com sends a post's photos over its WebSocket, and much of them only after Post: at
// 128 kbps up (emulator, DevTools throttling, a 4000x3000 photo, 2026-11-03) the attached photo
// kept the socket sending for about 30 s, and a post sent meanwhile showed "Uploading your post..."
// for as long. A post whose photo was still going up was lost when the app was closed then: Android
// ends a backgrounded app's process when it needs the memory. This tells the app (AstryxPage call
// "feedPosting", state "posting", then "posted" or "failed") while a post is still uploading, so it
// keeps the app in the foreground with a notification meanwhile (Posting), as for a story.
//
// Posting: Facebook's snackbar (.snackbar-container, "Uploading your post...") showing within
// COMPOSER_MS of leaving the composer (/composer/), while a socket still has bytes to send
// (bufferedAmount). Posted: nothing left to send, and the snackbar gone or changed ("Posted"), or
// SETTLE_MS later. Failed: a socket closed with bytes unsent. No text is read, only whether it
// changed, so it works in any language. ES5, like the other document-start scripts.
(function () {
  var Native = window.WebSocket;
  if (!Native || window.__astryxFeedPosting) return;
  var sockets = [];
  // Read by the tests and the live check: mostUnsent, the most a socket held unsent after a send.
  var state = window.__astryxFeedPosting = { sockets: sockets, posting: false, mostUnsent: 0 };

  function Wrapped(url, protocols) {
    var socket = protocols === undefined ? new Native(url) : new Native(url, protocols);
    sockets.push(socket);
    return socket;
  }
  Wrapped.prototype = Native.prototype;
  ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (name) { Wrapped[name] = Native[name]; });
  window.WebSocket = Wrapped;
  var nativeSend = Native.prototype.send;
  Native.prototype.send = function () {
    var result = nativeSend.apply(this, arguments);
    if (this.bufferedAmount > state.mostUnsent) state.mostUnsent = this.bufferedAmount;
    return result;
  };

  var COMPOSER_MS = 5000; // the snackbar shows within a second of Post, as the composer closes
  // All sent, and no "Posted" seen (the page hidden, its snackbar missed). Tests shorten it.
  var SETTLE_MS = window.__astryxFeedPostingSettleMs || 20000;
  var onComposer = false;
  var composerLeftAt = 0;
  var postingText = null;
  var sentAt = 0;

  function unsent() {
    var bytes = 0;
    for (var i = 0; i < sockets.length; i++) {
      if (sockets[i].readyState !== Native.CLOSED) bytes += sockets[i].bufferedAmount;
    }
    return bytes;
  }

  // A socket that closed since the last look with bytes still unsent; closed ones are let go.
  function closedUnsent() {
    var lost = false;
    for (var i = sockets.length - 1; i >= 0; i--) {
      if (sockets[i].readyState !== Native.CLOSED) continue;
      if (sockets[i].bufferedAmount > 0) lost = true;
      sockets.splice(i, 1);
    }
    return lost;
  }

  function report(posting) {
    state.posting = posting === 'posting';
    if (window.AstryxPage) window.AstryxPage.postMessage(JSON.stringify({ call: 'feedPosting', state: posting }));
  }

  setInterval(function () {
    var composer = /^\/composer\/?$/.test(location.pathname);
    if (onComposer && !composer) composerLeftAt = Date.now();
    onComposer = composer;
    var bar = document.querySelector('.snackbar-container');
    var text = bar ? bar.textContent : null;
    var lost = closedUnsent();
    var left = unsent();
    if (!state.posting) {
      if (text !== null && left > 0 && Date.now() - composerLeftAt < COMPOSER_MS) {
        postingText = text;
        sentAt = 0;
        report('posting');
      }
      return;
    }
    if (lost) { report('failed'); return; }
    if (left > 0) { sentAt = 0; return; }
    if (!sentAt) sentAt = Date.now();
    if (text !== postingText || Date.now() - sentAt > SETTLE_MS) report('posted');
  }, 500);
})();
