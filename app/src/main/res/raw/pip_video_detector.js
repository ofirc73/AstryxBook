(function () {
  if (window.__astryxPipObserverInstalled) return;
  window.__astryxPipObserverInstalled = true;

  function getActiveVideo() {
    var bestVideo = null;
    var bestScore = -1;
    var videos = document.querySelectorAll('video');

    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      if (v.paused || v.ended || v.readyState <= 2 || v.muted || v.volume <= 0) {
        continue;
      }

      var rect = v.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) {
        continue;
      }

      var viewportOverlapLeft = Math.max(0, Math.min(rect.right, window.innerWidth) - Math.max(rect.left, 0));
      var viewportOverlapTop = Math.max(0, Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0));
      var visiblePixels = viewportOverlapLeft * viewportOverlapTop;
      if (visiblePixels <= 0) {
        continue;
      }

      var score = visiblePixels;
      if (rect.top >= 0 && rect.bottom <= window.innerHeight) {
        score += 1000;
      }

      if (score > bestScore) {
        bestVideo = v;
        bestScore = score;
      }
    }

    return bestVideo;
  }

  // Polling rather than per-video event listeners: Facebook's feed constantly
  // adds new <video> elements as the user scrolls (infinite scroll, reels),
  // so a one-time querySelectorAll + listener attachment would miss anything
  // loaded after the initial scan. A 1s poll is simple, catches everything,
  // and only fires the bridge when the currently active video state changes.
  var lastState = null;
  var lastWidth = 0;
  var lastHeight = 0;

  setInterval(function () {
    var v = getActiveVideo();
    var playing = !!v;
    var videoWidth = v ? v.videoWidth : 0;
    var videoHeight = v ? v.videoHeight : 0;
    var stateChanged = playing !== lastState || videoWidth !== lastWidth || videoHeight !== lastHeight;

    if (stateChanged) {
      lastState = playing;
      lastWidth = videoWidth;
      lastHeight = videoHeight;

      if (window.PipBridge && window.PipBridge.setVideoState) {
        window.PipBridge.setVideoState(playing, videoWidth, videoHeight);
      } else if (window.PipBridge && window.PipBridge.setVideoPlaying) {
        window.PipBridge.setVideoPlaying(playing);
      }
    }
  }, 1000);
})();
