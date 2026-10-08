// "Keep screen on: while a video plays". Reports through ScreenBridge whether any video on
// the page is playing and shown, muted or not (the PiP detector only counts videos with
// sound). Event-driven, plus a slow poll for what the events miss (a video removed while
// playing). ES5, like the PiP scripts.
(function () {
  if (window.__astryxVideoPlayingInstalled || !window.ScreenBridge) return;
  window.__astryxVideoPlayingInstalled = true;

  var last = null;

  function anyPlaying() {
    var videos = document.querySelectorAll('video');
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      if (v.paused || v.ended || v.readyState <= 2) continue;
      var r = v.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) return true;
    }
    return false;
  }

  function report() {
    var playing = document.visibilityState !== 'hidden' && anyPlaying();
    if (playing !== last) {
      last = playing;
      window.ScreenBridge.setVideoPlaying(playing);
    }
  }

  ['play', 'playing', 'pause', 'ended', 'emptied'].forEach(function (name) {
    document.addEventListener(name, report, true);
  });
  document.addEventListener('visibilitychange', report);
  setInterval(report, 2000);
  report();
})();
