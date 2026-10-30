// After a post is published from Facebook's composer, opens the new post. m.facebook.com does that
// itself only for a composer opened from Home; opened from another page (a memory's Share on the
// Memories page) it goes back to that page. The site never tells the page the new post's id, so
// the app opens the user's own profile and there the newest post, by its time (as a tap would).
// Posting, unlike leaving the composer, shows a snackbar ("Uploading your post...", then
// "Posted"; read on the emulator 2026-10-30). Main view only. ES5 on purpose, like the PiP scripts.
(function () {
  if (window.__astryxMessagesLayer || window.__astryxNewPost) return;
  window.__astryxNewPost = true;

  var KEY = 'astryxOpenNewPost';
  var SNACKBAR_MS = 4000; // from leaving the composer to its snackbar
  var POSTED_MS = 10000; // longest wait for the snackbar's second text ("Posted")
  var OPEN_MS = 30000; // from going to the profile to its page
  var FIND_MS = window.__astryxNewPostFindMs || 15000; // tests shorten it
  var go = window.__astryxNewPostGo || function (url) { location.href = url; }; // tests catch it

  function store(value) {
    try {
      if (value === null) sessionStorage.removeItem(KEY);
      else sessionStorage.setItem(KEY, value);
    } catch (e) { /* no storage: nothing to open on the next page */ }
  }

  // A post's time in its header: the time between left-to-right marks, then the audience's icon
  // (an icon-font glyph); the first one on a profile is its newest post.
  function newestPostTime() {
    var texts = document.querySelectorAll('.native-text');
    for (var i = 0; i < texts.length; i++) {
      if (!/^\u200e[^\u200e]{1,24}\u200e[\uDB80-\uDBBF]/.test(texts[i].textContent || '')) continue;
      var action = texts[i].closest('[data-action-id]');
      if (!action) continue;
      var r = action.getBoundingClientRect();
      if (r.width > 0 && r.height > 0) return action;
    }
    return null;
  }
  window.__astryxNewestPostTime = newestPostTime;

  // On the profile the app opened: the newest post, once the page shows it.
  var pending = 0;
  try { pending = Number(sessionStorage.getItem(KEY)) || 0; } catch (e) { pending = 0; }
  if (pending) {
    store(null);
    if (Date.now() - pending < OPEN_MS) {
      var until = Date.now() + FIND_MS;
      (function find() {
        var time = newestPostTime();
        if (time) time.click();
        else if (Date.now() < until) setTimeout(find, 250);
      })();
    }
  }

  // The composer: the page it was opened from, and the one it went back to. Posting shows a
  // snackbar just before or just after leaving it; leaving it without posting shows none.
  var lastPath = location.pathname;
  var openedFrom = null;
  var leftAt = 0;
  var backTo = null;
  function moved() {
    var path = location.pathname;
    if (path === '/composer/' && lastPath !== '/composer/') {
      openedFrom = lastPath;
      leftAt = 0;
      backTo = null;
    } else if (lastPath === '/composer/' && path !== '/composer/') {
      leftAt = Date.now();
      backTo = location.href;
    }
    lastPath = path;
  }
  ['pushState', 'replaceState'].forEach(function (name) {
    var original = history[name];
    history[name] = function () {
      var result = original.apply(this, arguments);
      moved();
      return result;
    };
  });
  window.addEventListener('popstate', moved);

  // Once the snackbar's second text says the post is up: the profile. Not from Home, where
  // Facebook opens the new post itself, nor once the page has moved on from where it went back to.
  function posted(snackbar) {
    var first = snackbar.textContent;
    var done = false;
    function open() {
      if (done) return;
      done = true;
      watch.disconnect();
      if (!openedFrom || openedFrom === '/' || !backTo || location.href !== backTo) return;
      var me = /(?:^|;\s*)c_user=(\d+)/.exec(document.cookie);
      if (!me) return;
      store(String(Date.now()));
      go('/profile.php?id=' + me[1]);
    }
    var watch = new MutationObserver(function () {
      if (snackbar.textContent !== first) setTimeout(open, 500);
    });
    watch.observe(snackbar, { childList: true, characterData: true, subtree: true });
    setTimeout(open, POSTED_MS);
  }

  new MutationObserver(function (mutations) {
    var onComposer = location.pathname === '/composer/' && openedFrom;
    if (!onComposer && (!leftAt || Date.now() - leftAt > SNACKBAR_MS)) return;
    for (var i = 0; i < mutations.length; i++) {
      var added = mutations[i].addedNodes;
      for (var j = 0; j < added.length; j++) {
        var n = added[j];
        if (n.nodeType !== 1) continue;
        var bar = n.classList.contains('snackbar-container') ? n : n.querySelector('.snackbar-container');
        if (bar) {
          leftAt = 0;
          posted(bar);
          return;
        }
      }
    }
  }).observe(document.documentElement, { childList: true, subtree: true });
})();
