// Messages layer only. The desktop site moves between its pages in-page (history.pushState)
// rather than loading them, which the layer's request interceptor never sees. Report every
// URL change to the app (AstryxPage, or LayerBridge on older WebViews), which closes the layer when it's one of Facebook's own sections
// (messagesLayerExit). ES5, like the PiP scripts.
(function () {
  if (window.__astryxLayerNavInstalled || !(window.AstryxPage || window.LayerBridge)) return;
  window.__astryxLayerNavInstalled = true;

  var last = location.href;
  function check() {
    if (location.href === last) return;
    last = location.href;
    if (window.AstryxPage) {
      window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url: last }));
    } else {
      window.LayerBridge.onUrlChanged(last);
    }
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
  window.__astryxLayerBack = function () {
    if (window.__astryxClosePhotoViewer && window.__astryxClosePhotoViewer()) return 'photo';
    if (location.pathname.indexOf('/messages') !== 0) return 'page';
    var buttons = document.querySelectorAll('[role="button"]');
    for (var i = 0; i < buttons.length; i++) {
      var b = buttons[i], r = b.getBoundingClientRect();
      // Below Facebook's own header bar (56 px), at the chat header's left, an icon button.
      if (r.width < 20 || r.width > 56 || r.left < 0 || r.left > 80 || r.top < 56 || r.top > 200) continue;
      if (!b.querySelector('svg')) continue;
      b.click();
      return 'chat';
    }
    return 'list';
  };
})();
