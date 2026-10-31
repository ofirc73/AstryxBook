// Gets a video playing in step with a sound that plays elsewhere: the reel back from its comments
// with the comments' sound (reel_comments_audio.js), the PiP video back from the lock screen with
// the native player (pipHandbackJs). Each makes its own instance, with its own clock and its own
// remembered seek time: nothing is shared between them but this code. The caller keeps the video
// silent until it's in step, and decides what then (the sound handing over).
//
//   var c = window.__astryxCatchUp(video, clock, { leadKey: '...', firstLead: 0.3 });
//   c.hold();            // still to the eye while it loads
//   c.move(c.target());  // moved past the sound by the seek time (false: no seek needed)
//   c.place(t);          // [t], or the start instead of the very end
//   c.step();            // each poll: 'wait', 'inStep' or 'apart' (too far to catch up)
//   c.release();         // its own speed again
//
// clock() is where the sound is now (s, in the video's time). ES5, like the PiP scripts.
(function () {
  if (window.__astryxCatchUp) return;

  // Chromium's slowest speed: still to the eye. Played from its start while Facebook loaded it,
  // then moved, the reel was seen to jump.
  var HOLD_RATE = 0.0625;
  // In step this close: the sound hands over.
  var IN_STEP_S = 0.1;
  // Up to AHEAD_S ahead of the sound the video plays slower, up to BEHIND_S behind faster, by the
  // gap per second (a little slower or faster for a second or so; at 0.5x or 1.5x until in step,
  // the video was seen in slow motion on the phone). Past HOLD_AHEAD_S ahead it's held still until
  // the sound gets there: a moment's stop rather than slow motion (a seek took 1-3 s on the
  // emulator, each time different). Further apart, 'apart': the caller lets the video take over.
  var AHEAD_S = 4;
  var BEHIND_S = 2;
  var HOLD_AHEAD_S = 0.5;
  var MAX_LEAD_S = 4;

  window.__astryxCatchUp = function (video, clock, options) {
    // How long the last moves took to play (averaged: it varies from one seek to the next), kept
    // under the caller's own key: the reel's seek within what it has loaded and the PiP video's
    // after a lock take different times.
    var lead = options.firstLead;
    try { lead = parseFloat(localStorage.getItem(options.leadKey)) || lead; } catch (e) {}
    var movedAt = 0;

    function length() { return isFinite(video.duration) && video.duration > 0 ? video.duration : Infinity; }

    return {
      hold: function () { video.playbackRate = HOLD_RATE; },

      // The start instead of the very end, which Facebook takes for a finished video (it closes
      // the viewer, or the reel stops).
      place: function (t) { return t >= length() - 0.5 ? 0 : t; },

      // Past the sound by the seek time.
      target: function () { return this.place((clock() + lead) % length()); },

      move: function (time) {
        video.playbackRate = 1;
        if (Math.abs(video.currentTime - time) <= 1) return false;
        movedAt = Date.now();
        video.currentTime = time;
        return true;
      },

      step: function () {
        if (video.paused || video.seeking || video.readyState < 3) return 'wait';
        if (movedAt) {
          lead = Math.min(MAX_LEAD_S, Math.max(0.1, (lead + (Date.now() - movedAt) / 1000) / 2));
          try { localStorage.setItem(options.leadKey, String(lead)); } catch (e) {}
          movedAt = 0;
        }
        var ahead = video.currentTime - clock();
        if (Math.abs(ahead) <= IN_STEP_S) return 'inStep';
        if (ahead >= AHEAD_S || ahead <= -BEHIND_S) return 'apart';
        video.playbackRate = ahead > HOLD_AHEAD_S ? HOLD_RATE :
          Math.min(1.5, Math.max(0.5, Math.round((1 - ahead) * 20) / 20));
        return 'wait';
      },

      release: function () { video.playbackRate = 1; }
    };
  };
})();
