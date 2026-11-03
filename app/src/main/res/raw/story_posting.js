// Share to story pressed in the desktop story creator (Messages layer, next to story_layout.js):
// Facebook marks the Share button busy (aria-busy) while the story posts ("Posting your
// story..."), until the page moves on to the stories page (posted; the app sees that as the
// layer's URL) or the editor comes back (failed: the button no longer busy and the page still the
// creator, with Facebook's "You are currently offline" toast; seen with every request failed over
// DevTools, 2026-11-02). Reported to the app (AstryxPage call "storyPosting", state "posting" or
// "failed"), which keeps the creator going in the background and shows a notification meanwhile.
// The Share button is found inside the bottom row story_layout.js marks, never by its label.
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
  function inCreator() {
    return location.pathname.indexOf('/stories/create') === 0;
  }

  setInterval(function () {
    var busy = !!document.querySelector('[data-astryx-story="actions"] [aria-busy="true"]');
    if (busy && !posting) {
      posting = true;
      clearTimeout(failCheck);
      report('posting');
    } else if (!busy && posting) {
      posting = false;
      failCheck = setTimeout(function () {
        if (!posting && inCreator()) report('failed');
      }, FAILED_AFTER_MS);
    }
  }, 500);
})();
