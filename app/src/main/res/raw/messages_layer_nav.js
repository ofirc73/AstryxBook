// Messages layer only. The desktop site moves between its pages in-page (history.pushState)
// rather than loading them, which the layer's request interceptor never sees. Report every
// URL change to LayerBridge, which closes the layer when it's one of Facebook's own sections
// (messagesLayerExit). ES5, like the PiP scripts.
(function () {
  if (window.__astryxLayerNavInstalled || !window.LayerBridge) return;
  window.__astryxLayerNavInstalled = true;

  var last = location.href;
  function check() {
    if (location.href === last) return;
    last = location.href;
    window.LayerBridge.onUrlChanged(last);
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
})();
