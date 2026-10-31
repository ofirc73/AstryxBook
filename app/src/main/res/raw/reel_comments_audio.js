// A reel's sound goes on while its comments are open, and Back picks the reel up where the sound
// got to. Main view only (the Messages layer's desktop reels show comments beside the reel).
//
// m.facebook.com opens a reel's comments as a new screen in the same document (pushState, at
// Home's address "/"): it pauses the reel and removes its <video>, and Back draws the reel again
// from its start. So when the comment button of a playing, unmuted reel is tapped, this plays the
// reel's MP4 (the player's data-video-url, as download_content.js and the lock-screen hand-off
// use; the <video> itself plays a blob:) in an <audio> of its own, from where the reel stopped.
// Back to the reel's address: the reel that comes back is moved to the sound's position and the
// sound stops. Going anywhere else from the comments, or the page being hidden, stops it.
//
// The comment button is found by its icon, the same in every language (the label is
// translated), or by its English label. ES5, like the PiP scripts.
(function () {
  if (window.__astryxReelCommentsAudio || window.__astryxMessagesLayer) return;

  var COMMENT_ICON = '\uDB80\uDF79'; // U+F0379, the reel's comment button
  var COMMENT_LABEL = 'Comment on reel';
  var OPEN_WAIT_MS = 3000;
  var BACK_WAIT_MS = 8000;
  var AWAY_MS = 1000;
  var POLL_MS = 100;

  // The reel being heard: its page, its MP4, the <audio> playing it.
  var heard = null;

  // Back lands on the reel's path first; Facebook adds its query a moment later.
  function onReel() {
    return window.location.pathname === heard.reelPath;
  }

  function isReelPage() {
    return /^\/reels?\//.test(window.location.pathname);
  }

  function hasCommentIcon(button) {
    return (button.textContent || '').indexOf(COMMENT_ICON) >= 0;
  }

  function isCommentButton(button) {
    return (button.getAttribute('aria-label') || '').indexOf(COMMENT_LABEL) === 0 || hasCommentIcon(button);
  }

  // For the tests and the live checks: the <audio> playing while the comments are open.
  window.__astryxReelCommentsAudio = {
    audio: function () { return heard && heard.audio; },
    hasCommentIcon: hasCommentIcon
  };

  // The reel playing on screen (the reels scroller keeps the ones next to it too).
  function playingReel() {
    var videos = document.querySelectorAll('video');
    var best = null, bestArea = 0;
    for (var i = 0; i < videos.length; i++) {
      var v = videos[i];
      if (v.paused || v.ended) continue;
      var r = v.getBoundingClientRect();
      var area = Math.max(0, Math.min(r.bottom, window.innerHeight) - Math.max(r.top, 0)) *
                 Math.max(0, Math.min(r.right, window.innerWidth) - Math.max(r.left, 0));
      if (area > bestArea) { bestArea = area; best = v; }
    }
    return best;
  }

  function stop() {
    if (!heard) return;
    var audio = heard.audio;
    heard = null;
    if (audio) {
      audio.pause();
      audio.removeAttribute('src');
      audio.load();
    }
  }

  function startAudio(src, time) {
    var audio = new Audio();
    audio.preload = 'auto';
    audio.loop = true;
    audio.addEventListener('loadedmetadata', function () {
      audio.currentTime = isFinite(audio.duration) && time < audio.duration ? time : 0;
    });
    audio.src = src;
    var p = audio.play();
    if (p && p.catch) p.catch(function () {});
    return audio;
  }

  // Back on the reel's page: the reel Facebook draws again starts from 0. It's moved to where the
  // sound got to as it starts playing, and the sound stops.
  function handBack(video) {
    var audio = heard.audio;
    var time = audio ? audio.currentTime : heard.time;
    stop();
    if (isFinite(video.duration) && time >= video.duration - 0.5) time = 0;
    video.currentTime = time;
  }

  function watch() {
    var started = Date.now();
    var timer = setInterval(function () {
      if (!heard) { clearInterval(timer); return; }
      var href = window.location.href;
      var now = Date.now();
      if (!heard.audio) {
        // Waiting for the comments to open: the reel's <video> goes when they do.
        if (document.documentElement.contains(heard.video)) {
          if (now - started > OPEN_WAIT_MS) { clearInterval(timer); heard = null; }
          return;
        }
        if (isReelPage()) { clearInterval(timer); heard = null; return; }
        heard.time = heard.video.currentTime;
        heard.video = null;
        heard.commentsHref = href;
        heard.audio = startAudio(heard.src, heard.time);
        return;
      }
      var onComments = href === heard.commentsHref;
      // Back passes through /watch/ for a moment on its way to the reel: only a page that stays
      // stops the sound.
      if (onComments || onReel()) heard.awaySince = 0;
      else if (!heard.awaySince) heard.awaySince = now;
      else if (now - heard.awaySince > AWAY_MS) { clearInterval(timer); stop(); return; }
      // Back on the reel: the 'play' listener below hands over; this gives up if no reel plays.
      if (!onReel()) heard.backSince = 0;
      else if (!heard.backSince) heard.backSince = now;
      else if (now - heard.backSince > BACK_WAIT_MS) { clearInterval(timer); stop(); }
    }, POLL_MS);
  }

  document.addEventListener('click', function (e) {
    if (!isReelPage() || !e.target || !e.target.closest) return;
    var button = e.target.closest('[role="button"]');
    if (!button || !isCommentButton(button)) return;
    var video = playingReel();
    var player = video && video.closest('[data-video-url]');
    var src = player && player.getAttribute('data-video-url');
    if (!video || video.muted || !src || src.indexOf('https://') !== 0) return;
    stop();
    heard = { reelPath: window.location.pathname, src: src, video: video, time: video.currentTime, audio: null };
    watch();
  }, true);

  // Media events don't bubble, but a capture listener on the document sees them.
  // Back fires the reel's 'play' while still at /watch/, its 'playing' at the reel's path.
  function onReelPlay(e) {
    if (!heard || !heard.audio || e.target.tagName !== 'VIDEO') return;
    if (onReel()) handBack(e.target);
  }
  document.addEventListener('play', onReelPlay, true);
  document.addEventListener('playing', onReelPlay, true);

  document.addEventListener('visibilitychange', function () {
    if (!heard || !heard.audio) return;
    if (document.visibilityState === 'hidden') {
      heard.audio.pause();
    } else {
      var p = heard.audio.play();
      if (p && p.catch) p.catch(function () {});
    }
  });
})();
