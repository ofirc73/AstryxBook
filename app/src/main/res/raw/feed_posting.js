// Runs at document start in the main view, before Facebook's own scripts.
//
// m.facebook.com sends a post's photos over its WebSocket, and most of them only after Post, while
// its snackbar says "Uploading your post...": at 128 kbps up (emulator, DevTools throttling, a
// 4000x3000 photo, 2026-11-03) Facebook said "Posted" 40 s after Post. A post needs the page until
// then: closed by Android (as it ends a backgrounded app that needs the memory) 2.6 s after Post,
// with all of the page's bytes handed to the network, the app still lost the post, without a word.
// This tells the app (AstryxPage call "feedPosting", state "posting", then "posted" or "ended")
// from Post until Facebook has it, so it keeps the app in the foreground with a notification
// meanwhile (Posting), as for a story.
//
// Posting: from a tap on Post with a photo in the composer (/composer/): Post is the button in the
// composer's top row right of its back button (it has no label). Also the composer closing with a
// photo in it and no dialog on it in the last DIALOG_MS (its "Discard post?" sheet, a
// [role="dialog"]). Not Facebook's snackbar (.snackbar-container), nor the composer closing alone:
// over a slow link both came seconds after Post, and a post closed before them was lost too.
// Posted: the snackbar showing a text other than the first it showed ("Uploading your post..."
// then, after a while with no text, "Posted"). The sockets' bufferedAmount can't tell: it was 0
// long before "Posted", while Facebook went on sending the photo a piece at a time. Ended: no
// photo-sized send (BIG_SEND bytes) for SETTLE_MS, and no "Posted" seen (the page hidden, its
// timers slowed to a check a minute, or "Posted" the first text seen): nothing is said then. No
// text is read, only whether there's any and whether it changed, so it works in any language.
// ES5, like the other document-start scripts.
(function () {
  var Native = window.WebSocket;
  if (!Native || window.__astryxFeedPosting) return;
  var sockets = [];
  // Read by the tests and the live check: mostUnsent, the most a socket held unsent after a send.
  var state = window.__astryxFeedPosting = { sockets: sockets, posting: false, mostUnsent: 0 };

  var BIG_SEND = 8000; // a piece of a photo (about 30 KB each); the page's own messages are smaller
  var DIALOG_MS = 2000; // the sheet goes as Discard is tapped, the composer a moment later
  var SETTLE_MS = window.__astryxFeedPostingSettleMs || 30000; // tests shorten it
  var bigSendAt = 0;

  function Wrapped(url, protocols) {
    var socket = protocols === undefined ? new Native(url) : new Native(url, protocols);
    sockets.push(socket);
    return socket;
  }
  Wrapped.prototype = Native.prototype;
  ['CONNECTING', 'OPEN', 'CLOSING', 'CLOSED'].forEach(function (name) { Wrapped[name] = Native[name]; });
  window.WebSocket = Wrapped;
  var nativeSend = Native.prototype.send;
  Native.prototype.send = function (data) {
    var result = nativeSend.apply(this, arguments);
    var size = data && (data.byteLength || data.size || data.length) || 0;
    if (size >= BIG_SEND) bigSendAt = Date.now();
    if (this.bufferedAmount > state.mostUnsent) state.mostUnsent = this.bufferedAmount;
    return result;
  };

  var onComposer = false;
  var withPhoto = false;
  var dialogAt = 0;
  var startedAt = 0;
  var firstText = null;

  // The composer's top row: the top-most buttons on screen, its back button the left-most of them.
  function isPostButton(button) {
    var buttons = document.querySelectorAll('[role="button"]');
    var row = [];
    var top = Infinity;
    for (var i = 0; i < buttons.length; i++) {
      var box = buttons[i].getBoundingClientRect();
      if (box.width <= 0) continue;
      if (box.top < top - 4) { top = box.top; row = [buttons[i]]; } else if (box.top <= top + 4) row.push(buttons[i]);
    }
    var back = null;
    for (var j = 0; j < row.length; j++) {
      if (!back || row[j].getBoundingClientRect().left < back.getBoundingClientRect().left) back = row[j];
    }
    return row.indexOf(button) >= 0 && button !== back;
  }

  // Facebook's snackbar while posting, at each change of the page too: with the page hidden (the
  // app left) its timers run seconds to a minute apart, and "Posted" shows for a few seconds (missed
  // on the phone, 2026-11-03). A MutationObserver isn't slowed. Whether it said "Posted".
  function postedSeen() {
    var text = snackbarText();
    if (text !== null && firstText === null) firstText = text;
    return text !== null && text !== firstText;
  }
  var observer = window.MutationObserver && new MutationObserver(function () {
    if (state.posting && postedSeen()) report('posted');
  });

  function report(posting) {
    state.posting = posting === 'posting';
    if (observer && !state.posting) observer.disconnect();
    if (window.AstryxPage) window.AstryxPage.postMessage(JSON.stringify({ call: 'feedPosting', state: posting }));
  }

  // Facebook's snackbar's text, or null when there's none.
  function snackbarText() {
    var bar = document.querySelector('.snackbar-container');
    return bar && bar.textContent.replace(/^\s+|\s+$/g, '') || null;
  }

  function start() {
    startedAt = Date.now();
    firstText = snackbarText();
    report('posting');
    if (observer) observer.observe(document.body, { childList: true, subtree: true, characterData: true });
  }

  document.addEventListener('click', function (event) {
    if (state.posting || !/^\/composer\/?$/.test(location.pathname)) return;
    var button = event.target && event.target.closest && event.target.closest('[role="button"]');
    if (button && isPostButton(button) && document.querySelector('img[src^="data:image"]')) start();
  }, true);

  setInterval(function () {
    var composer = /^\/composer\/?$/.test(location.pathname);
    var closed = onComposer && !composer;
    onComposer = composer;
    if (composer) {
      withPhoto = !!document.querySelector('img[src^="data:image"]');
      if (document.querySelector('[role="dialog"]')) dialogAt = Date.now();
    }
    if (!state.posting) {
      if (closed && withPhoto && Date.now() - dialogAt > DIALOG_MS) start();
      return;
    }
    if (postedSeen()) report('posted');
    else if (Date.now() - Math.max(startedAt, bigSendAt) > SETTLE_MS) report('ended');
  }, 500);
})();
