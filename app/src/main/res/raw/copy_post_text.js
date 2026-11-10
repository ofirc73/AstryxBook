// A long press on a post's text copies it, as the Facebook app does: m.facebook.com's text can't be
// selected (its body is `user-select: none`), and Facebook does nothing on a post's text held down.
// Only a post's own text, its body: a post is a row of Facebook's list (a child of a
// data-type="vscroller", on Home and on a post's page alike), and its body is the row's first text
// block (data-mcomponent="TextArea") as wide as the post, before any photo or video; the names and
// time above it are narrower, the link cards, counts and comments come after the post's media
// (read on the emulator 2026-11-10). The body is copied whole, its server-made line breaks joined
// (server_lines.js), through the app (AstryxPage call `copyText`), which buzzes and puts it on the
// clipboard. Facebook's own long presses win: a comment's opens its menu (Reply, Copy...) on the
// touch's end, which Facebook prevents, and then nothing is copied. A post cut short ("... See
// more", in any language: the last bit of text starts with the dots) is first opened in place by a
// tap on it, as Facebook does, and copied once its whole text shows.
// Main view only (the Messages layer's desktop pages can be selected). ES5-style, like the PiP scripts.
(function () {
  if (window.__astryxCopyPostText || window.__astryxMessagesLayer || !window.__astryxServerLines) return;
  var BODY_WIDTH = 0.9;                               // a body's least width, as a share of its post's
  var MEDIA_WIDTH = 0.5;                              // a photo or video's (avatars and icons are smaller)
  var EXPAND_MS = window.__astryxCopyExpandMs || 4000; // tests shorten it
  var held = null;                                    // the block held down, until the touch ends

  function textBlock(el) {
    for (; el && el !== document.body; el = el.parentElement) {
      if (el.getAttribute && el.getAttribute('data-mcomponent') === 'TextArea') return el;
    }
    return null;
  }

  // The post (a row of Facebook's list) the element is in.
  function postOf(el) {
    for (; el && el.parentElement; el = el.parentElement) {
      if (el.parentElement.getAttribute('data-type') === 'vscroller') return el;
    }
    return null;
  }

  // The post's body, if the block is one.
  function isPostBody(block) {
    var post = block && postOf(block);
    if (!post) return false;
    var width = post.getBoundingClientRect().width;
    var items = post.querySelectorAll('[data-mcomponent="TextArea"], img, video');
    for (var i = 0; i < items.length; i++) {
      var box = items[i].getBoundingClientRect();
      if (items[i].tagName === 'IMG' || items[i].tagName === 'VIDEO') {
        if (box.width >= width * MEDIA_WIDTH) return false;
      } else if (box.width >= width * BODY_WIDTH && /\S/.test(items[i].textContent)) {
        return items[i] === block;
      }
    }
    return false;
  }

  // The "... See more" at the end of a post cut short: its own last bit of text.
  function seeMore(block) {
    if (!block.hasAttribute('data-action-id')) return null;
    var spans = block.querySelectorAll('span');
    var last = spans.length > 1 ? spans[spans.length - 1] : null;
    return last && /^\s*(\.\.\.|\u2026)/.test(last.textContent) ? last : null;
  }

  // The block's text as the user reads it: lines joined, without the app's icon-font glyphs
  // (private use characters) Facebook draws some icons with, nor a "... See more".
  function textOf(block) {
    var text = '';
    var parts = window.__astryxServerLines.join(block);
    var more = seeMore(block);
    for (var i = 0; i < parts.length; i++) {
      if (!more || !more.contains(parts[i].node)) text += parts[i].text;
    }
    return text.replace(/[\uE000-\uF8FF]|[\uDB80-\uDBFF][\uDC00-\uDFFF]/g, '').replace(/^\s+|\s+$/g, '');
  }

  function copy(text) {
    if (!text || !window.AstryxPage) return;
    window.AstryxPage.postMessage(JSON.stringify({ call: 'copyText', text: text }));
  }

  // Opens a post cut short, then copies it once its text has grown (the server draws it again,
  // maybe as a new block at the same place), or what showed if it doesn't within EXPAND_MS.
  function copyWhole(block, x, y) {
    var shown = textOf(block);
    if (!seeMore(block)) { copy(shown); return; }
    block.click();
    var started = Date.now();
    (function check() {
      var now = textBlock(document.elementFromPoint(x, y));
      if (!isPostBody(now)) now = block;
      if (document.contains(now) && !seeMore(now) && textOf(now).length > shown.length) { copy(textOf(now)); return; }
      if (Date.now() - started > EXPAND_MS) { copy(shown); return; }
      setTimeout(check, 100);
    })();
  }

  // For the live checks: what counts as a post's body.
  window.__astryxCopyPostText = { isPostBody: isPostBody };

  document.addEventListener('touchstart', function () { held = null; }, true);
  document.addEventListener('touchcancel', function () { held = null; }, true);
  // Chromium's long press: the page's contextmenu, while the finger is still down.
  document.addEventListener('contextmenu', function (e) {
    var block = textBlock(e.target);
    held = isPostBody(block) ? { block: block, x: e.clientX, y: e.clientY } : null;
  }, true);
  document.addEventListener('touchend', function (e) {
    var press = held;
    held = null;
    if (!press) return;
    // After Facebook's own handlers have seen the touch end.
    setTimeout(function () {
      if (!e.defaultPrevented && document.contains(press.block)) copyWhole(press.block, press.x, press.y);
    }, 0);
  }, true);
})();
