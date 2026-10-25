// Main view, once a post opened from a chat has loaded (MaterialbookWebView). m.facebook.com shows
// a post as Home, with the post as a "From your link" card under the feed's first post, so the
// post was somewhere down the page: that card is scrolled to the top, under the bars that stay
// there. Found by its shape, not its (translated) label: a top-level feed card whose first row is
// a short line of text with no picture (every post's first row has its author's picture). Gives
// up after 8 s, or as soon as the user touches the page. ES5, like the PiP scripts.
(function () {
  var CARD = 'div[data-mcomponent="MContainer"][data-dcm-id="1"]';
  // Only among the first cards: the linked post comes right after the feed's first one or two.
  var FIRST_CARDS = 6;

  function linkedCard() {
    var cards = document.querySelectorAll(CARD);
    var seen = 0;
    for (var i = 0; i < cards.length && seen < FIRST_CARDS; i++) {
      var card = cards[i];
      if (card.parentElement && card.parentElement.closest(CARD)) continue;
      seen++;
      var first = card.firstElementChild;
      if (!first || first.querySelector('img')) continue;
      var r = first.getBoundingClientRect();
      if (r.height > 0 && r.height < 48 && /\S/.test(first.textContent || '')) return card;
    }
    return null;
  }

  // The bottom of the bars fixed (or sticky) at the top of the screen.
  function barsBottom() {
    var bottom = 0;
    for (var y = 1; y < window.innerHeight / 3; y += 8) {
      for (var e = document.elementFromPoint(window.innerWidth / 2, y); e && e !== document.body; e = e.parentElement) {
        var position = getComputedStyle(e).position;
        if (position === 'fixed' || position === 'sticky') {
          bottom = Math.max(bottom, e.getBoundingClientRect().bottom);
          break;
        }
      }
    }
    return bottom;
  }

  var until = Date.now() + 8000;
  var touched = false;
  document.addEventListener('touchstart', function () { touched = true; }, { capture: true, once: true });
  (function poll() {
    if (touched || Date.now() > until) return;
    var card = linkedCard();
    if (card) {
      window.scrollTo(0, Math.max(0, card.getBoundingClientRect().top + window.scrollY - barsBottom()));
      return;
    }
    setTimeout(poll, 300);
  })();
})();
