// A reel's sound goes on while its comments are open, and Back picks the reel up where the sound
// got to, without a break either way. Main view only (the Messages layer's desktop reels show
// comments beside the reel).
//
// m.facebook.com opens a reel's comments as a new screen in the same document (pushState, at
// Home's address "/"): about a second after the tap it pauses the reel, sets it back to 0 and
// removes its <video>, and Back draws the reel again from its start. So when the comment button
// of a playing, unmuted reel is tapped, this fetches the reel's sound (the audio track the player
// streams, soundUrl(); the <video> itself plays a blob:), decodes it (Web Audio) and plays it silently in step with the reel; when Facebook calls
// the reel's pause() it's faded in, at once (v1.39.2 started it only then, after noticing the reel
// had stopped: a short cut, on the phone). An <audio> took 1-8 s on the emulator to play after a
// seek, even from memory; a decoded sound starts at the exact spot (fetched and decoded in about a
// second there, about when Facebook stops the reel; when later, it starts as soon as it can). Back: the reel that comes back is silenced, moved a little past the
// sound (seeking takes it a moment), then played a little slower or faster until it's in step
// with the sound; then it's heard again and the sound stops. Silenced through Web Audio (its sound
// routed through a gain of 0), not muted: muted, Facebook's player stalled loading it, until it
// was unmuted. And moved only once, as it starts playing: a seek after that stalled Facebook's
// player, past what it had loaded for good. Going anywhere else from the comments
// stops the sound; the page hidden pauses it.
//
// The comment button is found by its icon, the same in every language (the label is
// translated), or by its English label. ES5, like the PiP scripts.
(function () {
  if (window.__astryxReelCommentsAudio || window.__astryxMessagesLayer) return;

  var COMMENT_ICON = '\uDB80\uDF79'; // U+F0379, the reel's comment button
  var COMMENT_LABEL = 'Comment on reel';
  var OPEN_WAIT_MS = 3000;
  // Facebook took 2-8 s on the emulator to load the reel back; the sound plays on meanwhile.
  var BACK_WAIT_MS = 20000;
  var AWAY_MS = 1000;
  var POLL_MS = 50;
  // Off from the reel by more than this, the silent sound is started again at the reel's time.
  var DRIFT_S = 0.05;
  // How far past the sound the reel coming back is moved: what its seek took last time (it's
  // ready at the sound's position then), at first BACK_LEAD_S.
  var BACK_LEAD_S = 0.8;
  var MAX_LEAD_S = 4;
  var backLead = BACK_LEAD_S;
  // The reel back plays at the sound's position when this close: the sound hands over.
  var IN_STEP_S = 0.08;
  // Up to this far ahead of the sound, the reel back plays slower to let it catch up; up to
  // BEHIND_S behind, faster. Further apart, the reel just takes over.
  var AHEAD_S = 1;
  var BEHIND_S = 1.5;
  // A sound fetched for a touch that brought no tap is let go after this.
  var EARLY_MS = 5000;

  // The reel being heard: its page, its <video> (until Facebook stops it), where it was and when,
  // its decoded sound (once fetched) and where that plays, and the reel that comes back. phase is
  // 'opening' (the reel still plays), 'comments' (the sound plays) or 'back' (the reel back,
  // silenced, getting in step with the sound).
  var heard = null;
  // One Web Audio context for the page (Chrome allows only a few), suspended while nothing plays
  // through it.
  var context = null;
  // The reels whose sound goes through it (silence()).
  var routed = [];
  // The last sound fetched, from the touch on the comment button: { src, sound (a promise) }.
  var early = null;

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

  // Where the sound is (s), -1 while none plays.
  function soundAt() {
    if (!heard || !heard.source) return -1;
    var t = heard.offset + context.currentTime - heard.startedAt;
    return t % heard.sound.duration;
  }

  // For the tests and the live checks.
  window.__astryxReelCommentsAudio = {
    position: soundAt,
    hasCommentIcon: hasCommentIcon,
    soundUrl: soundUrl
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

  // Where the reel is: its own time while it plays, then counted on from there.
  function reelTime() {
    var t = heard.time + (heard.video ? 0 : (Date.now() - heard.timeAt) / 1000);
    return t % heard.sound.duration;
  }

  // The sound from [at] s, heard or (while the reel still plays) silent.
  function play(at, heardNow) {
    if (heard.source) heard.source.stop();
    if (context.state === 'suspended') context.resume();
    var gain = context.createGain();
    gain.gain.value = heardNow ? 1 : 0;
    gain.connect(context.destination);
    var source = context.createBufferSource();
    source.buffer = heard.sound;
    source.loop = true;
    source.connect(gain);
    source.start(0, at);
    heard.source = source;
    heard.volume = gain;
    heard.played = heard.played || heardNow;
    heard.offset = at;
    heard.startedAt = context.currentTime;
  }

  // The silent sound heard, faded in over about 10 ms (no click).
  function fadeIn() {
    heard.played = true;
    heard.volume.gain.setTargetAtTime(1, context.currentTime, 0.01);
  }

  // The context suspended when neither the sound nor a reel routed through it plays: a running
  // one keeps the device's audio output open.
  function rest() {
    if (!context || (heard && heard.source)) return;
    routed = routed.filter(function (v) { return document.documentElement.contains(v); });
    for (var i = 0; i < routed.length; i++) if (!routed[i].paused) return;
    context.suspend();
  }

  function pause() {
    if (!heard.source) return;
    heard.offset = soundAt();
    heard.source.stop();
    heard.source = null;
    rest();
  }

  function stop() {
    if (!heard) return;
    if (heard.video) delete heard.video.pause;
    if (heard.source) heard.source.stop();
    if (heard.back) heard.back.playbackRate = 1;
    if (heard.silencer) heard.silencer.gain.value = 1;
    heard = null;
    rest();
  }

  // Once decoded, the sound plays silently with the reel while it plays, and heard once the reel
  // has stopped.
  function playWhenReady() {
    if (!heard.sound || heard.source || document.visibilityState === 'hidden') return;
    if (heard.phase === 'comments') play(reelTime(), true);
    else if (!heard.video.paused) play(heard.video.currentTime % heard.sound.duration, false);
  }

  // The reel's decoded sound, fetched from the touch on the button (the tap's click comes about
  // 0.1 s later) and kept for the tap.
  function fetchSound(src) {
    if (early && early.src === src) return early.sound;
    if (!context) context = new (window.AudioContext || window.webkitAudioContext)();
    var mine = early = { src: src };
    mine.sound = fetch(src).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.arrayBuffer();
    }).then(function (data) {
      return context.decodeAudioData(data);
    });
    setTimeout(function () { if (early === mine && !heard) early = null; }, EARLY_MS);
    return mine.sound;
  }

  function load(src) {
    var mine = heard;
    fetchSound(src).then(function (sound) {
      early = null;
      if (heard !== mine) return;
      heard.sound = sound;
      playWhenReady();
    }, function () {
      early = null;
      // No sound to play: the comments stay quiet, as Facebook has them.
      if (heard === mine) stop();
    });
  }

  // Facebook stopped the reel (paused it, or took it off the page): the sound takes over.
  function reelStopped() {
    if (!heard || heard.phase !== 'opening') return;
    delete heard.video.pause;
    heard.phase = 'comments';
    heard.video = null;
    heard.timeAt = Date.now();
    if (!heard.source) { playWhenReady(); return; }
    if (Math.abs(soundAt() - reelTime()) > DRIFT_S) play(reelTime(), true);
    else fadeIn();
  }

  // The reel still playing: its position kept (Facebook sets it back to 0 on the way out), and the
  // silent sound kept in step with it.
  function track() {
    var v = heard.video;
    if (!document.documentElement.contains(v) || (v.paused && !onReel())) { reelStopped(); return; }
    if (v.paused || v.currentTime <= heard.time) return;
    heard.time = v.currentTime;
    heard.timeAt = Date.now();
    if (heard.source && Math.abs(soundAt() - reelTime()) > DRIFT_S) play(reelTime(), false);
    else playWhenReady();
  }

  // The reel back, as it starts playing from 0: moved once a little past the sound, again only if
  // Facebook starts it from 0 again (twice at most).
  function moveBack() {
    var v = heard.back;
    if (!onReel() || !heard.source || heard.moves >= 2) return;
    if (heard.placed && v.currentTime > 1) return;
    heard.placed = true;
    var time = (soundAt() + backLead) % heard.sound.duration;
    if (isFinite(v.duration) && time >= v.duration - 0.5) time = 0;
    if (Math.abs(v.currentTime - time) <= 1) return;
    heard.moves++;
    heard.movedAt = Date.now();
    v.currentTime = time;
  }

  // The reel back, silenced, playing: a little slower while it's ahead of the sound, faster while
  // behind, until it plays in step with it; then heard, and the sound stops. Further apart, it
  // takes over at once. Never moved again: any seek, even within what it had loaded, stalled
  // Facebook's player for seconds on the emulator.
  function caughtUp() {
    var v = heard.back;
    if (!heard.source) { stop(); return true; }
    if (v.paused || v.seeking || v.readyState < 3 || !heard.placed) return false;
    if (heard.movedAt) {
      backLead = Math.min(MAX_LEAD_S, Math.max(0.3, (Date.now() - heard.movedAt) / 1000));
      heard.movedAt = 0;
    }
    var ahead = v.currentTime - soundAt();
    if (ahead > IN_STEP_S && ahead < AHEAD_S) { v.playbackRate = 0.5; return false; }
    if (ahead < -IN_STEP_S && ahead > -BEHIND_S) { v.playbackRate = 1.5; return false; }
    stop();
    return true;
  }

  function watch() {
    var started = Date.now();
    var timer = setInterval(function () {
      if (!heard) { clearInterval(timer); return; }
      var now = Date.now();
      if (heard.phase === 'opening') {
        track();
        if (heard.phase === 'opening' && now - started > OPEN_WAIT_MS) { clearInterval(timer); stop(); }
        return;
      }
      var href = window.location.href;
      // The comments' address, once Facebook has moved there.
      if (!heard.commentsHref) {
        if (!onReel()) heard.commentsHref = href;
        return;
      }
      if (heard.back) {
        if (caughtUp()) { clearInterval(timer); return; }
        // Its 'playing' came before the reel's path did.
        if (!heard.placed && heard.back.currentTime < 2) moveBack();
      }
      // Back passes through /watch/ for a moment on its way to the reel: only a page that stays
      // stops the sound.
      if (href === heard.commentsHref || onReel()) heard.awaySince = 0;
      else if (!heard.awaySince) heard.awaySince = now;
      else if (now - heard.awaySince > AWAY_MS) { clearInterval(timer); stop(); return; }
      // Back on the reel: the listeners below hand over; this gives up if no reel gets in step.
      if (!onReel()) heard.backSince = 0;
      else if (!heard.backSince) heard.backSince = now;
      else if (now - heard.backSince > BACK_WAIT_MS) { clearInterval(timer); stop(); }
    }, POLL_MS);
  }

  // The sound the player plays: the audio track of its DASH stream (data-extra's
  // dash_prefetch_representations, a whole fragmented MP4 at its base_url). The MP4 in
  // data-video-url has the same sound, but quieter (8.7 dB on the emulator's test reel): it's the
  // fallback.
  function soundUrl(player) {
    try {
      var reps = JSON.parse(player.getAttribute('data-extra')).dash_prefetch_representations.representations;
      for (var i = 0; i < reps.length; i++) {
        if (reps[i].mime_type === 'audio/mp4' && reps[i].base_url.indexOf('https://') === 0) return reps[i].base_url;
      }
    } catch (e) {}
    return player.getAttribute('data-video-url');
  }

  // The playing reel and its sound's address when [e] is on the comment button of a reel page,
  // else null.
  function commentTap(e) {
    if (!isReelPage() || !e.target || !e.target.closest) return null;
    var button = e.target.closest('[role="button"]');
    if (!button || !isCommentButton(button)) return null;
    var video = playingReel();
    var player = video && video.closest('[data-video-url]');
    var src = player && soundUrl(player);
    if (!video || video.muted || !src || src.indexOf('https://') !== 0) return null;
    return { video: video, src: src };
  }

  document.addEventListener('pointerdown', function (e) {
    var tap = commentTap(e);
    if (tap) fetchSound(tap.src).then(null, function () {});
  }, true);

  document.addEventListener('click', function (e) {
    var tap = commentTap(e);
    if (!tap) return;
    var video = tap.video;
    stop();
    heard = { phase: 'opening', reelPath: window.location.pathname, video: video, time: video.currentTime,
      timeAt: Date.now(), sound: null, source: null, played: false, offset: 0, startedAt: 0, back: null, placed: false,
      moves: 0, movedAt: 0, silencer: null };
    load(tap.src);
    // Facebook stops the reel for the comments with its pause(): the sound comes in right then, at
    // the reel's time just before it. Only this reel's own pause, and only until it's stopped.
    video.pause = function () {
      if (heard && heard.video === video) {
        if (video.currentTime > heard.time) heard.time = video.currentTime;
        reelStopped();
      }
      return HTMLMediaElement.prototype.pause.apply(this, arguments);
    };
    // Its 'pause' event comes once the reel is off the page, where the document no longer sees it
    // (a reel that ends and starts again pauses too, on the page).
    video.addEventListener('pause', function () {
      if (heard && heard.video === video) track();
    });
    watch();
  }, true);

  // The reel's sound through a gain of 0, for good: an element's sound can't be taken back out of
  // Web Audio, so the gain is set to 1 when it's heard again. Without it (Web Audio refused the
  // element), the reel is heard from 0 for a moment, as before.
  function silence(v) {
    try {
      var gain = context.createGain();
      gain.gain.value = 0;
      context.createMediaElementSource(v).connect(gain);
      gain.connect(context.destination);
      heard.silencer = gain;
      routed.push(v);
    } catch (e) {}
  }

  // A reel routed through the context is heard only while it runs.
  document.addEventListener('play', function (e) {
    if (routed.indexOf(e.target) >= 0 && context.state === 'suspended') context.resume();
  }, true);
  document.addEventListener('pause', function (e) {
    if (routed.indexOf(e.target) >= 0) rest();
  }, true);

  // A capture listener on the document sees media events. Back fires the reel's 'play' while
  // still at /watch/ (silenced from the start), its 'playing' at the reel's path (moved on then).
  document.addEventListener('play', function (e) {
    if (!heard || heard.phase !== 'comments' || e.target.tagName !== 'VIDEO') return;
    if (!heard.commentsHref || window.location.href === heard.commentsHref) return;
    heard.phase = 'back';
    heard.back = e.target;
    silence(e.target);
  }, true);
  document.addEventListener('playing', function (e) {
    if (heard && e.target === heard.back) moveBack();
  }, true);

  document.addEventListener('visibilitychange', function () {
    if (!heard) return;
    if (document.visibilityState === 'hidden') pause();
    else if (heard.played && !heard.source) play(heard.offset, true);
    else playWhenReady();
  });
})();
