// window.__astryxSetViewport(content): every viewport <meta> of the page set to `content`, one added
// when there's none. Shared by the pages the app lays out at a width of its own in the Messages
// layer: the story editor (story_layout.js, phone width) and the reel creator (share_media.js, its
// own width); each keeps it when another script (pinch_to_zoom.js) rewrites it. Run before them.
// ES5, like the PiP scripts.
(function () {
  if (window.__astryxSetViewport) return;
  window.__astryxSetViewport = function (content) {
    var metas = document.querySelectorAll('meta[name="viewport"]');
    if (!metas.length && document.head) {
      var meta = document.createElement('meta');
      meta.name = 'viewport';
      document.head.appendChild(meta);
      metas = [meta];
    }
    for (var i = 0; i < metas.length; i++) {
      if (metas[i].getAttribute('content') !== content) metas[i].setAttribute('content', content);
    }
  };
})();
