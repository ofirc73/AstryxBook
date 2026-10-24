// Messages layer only. The desktop site moves between its pages in-page (history.pushState)
// rather than loading them, which the layer's request interceptor never sees. Report every
// URL change to the app (AstryxPage, or LayerBridge on older WebViews), which closes the layer when it's one of Facebook's own sections
// (messagesLayerExit). ES5, like the PiP scripts.
(function () {
  if (window.__astryxLayerNavInstalled || !(window.AstryxPage || window.LayerBridge)) return;
  window.__astryxLayerNavInstalled = true;

  // A chat left for another page in the layer (a post shared in it), to show again on the way
  // back: an end-to-end encrypted chat opened again at its address shows the chat list at phone
  // width (Back from a shared post landed there). Kept in sessionStorage in case that page was a
  // full load.
  var RETURN_CHAT = 'astryxReturnChat';
  // The link tapped in that chat (the shared post), shown again in the chat on the way back:
  // opened anew, the chat shows its latest messages.
  var RETURN_LINK = 'astryxReturnLink';
  var CHAT_PATH = /^\/messages\/(?:e2ee\/)?t\/[^\/]+/;
  var shownChat = null;

  function chatPathOf(href) {
    var a = document.createElement('a');
    a.href = href;
    var m = CHAT_PATH.exec(a.pathname);
    return m ? m[0] + '/' : null;
  }

  function returnChat() {
    try { return sessionStorage.getItem(RETURN_CHAT); } catch (e) { return null; }
  }

  function setReturnChat(path) {
    try {
      if (path) sessionStorage.setItem(RETURN_CHAT, path); else sessionStorage.removeItem(RETURN_CHAT);
    } catch (e) {}
  }

  function stored(key) {
    try { return sessionStorage.getItem(key); } catch (e) { return null; }
  }

  function store(key, value) {
    try {
      if (value) sessionStorage.setItem(key, value); else sessionStorage.removeItem(key);
    } catch (e) {}
  }

  // The link the chat left by, scrolled to the middle of the screen once the chat shows it: true
  // when done. Again a moment later, in case the chat scrolls itself to its latest messages.
  function showReturnLink() {
    var href = stored(RETURN_LINK);
    if (!href) return true;
    var links = document.querySelectorAll('a[href]');
    for (var i = links.length - 1; i >= 0; i--) {
      var r = links[i].getBoundingClientRect();
      if (links[i].getAttribute('href') !== href || !r.width || !r.height) continue;
      var link = links[i];
      store(RETURN_LINK, null);
      link.scrollIntoView({ block: 'center' });
      setTimeout(function () { link.scrollIntoView({ block: 'center' }); }, 800);
      return true;
    }
    return false;
  }

  // Back at the chat it left: while the list shows instead, its row is tapped, and then the link
  // it was left by is shown (for up to 10 s).
  function reopenChat(path) {
    var until = Date.now() + 10000;
    var tapped = 0;
    (function poll() {
      if (chatPathOf(location.href) !== path || Date.now() > until) return;
      if (chatOpen()) {
        if (!showReturnLink()) setTimeout(poll, 250);
        return;
      }
      var row = document.querySelector('a[href="' + path + '"]');
      var r = row && row.getBoundingClientRect();
      if (r && r.width > 0 && r.height > 0 && Date.now() - tapped > 3000) {
        tapped = Date.now();
        row.click();
      }
      setTimeout(poll, 250);
    })();
  }

  var last = location.href;
  function check() {
    var path = chatPathOf(location.href);
    // The chat itself on screen, not the list (which keeps the chat's address).
    if (path) shownChat = chatOpen() ? path : null;
    if (location.href === last) return;
    var from = chatPathOf(last);
    last = location.href;
    if (from && from === shownChat && location.pathname.indexOf('/messages') !== 0) {
      setReturnChat(from);
    } else if (path && path === returnChat()) {
      setReturnChat(null);
      reopenChat(path);
    } else if (location.pathname.indexOf('/messages') === 0) {
      setReturnChat(null);
    }
    if (window.AstryxPage) {
      window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url: last }));
    } else {
      window.LayerBridge.onUrlChanged(last);
    }
  }
  // A link tapped in a chat on screen: shown again if the chat comes back (reopenChat).
  document.addEventListener('click', function (e) {
    var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
    if (!a || !chatOpen() || !chatPathOf(location.href)) return;
    var href = a.getAttribute('href');
    if (href.indexOf('/messages') !== 0) store(RETURN_LINK, href);
  }, true);

  // A shared post opens as a new page load (its link opens a new window, which the app loads in
  // the layer), which no URL watching sees: the chat on screen is kept as the page goes.
  window.addEventListener('pagehide', function () {
    check();
    if (shownChat && shownChat === chatPathOf(location.href)) setReturnChat(shownChat);
  });
  // Loaded anew at the chat it left.
  var startPath = chatPathOf(location.href);
  if (startPath && startPath === returnChat()) {
    setReturnChat(null);
    reopenChat(startPath);
  }

  ['pushState', 'replaceState'].forEach(function (name) {
    var original = history[name];
    history[name] = function () {
      var result = original.apply(this, arguments);
      check();
      return result;
    };
  });
  window.addEventListener('popstate', check);
  // In case the page kept a reference to the original pushState.
  setInterval(check, 1000);

  // Back in the app (MessagesLayer), as the desktop site does it at phone width:
  // - a photo open in the viewer closes (layer_photo_viewer.js);
  // - in a chat, the chat's own Back button (its header's top-left arrow) shows the chat list;
  //   stepping back a page left "No chats selected" instead (seen on a Galaxy A56);
  // - on the chat list, the layer closes ('list');
  // - anywhere else (a profile opened from a chat) the layer steps back a page ('page').
  // Answers what it did: 'photo', 'chat', 'list' or 'page'.
  // The chat header's Back button: an icon button below Facebook's own header bar (56 px), at the
  // left, with the chat's name (a link) right next to it on the same row. The name tells it from
  // the chat list's search icon, which sits there too (Back on the list pressed it, and the list
  // stayed; seen on a Galaxy A56). In an end-to-end encrypted chat the name can be a plain heading
  // (the account's chat with itself, 2026-10-24; Back closed the layer): a heading counts while a
  // chat's message box is on screen, which the chat list never has.
  function chatOpen() {
    var boxes = document.querySelectorAll('[role="textbox"][contenteditable="true"]');
    for (var i = 0; i < boxes.length; i++) {
      var r = boxes[i].getBoundingClientRect();
      if (r.width > 0 && r.height > 0) return true;
    }
    return false;
  }

  function chatBackButton() {
    var buttons = document.querySelectorAll('[role="button"]');
    var names = 'a[href], [role="link"]' + (chatOpen() ? ', h1, h2, h3, [role="heading"]' : '');
    var links = document.querySelectorAll(names);
    for (var i = 0; i < buttons.length; i++) {
      var b = buttons[i], r = b.getBoundingClientRect();
      if (r.width < 20 || r.width > 56 || r.left < 0 || r.left > 80 || r.top < 56 || r.top > 200) continue;
      if (!b.querySelector('svg')) continue;
      // On screen, not just laid out: with the chat list showing, the chat's header is still there
      // underneath, its call buttons where the Back button would be (one of them was pressed).
      var hit = document.elementFromPoint(r.left + r.width / 2, r.top + r.height / 2);
      if (!hit || !b.contains(hit)) continue;
      for (var j = 0; j < links.length; j++) {
        var l = links[j].getBoundingClientRect();
        var middle = l.top + l.height / 2;
        if (l.width > 0 && l.left >= r.right - 4 && l.left <= r.right + 60 && middle >= r.top && middle <= r.bottom) return b;
      }
    }
    return null;
  }

  window.__astryxLayerBack = function () {
    if (window.__astryxClosePhotoViewer && window.__astryxClosePhotoViewer()) return 'photo';
    if (location.pathname.indexOf('/messages') !== 0) return 'page';
    var back = chatBackButton();
    if (!back) return 'list';
    back.click();
    return 'chat';
  };
})();
