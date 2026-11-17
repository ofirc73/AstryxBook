// Share to story pressed in the desktop story creator (Messages layer, next to story_layout.js):
// Facebook marks the Share button busy (aria-busy) while the story posts ("Posting your
// story..."), until the page moves on to the stories page (posted; the app sees that as the
// layer's URL) or the editor comes back (failed: the button no longer busy and the page still the
// creator, with Facebook's "You are currently offline" toast; seen with every request failed over
// DevTools, 2026-11-02). Reported to the app (AstryxPage call "storyPosting", state "posting" or
// "failed"), which keeps the creator going in the background and shows a notification meanwhile.
// The Share button is found inside the bottom row story_layout.js marks, never by its label.
// While it posts, the page's sound is muted: a story's music plays on in the creator's <audio>
// (seen 2026-11-17) with the layer out of sight. Muted, not paused, so Facebook's player state is
// left as it is; unmuted when posting ends (the editor back after a failure).
// ES5 on purpose, like the PiP scripts.
(function () {
  if (window.__astryxStoryPosting || !window.AstryxPage) return;
  window.__astryxStoryPosting = true;

  // The editor back a moment after the button stopped being busy: posted pages have moved on by then.
  var FAILED_AFTER_MS = 2000;
  var posting = false;
  var failCheck = null;

  function report(state) {
    window.AstryxPage.postMessage(JSON.stringify({ call: 'storyPosting', state: state }));
  }
  // The page's media muted for the posting, to be unmuted when it ends.
  var muted = [];
  function muteMedia() {
    var media = document.querySelectorAll('audio, video');
    for (var i = 0; i < media.length; i++) {
      if (!media[i].muted) {
        media[i].muted = true;
        muted.push(media[i]);
      }
    }
  }
  function unmuteMedia() {
    for (var i = 0; i < muted.length; i++) muted[i].muted = false;
    muted = [];
  }
  function inCreator() {
    return location.pathname.indexOf('/stories/create') === 0;
  }

  setInterval(function () {
    var busy = !!document.querySelector('[data-astryx-story="actions"] [aria-busy="true"]');
    // Again on every check: Facebook may put up a new element meanwhile.
    if (busy) muteMedia();
    if (busy && !posting) {
      posting = true;
      clearTimeout(failCheck);
      report('posting');
    } else if (!busy && posting) {
      posting = false;
      unmuteMedia();
      failCheck = setTimeout(function () {
        if (!posting && inCreator()) report('failed');
      }, FAILED_AFTER_MS);
    }
  }, 500);
})();
