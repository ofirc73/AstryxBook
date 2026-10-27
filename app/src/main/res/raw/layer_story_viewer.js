// Messages layer only. A story opened from a chat (a reply or reaction to a story) shows in the
// desktop site's stories viewer, laid out for a desktop window: a 360 px Stories column (Your story,
// All stories) beside the story, which left the story 24 of 384 px and pushed it off the right edge
// (Galaxy A56, 2026-10-27), and Facebook's top bar (close, logo, account buttons) fixed over the
// story's own header. At phone width both are hidden while a story is open: the story takes the
// whole width (Facebook sizes it again on resize) and Back leaves it, as the bar's close did.
// Found by the column's navigation (taller than half the window, at least half its width) and by the
// bar's boxes (fixed at the top, no taller than the bar). ES5, like the PiP scripts.
(function () {
  if (window.__astryxStoryViewerInstalled) return;
  window.__astryxStoryViewerInstalled = true;

  var HIDE = 'data-astryx-story-hide';
  var ON = 'astryx-story-viewer';
  var NARROW = 720; // two of the Stories column's widths
  var BAR_HEIGHT = 64;
  var MAX_LEVELS = 8;

  var style = document.createElement('style');
  style.textContent = 'html.' + ON + ' [' + HIDE + '] { display: none !important; }';
  (document.head || document.documentElement).appendChild(style);

  function isStoryPage() {
    var path = location.pathname;
    return /^\/stories\/[^\/]+/.test(path) && path.indexOf('/stories/create') !== 0;
  }

  function hide(e) {
    if (e.hasAttribute(HIDE)) return false;
    e.setAttribute(HIDE, 'true');
    return true;
  }

  // The Stories column: the navigation's ancestor just below the row it shares with the story.
  function hideColumn(dialog) {
    var navs = dialog.querySelectorAll('[role="navigation"]');
    var hidden = false;
    for (var i = 0; i < navs.length; i++) {
      var r = navs[i].getBoundingClientRect();
      if (r.height < innerHeight * 0.5 || r.width < innerWidth * 0.5) continue;
      var column = navs[i];
      for (var n = 0; n < MAX_LEVELS && column.parentElement && column.parentElement !== dialog; n++) {
        if (column.parentElement.getBoundingClientRect().width > r.width + 1) break;
        column = column.parentElement;
      }
      if (hide(column)) hidden = true;
    }
    return hidden;
  }

  // The top bar: the fixed boxes at the top that hold its buttons and links (the close button is
  // the viewer's, the account buttons are Facebook's banner, outside the viewer's dialog).
  function hideBar() {
    var items = document.querySelectorAll('[role="button"], [role="navigation"], a');
    var hidden = false;
    for (var i = 0; i < items.length; i++) {
      var r = items[i].getBoundingClientRect();
      if (r.height === 0 || r.bottom > BAR_HEIGHT) continue;
      for (var e = items[i], n = 0; e && n < MAX_LEVELS; e = e.parentElement, n++) {
        if (getComputedStyle(e).position !== 'fixed') continue;
        var box = e.getBoundingClientRect();
        if (box.top <= BAR_HEIGHT / 4 && box.bottom <= BAR_HEIGHT && hide(e)) hidden = true;
        break;
      }
    }
    return hidden;
  }

  function update() {
    var on = isStoryPage() && innerWidth < NARROW;
    document.documentElement.classList.toggle(ON, on);
    if (!on) return;
    var dialogs = document.querySelectorAll('[role="dialog"]');
    var hidden = hideBar();
    for (var i = 0; i < dialogs.length; i++) {
      if (hideColumn(dialogs[i])) hidden = true;
    }
    // Facebook sizes the story for the space it has only when the window resizes.
    if (hidden) window.dispatchEvent(new Event('resize'));
  }

  // The story's progress bar changes the page all the time: look again at most every 200 ms.
  var pending = null;
  function schedule() {
    if (pending) return;
    pending = setTimeout(function () { pending = null; update(); }, 200);
  }

  update();
  new MutationObserver(schedule).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener('popstate', schedule);
  window.addEventListener('resize', schedule);
})();
