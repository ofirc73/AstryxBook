// Messages layer only. A photo opened from a chat shows in the desktop site's viewer, laid out for
// a desktop window: the photo in a middle column between two columns for the previous/next arrows,
// so at phone width it filled only about half the screen's width (216 of 384 px on a Galaxy A56).
// The arrow columns are laid over the photo instead, as the Facebook app shows them, and the photo
// takes the row's whole width (the viewer's own size limits, by the window's height, still apply),
// with rounded corners like the app's.
// Found by the photo: the viewer's only <img> with object-fit: contain. ES5, like the PiP scripts.
(function () {
  if (window.__astryxPhotoViewerInstalled) return;
  window.__astryxPhotoViewerInstalled = true;

  var MARK = 'data-astryx-photo-row';
  var MAX_ARROW_COLUMN = 120;
  var MAX_LEVELS = 8;
  var CORNER_RADIUS = '16px';

  function isCreator() {
    var path = location.pathname;
    return path.indexOf('/stories/create') === 0 || path.indexOf('/reels/create') === 0;
  }

  // The viewer's row: an ancestor with three children, the photo in the middle one and narrow,
  // tall arrow columns on both sides.
  function photoRow(img) {
    var child = img;
    for (var e = img.parentElement, n = 0; e && n < MAX_LEVELS; child = e, e = e.parentElement, n++) {
      var kids = e.children;
      if (kids.length !== 3 || kids[1] !== child) continue;
      var row = e.getBoundingClientRect();
      var left = kids[0].getBoundingClientRect();
      var right = kids[2].getBoundingClientRect();
      if (left.width > 0 && left.width <= MAX_ARROW_COLUMN && right.width > 0 && right.width <= MAX_ARROW_COLUMN &&
          left.height >= row.height * 0.5 && right.height >= row.height * 0.5) {
        return e;
      }
    }
    return null;
  }

  function widen() {
    if (isCreator()) return;
    var imgs = document.querySelectorAll('img');
    for (var i = 0; i < imgs.length; i++) {
      var img = imgs[i];
      if (getComputedStyle(img).objectFit !== 'contain' || img.getBoundingClientRect().width < 50) continue;
      var row = photoRow(img);
      if (!row || row.hasAttribute(MARK)) continue;
      row.setAttribute(MARK, 'true');
      var kids = row.children;
      row.style.position = 'relative';
      kids[0].style.cssText += ';position:absolute;left:0;top:0;bottom:0;z-index:2';
      kids[2].style.cssText += ';position:absolute;right:0;top:0;bottom:0;z-index:2';
      kids[1].style.cssText += ';flex:1 1 100%;max-width:100%;width:100%';
      img.style.borderRadius = CORNER_RADIUS;
    }
  }

  widen();
  new MutationObserver(widen).observe(document.documentElement, { childList: true, subtree: true });
})();
