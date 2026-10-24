(function () {
  if (window.__astryxPipObserverInstalled) return;
  window.__astryxPipObserverInstalled = true;

  // Keep the last real active video after Android pauses it during PiP entry.
  // PiP focus mode runs after that pause; without this reference it can select
  // another visible, paused reel by viewport area.
  window.__astryxLastActiveVideo = null;

  // A video counts as playing once it can play on (readyState 3+), so one that hasn't loaded yet
  // isn't picked. One that has played and stalls for data (readyState 2 or less, still not paused)
  // keeps counting for STALL_GRACE_MS after it last played: Home during a brief buffering stall on
  // a slow network opened no PiP (the daily live check, issues #59, #61 and #66). A paused or
  // ended video stops counting at once.
  var STALL_GRACE_MS = 2000;
  var progress = new WeakMap(); // video -> { time: its currentTime, at: when it last played }

  function notePlayback(v, now) {
    var p = progress.get(v);
    if (!p) {
      p = { time: v.currentTime, at: 0 };
      progress.set(v, p);
    }
    if (v.paused || v.ended || v.readyState === 0) {
      p.at = 0;
    } else if (v.readyState > 2 || v.currentTime !== p.time) {
      p.at = now;
    }
    p.time = v.currentTime;
    return p;
  }

  function isPlaying(v, now) {
    var p = notePlayback(v, now);
    if (v.paused || v.ended || v.muted || v.volume <= 0) return false;
    return v.readyState > 2 || (p.at > 0 && now - p.at < STALL_GRACE_MS);
  }

  function getActiveVideo() {
    var bestVideo = null;
    var bestScore = -1;
    var videos = document.querySelectorAll('video');
    var now = Date.now();

    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      if (!isPlaying(v, now)) {
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

    if (bestVideo) {
      window.__astryxLastActiveVideo = bestVideo;
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

  // Why no video counts as playing, for the log (each video's first failing check): a reel that
  // played on screen was once reported paused right after PiP closed, and Home then opened no PiP.
  function whyNotPlaying() {
    var videos = document.querySelectorAll('video');
    var out = [];
    for (var i = 0; i < videos.length && i < 6; i++) {
      var v = videos[i];
      var r = v.getBoundingClientRect();
      var why = v.paused ? 'paused' : v.ended ? 'ended' : v.muted ? 'muted' : v.volume <= 0 ? 'volume0'
        : !isPlaying(v, Date.now()) ? 'ready' + v.readyState : (r.width <= 0 || r.height <= 0) ? 'nosize'
        : (r.bottom <= 0 || r.top >= window.innerHeight || r.right <= 0 || r.left >= window.innerWidth) ? 'offscreen' : 'ok';
      out.push('v' + i + ':' + why);
    }
    return out.length ? out.join(' ') : 'novideo';
  }

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

      // AstryxPage: the app's Facebook-only message listener; PipBridge: older WebViews.
      if (window.AstryxPage) {
        window.AstryxPage.postMessage(JSON.stringify({ call: 'videoState', playing: playing, width: videoWidth, height: videoHeight,
          why: playing ? '' : whyNotPlaying() }));
      } else if (window.PipBridge && window.PipBridge.setVideoState) {
        window.PipBridge.setVideoState(playing, videoWidth, videoHeight);
      } else if (window.PipBridge && window.PipBridge.setVideoPlaying) {
        window.PipBridge.setVideoPlaying(playing);
      }
    }
  }

  var trackedVideos = new WeakSet();
  var EVENTS = ['play', 'pause', 'ended', 'volumechange', 'loadedmetadata', 'emptied', 'waiting', 'playing'];

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
  observer.observe(document.body, {
    childList: true,
    subtree: true,
    // Facebook's reels player likely reuses the same <video> element across
    // swipes rather than creating a fresh one each time (consistent with its
    // MSE/DASH-based internals), swapping the underlying content without
    // necessarily firing a real pause/play transition on the element - our
    // EVENTS listeners never fire for that, so childList alone (new nodes
    // only) can miss a reel swap entirely, leaving detection to fall back to
    // the slow poll below. Watching src changes on existing elements too
    // catches that content-swap case directly, near-instantly.
    attributes: true,
    attributeFilter: ['src']
  });

  // Backstop for whatever the event listeners and the src-attribute watch
  // above still miss. Was 3000ms - field-tested and found to directly
  // explain a multi-second lag before PiP would trigger on a freshly
  // scrolled-to reel (immediate PiP attempt failed, but worked "after a few
  // seconds", matching the poll interval). Tightened significantly; a
  // getBoundingClientRect() + a few property reads over 1-3 videos is cheap
  // enough to run this often.
  setInterval(reportState, 300);
})();
