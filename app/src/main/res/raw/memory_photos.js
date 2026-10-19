// Memories in the feed ("5 years ago"): the card shows a post's photos, but each one it opens is
// on its own (photo.php?fbid=..., "1 of 1"), so the others can't be paged through. When a photo
// tile of a card with several photos opens a bare photo.php, the app looks up the photo's post
// (AstryxPage call "photoPost", answered with {fbid, url, index}; PhotoPost.kt) and this script
// opens the post in its place, then taps the same photo there: the post's photos, one under
// another, starting at it (what tapping a photo of the post itself does).
// A post's own photo tiles open that list in the page, never photo.php, so only memories (and
// any other card that opens its photos alone) get here. ES5 on purpose, like the PiP scripts.
(function () {
  var page = window.AstryxPage;
  if (!page || window.__astryxMemoryPhotos) return;
  window.__astryxMemoryPhotos = true;

  var KEY = 'astryxPhotoPost';
  var TAP_KEY = 'astryxMemoryTap';
  var TAP_MS = 8000; // from the tap to the photo's address (a new page takes longer)
  var OPEN_MS = 30000; // from the lookup to the post's page
  var FIND_MS = window.__astryxMemoryFindMs || 15000; // tests shorten it
  var MIN_TILE = 60;
  var SETTLE_MS = window.__astryxMemorySettleMs || 300;
  var KEEP_MS = 6000;
  var LIST_TOP = 48; // below the list's bar (Back)
  var go = window.__astryxMemoryGo || function (url) { location.replace(url); }; // tests catch it

  // A feed card's photo tiles: innermost actions holding a picture big enough to be a photo (not
  // the reactions row, whose action is as big but holds small icons).
  function tiles(card) {
    var out = [];
    var actions = card.querySelectorAll('[data-action-id]');
    for (var i = 0; i < actions.length; i++) {
      var a = actions[i];
      if (a.querySelector('[data-action-id]')) continue;
      var images = a.querySelectorAll('img');
      for (var j = 0; j < images.length; j++) {
        var r = images[j].getBoundingClientRect();
        if (r.width >= MIN_TILE && r.height >= MIN_TILE) {
          out.push(a);
          break;
        }
      }
    }
    return out;
  }

  function cardOf(el) {
    return el && el.closest ? el.closest('[data-tracking-duration-id]') : null;
  }

  // The tap is also kept for the page: soon after the feed loads, Facebook may open the photo as
  // a new page (a full load) rather than in this one, and the photo's page then asks.
  var tappedAt = 0;
  var asked = null;

  function store(key, value) {
    try {
      if (value === null) sessionStorage.removeItem(key);
      else sessionStorage.setItem(key, value);
    } catch (e) { /* no storage: the tap only counts in this page */ }
  }

  document.addEventListener('click', function (event) {
    var action = event.target && event.target.closest ? event.target.closest('[data-action-id]') : null;
    var card = cardOf(action);
    tappedAt = card && tiles(card).length >= 2 ? Date.now() : 0;
    store(TAP_KEY, tappedAt ? String(tappedAt) : null);
  }, true);

  function check() {
    var at = tappedAt;
    if (!at) {
      try { at = Number(sessionStorage.getItem(TAP_KEY)) || 0; } catch (e) { at = 0; }
    }
    if (!at || Date.now() - at > TAP_MS) return;
    if (location.pathname !== '/photo.php' || /[?&]set=/.test(location.search)) return;
    var m = /[?&]fbid=(\d+)/.exec(location.search);
    if (!m) return;
    tappedAt = 0;
    store(TAP_KEY, null);
    asked = { fbid: m[1], at: Date.now() };
    page.postMessage(JSON.stringify({ call: 'photoPost', fbid: m[1] }));
  }

  page.addEventListener('message', function (event) {
    var answer;
    try { answer = JSON.parse(event.data); } catch (e) { return; }
    if (!answer || !asked || answer.fbid !== asked.fbid || !answer.url) return;
    asked = null;
    // Only while the photo is still open: the user may have closed it meanwhile.
    if (location.search.indexOf('fbid=' + answer.fbid) < 0) return;
    try {
      sessionStorage.setItem(KEY, JSON.stringify({ index: answer.index, at: Date.now() }));
    } catch (e) { return; }
    // In the photo's place, so Back returns to the memory.
    go(answer.url);
  });

  ['pushState', 'replaceState'].forEach(function (name) {
    var original = history[name];
    history[name] = function () {
      var result = original.apply(this, arguments);
      check();
      return result;
    };
  });
  window.addEventListener('popstate', check);
  check(); // a photo opened as a new page

  // The post's page (m.facebook.com shows it at the top of the feed, "From your link"): tap the
  // photo the memory opened.
  var pending = null;
  try {
    pending = JSON.parse(sessionStorage.getItem(KEY) || 'null');
    sessionStorage.removeItem(KEY);
  } catch (e) { pending = null; }
  if (!pending || Date.now() - pending.at > OPEN_MS) return;

  var started = Date.now();
  var seen = false;
  (function open() {
    var cards = document.querySelectorAll('[data-tracking-duration-id]');
    for (var i = 0; i < cards.length; i++) {
      var photos = tiles(cards[i]);
      if (photos.length < 2) continue;
      // Once the post is there, a moment for the page to finish: tapped earlier, the list
      // opens at its first photo.
      if (!seen) {
        seen = true;
        setTimeout(open, SETTLE_MS);
        return;
      }
      var k = Math.max(0, Math.min(pending.index | 0, photos.length - 1));
      photos[k].click();
      if (k > 0) keepAt(cards[i], k);
      return;
    }
    if (Date.now() - started < FIND_MS) setTimeout(open, 300);
  })();

  // The list opens at the photo, but while it's still loading Facebook redraws it at its top. In
  // the list each photo is a card of its own, the post's first (with the post's text) first: once
  // the list has replaced the post's card, bring the photo back to the top a few times, until the
  // user touches the page.
  function keepAt(postCard, k) {
    var until = Date.now() + KEEP_MS;
    var touched = false;
    window.addEventListener('touchstart', function () { touched = true; }, { capture: true, once: true });
    (function again() {
      if (touched || Date.now() > until) return;
      if (!document.contains(postCard)) {
        var card = document.querySelectorAll('[data-tracking-duration-id]')[k];
        if (card) {
          var r = card.getBoundingClientRect();
          // (At the list's end it can't go higher; scrollBy then does nothing.)
          if (Math.abs(r.top - LIST_TOP) > 40) window.scrollBy(0, r.top - LIST_TOP);
        }
      }
      setTimeout(again, 250);
    })();
  }
})();
