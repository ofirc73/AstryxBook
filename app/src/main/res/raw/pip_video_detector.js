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

  // Event-driven rather than a tight poll: real play/pause/volumechange/
  // loadedmetadata listeners respond instantly instead of waiting up to a
  // full poll interval. MutationObserver catches new <video> elements added
  // by infinite scroll (reels), which a one-time querySelectorAll would
  // miss. A slow 3s interval remains only as a backstop for edge cases
  // (e.g. a video removed+re-added without a fresh DOM node), not the
  // primary mechanism anymore.
  var lastState = null;
  var lastWidth = 0;
  var lastHeight = 0;

  function reportState() {
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
  }

  var trackedVideos = new WeakSet();
  var EVENTS = ['play', 'pause', 'ended', 'volumechange', 'loadedmetadata', 'emptied'];

  function trackVideo(v) {
    if (trackedVideos.has(v)) return;
    trackedVideos.add(v);
    for (var i = 0; i < EVENTS.length; i++) {
      v.addEventListener(EVENTS[i], reportState);
    }
  }

  function scanForVideos() {
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      trackVideo(videos[i]);
    }
  }

  scanForVideos();
  reportState();

  var observer = new MutationObserver(function () {
    scanForVideos();
    reportState();
  });
  observer.observe(document.body, { childList: true, subtree: true });

  setInterval(reportState, 3000);
})();
