// Messages layer, after Share to story (MessagesLayer): the creator moves in-page to Facebook's
// stories page (www.facebook.com/stories), which already holds the new story ("1 new", 1m), while
// the feed's stories tray gets it some 30 s later (Galaxy A56, 2026-10-27). Opens the user's own
// stories there: the card under Your story, found by the Add to story link (/stories/create/) drawn
// on it (beside it in the page, not inside), so in any language. Marks the page for
// layer_story_viewer.js, which lays the viewer out full screen. "opened", or "none" while the card
// isn't there. ES5, like the PiP scripts.
(function () {
  var links = document.querySelectorAll('a[href*="/stories/create"]');
  var buttons = document.querySelectorAll('[role="button"]');
  for (var i = 0; i < links.length; i++) {
    var r = links[i].getBoundingClientRect();
    if (r.width === 0) continue;
    var x = r.left + r.width / 2;
    var y = r.top + r.height / 2;
    for (var j = 0; j < buttons.length; j++) {
      var b = buttons[j].getBoundingClientRect();
      if (b.width > r.width * 2 && x >= b.left && x <= b.right && y >= b.top && y <= b.bottom) {
        window.__astryxSharedStoryOpen = true;
        buttons[j].click();
        return 'opened';
      }
    }
  }
  return 'none';
})()
