// Text wrap on zoom (opt-in, only with pinch to zoom): when the user pinches in, text
// blocks near the visible area are narrowed to the visible width so their lines wrap on
// screen instead of running off the side, as Opera's "Text wrap" does. Zooming back out
// restores them. Runs once the pinch (or a pan) settles. ES5-style, like the PiP scripts.
//
// Facebook's mobile site lays pages out on its server: text blocks are `white-space: pre`
// with the line breaks already in the text, and every box around them has a fixed height.
// So a wrapped block also gets `pre-wrap`, has the server's line breaks turned back into
// spaces, and lets the boxes around it grow with it.
//
// Never restyles an element that holds a video: Facebook's reels controller watches the
// video's ancestors and pauses the reel (see PIP_FOCUS_MODE_JS).
(function() {
  if (window.__astryxTextWrap || !window.__astryxServerLines || !window.visualViewport || !document.body) return;

  var vv = window.visualViewport;
  var ATTR = 'data-astryx-wrap';      // marks a wrapped block
  var MIN_SCALE = 1.05;               // below this the page counts as not zoomed
  var MIN_TEXT = 15;                  // shorter text (names, buttons, counts) is left alone
  var GUTTER = 8;                     // CSS px kept free on each side of the wrapped text
  var MIN_FIT = 0.5;                  // narrowest a block gets to end at the screen edge, as a share of the screen
  var SETTLE_MS = 250;
  var timer = null;
  var pinched = false;
  var run = 0;                        // marks elements already looked at in this pass
  var lastScale = 1;
  var changed = [];                   // elements and text nodes changed, to put back
  var sized = [];                     // boxes given a taller height
  var tapped = null;                  // where the view was on a wrapped block the user tapped

  function blockOf(node) {
    var el = node.parentElement;
    while (el && el !== document.body && el !== document.documentElement) {
      var display = getComputedStyle(el).display;
      if (display !== 'inline' && display !== 'contents') return el;
      el = el.parentElement;
    }
    return null;
  }

  function save(el) {
    if (el.__astryxWrapSaved) return;
    el.__astryxWrapSaved = {
      maxWidth: el.style.maxWidth, whiteSpace: el.style.whiteSpace, height: el.style.height,
      marginLeft: el.style.marginLeft, marginRight: el.style.marginRight
    };
    changed.push(el);
  }

  function unwrapAll() {
    for (var i = changed.length - 1; i >= 0; i--) {
      var item = changed[i];
      if (item.nodeType === 3) {
        if (item.nodeValue === item.__astryxWrapText.now) item.nodeValue = item.__astryxWrapText.was;
        item.__astryxWrapText = null;
        continue;
      }
      var saved = item.__astryxWrapSaved;
      item.style.maxWidth = saved.maxWidth;
      item.style.whiteSpace = saved.whiteSpace;
      item.style.height = saved.height;
      item.style.marginLeft = saved.marginLeft;
      item.style.marginRight = saved.marginRight;
      item.__astryxWrapSaved = null;
      item.__astryxWrapFromRight = null;
      item.removeAttribute(ATTR);
    }
    changed = [];
    sized = [];
  }

  function heightOf(el) {
    return el.__astryxWrapSaved ? el.__astryxWrapSaved.height : el.style.height;
  }

  // The boxes around the block that have a fixed height, up to the first one sized by its
  // content. Null when the block can't be wrapped: one of them holds a video, or it's in a
  // fixed or sticky bar.
  function heightChain(block) {
    var chain = [];
    var done = false;
    for (var el = block; el && el !== document.body; el = el.parentElement) {
      var position = getComputedStyle(el).position;
      if (position === 'fixed' || position === 'sticky') return null;
      var height = heightOf(el);
      if (done || el === block || !height) continue;
      if (!/px$/.test(height)) {
        done = true;
        continue;
      }
      if (el.querySelector('video')) return null;
      chain.push(el);
    }
    return chain;
  }

  // The height of the block's lines. Not the block's own height: Facebook stretches some
  // blocks to the fixed height of the box around them.
  function textHeight(block) {
    var range = document.createRange();
    range.selectNodeContents(block);
    return range.getBoundingClientRect().height;
  }

  // Grows the boxes around the wrapped blocks by as much as the blocks inside them grew,
  // as Facebook's server would have laid them out. Not `height: auto`: the server overlays
  // boxes with negative margins as tall as what they cover, which auto heights get wrong.
  function layOut() {
    var wrapped = allWrapped();
    var grown = [];
    var i, j;
    for (i = 0; i < wrapped.length; i++) {
      // Looked up again each time: Facebook moves boxes into new ones (See more).
      var chain = heightChain(wrapped[i]) || [];
      var growth = textHeight(wrapped[i]) - wrapped[i].__astryxWrapHeight;
      for (j = 0; j < chain.length; j++) {
        if (chain[j].__astryxWrapGrowth === undefined) {
          chain[j].__astryxWrapGrowth = 0;
          grown.push(chain[j]);
        }
        chain[j].__astryxWrapGrowth += growth;
      }
    }
    // Boxes whose wrapped blocks Facebook has since replaced (See more) go back.
    for (i = 0; i < sized.length; i++) {
      if (sized[i].__astryxWrapGrowth === undefined) sized[i].style.height = sized[i].__astryxWrapSaved.height;
    }
    for (i = 0; i < grown.length; i++) {
      var el = grown[i];
      save(el);
      el.style.height = Math.max(0, parseFloat(el.__astryxWrapSaved.height) + el.__astryxWrapGrowth) + 'px';
      el.__astryxWrapGrowth = undefined;
    }
    sized = grown;
  }

  // The server's line breaks turned back into spaces (server_lines.js).
  function joinLines(block) {
    if (block.textContent.indexOf('\n') < 0) return;
    var joined = window.__astryxServerLines.join(block);
    for (var i = 0; i < joined.length; i++) {
      var node = joined[i].node;
      if (joined[i].text !== node.nodeValue) {
        node.__astryxWrapText = { was: node.nodeValue, now: joined[i].text };
        node.nodeValue = joined[i].text;
        changed.push(node);
      }
    }
  }

  function wrap(block, width) {
    var whiteSpace = getComputedStyle(block).whiteSpace;
    save(block);
    block.setAttribute(ATTR, '');
    block.__astryxWrapHeight = textHeight(block);
    if (whiteSpace !== 'normal') joinLines(block);
    if (whiteSpace === 'pre') block.style.whiteSpace = 'pre-wrap';
    if (/px$/.test(block.style.height)) block.style.height = 'auto';
    // Narrowed, a block keeps to the side its box starts from. Text in the other direction
    // (Hebrew in an English page) moves to its own side, where the view lines up with it.
    var rtl = getComputedStyle(block).direction === 'rtl';
    var fromRight = getComputedStyle(block.parentElement).direction === 'rtl';
    if (rtl !== fromRight && block.parentElement.children.length === 1) {
      if (rtl) block.style.marginLeft = 'auto';
      else block.style.marginRight = 'auto';
      fromRight = rtl;
    }
    block.__astryxWrapFromRight = fromRight;
    block.style.maxWidth = width + 'px';
  }

  // The width that makes the block end at the screen edge with the view starting at
  // viewLeft, so text that starts further in (a post's header, a comment) fits too.
  function fitWidth(block, full, viewLeft) {
    var r = block.getBoundingClientRect();
    var fromRight = block.__astryxWrapFromRight;
    var room = fromRight ? r.right - viewLeft - GUTTER : viewLeft + vv.width - GUTTER - r.left;
    return room < full && room >= full * MIN_FIT ? Math.floor(room) : full;
  }

  // Narrows the text blocks from one screen above to one below the visible area to the
  // visible width. Returns the blocks it wrapped.
  function wrapNearby(width) {
    var top = vv.offsetTop - vv.height;
    var bottom = vv.offsetTop + 2 * vv.height;
    var blocks = [];
    run++;
    var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
    var node;
    while ((node = walker.nextNode())) {
      var parent = node.parentElement;
      if (!parent || parent.__astryxWrapRun === run) continue;
      if (node.nodeValue.replace(/\s+/g, '').length < MIN_TEXT) continue;
      if (/^(SCRIPT|STYLE|NOSCRIPT|TEXTAREA)$/.test(parent.tagName)) continue;
      parent.__astryxWrapRun = run;
      var near = parent.getBoundingClientRect();
      if (near.bottom < top || near.top > bottom) continue;
      var block = blockOf(node);
      if (!block || (block !== parent && block.__astryxWrapRun === run)) continue;
      block.__astryxWrapRun = run;
      if (block.hasAttribute(ATTR) || block.querySelector('video')) continue;
      if (block.getBoundingClientRect().width <= width + 1) continue;
      var whiteSpace = getComputedStyle(block).whiteSpace;
      if (whiteSpace === 'nowrap') continue;
      if (!heightChain(block)) continue;
      wrap(block, width);
      blocks.push(block);
    }
    return blocks;
  }

  // The wrapped block that fills most of the screen's height; the one nearest the middle
  // of the screen when they tie.
  function focusOf(blocks) {
    var top = vv.offsetTop;
    var bottom = top + vv.height;
    var middle = top + vv.height / 2;
    var focus = null;
    var bestShown = 0;
    var bestDistance = Infinity;
    for (var i = 0; i < blocks.length; i++) {
      var rect = blocks[i].getBoundingClientRect();
      var shown = Math.min(rect.bottom, bottom) - Math.max(rect.top, top);
      var distance = rect.top > middle ? rect.top - middle : rect.bottom < middle ? middle - rect.bottom : 0;
      if (shown > bestShown + 1 || (shown > bestShown - 1 && distance < bestDistance)) {
        focus = blocks[i];
        bestShown = Math.max(shown, bestShown);
        bestDistance = distance;
      }
    }
    return focus;
  }

  // Pans the zoomed-in view sideways so it starts at viewLeft (in the layout viewport's
  // coordinates). A marker as wide as the view, at the current top of the view, is scrolled
  // into view, which moves the visual viewport sideways only.
  function panTo(viewLeft) {
    var marker = document.createElement('div');
    marker.style.cssText = 'position:absolute;height:1px;pointer-events:none;visibility:hidden;' +
      'width:' + Math.floor(vv.width) + 'px;left:' + Math.max(0, viewLeft + window.scrollX) + 'px;' +
      'top:' + vv.pageTop + 'px';
    document.documentElement.appendChild(marker);
    try {
      marker.scrollIntoView({ block: 'start', inline: 'center' });
    } catch (e) {}
    marker.parentNode.removeChild(marker);
  }

  // The element at the middle of the screen and where it is, so it can be kept in place:
  // wrapping makes text blocks taller and moves everything below them.
  function anchorAt() {
    var el = document.elementFromPoint(vv.offsetLeft + vv.width / 2, vv.offsetTop + vv.height / 2);
    return el ? { el: el, top: el.getBoundingClientRect().top } : null;
  }

  function restore(anchor) {
    if (!anchor || !document.documentElement.contains(anchor.el)) return;
    var moved = anchor.el.getBoundingClientRect().top - anchor.top;
    if (Math.abs(moved) > 1) window.scrollBy(0, moved);
  }

  function allWrapped() {
    return Array.prototype.slice.call(document.querySelectorAll('[' + ATTR + ']'));
  }

  // After a pinch: wrap, line the text that fills the screen up with its edge and fit the
  // other blocks to the view. After a pan: only wrap what came into view, fitted to the
  // view where it is, so the user can still move around freely.
  // Pinch zoom on top of the scale the page rests at (page_zoom.js zooms whole pages).
  function pinchScale() {
    var rest = window.__astryxPageZoomScale ? window.__astryxPageZoomScale() : 1;
    return vv.scale / rest;
  }

  function update(afterPinch) {
    var anchor = anchorAt();
    if (pinchScale() <= MIN_SCALE) {
      lastScale = vv.scale;
      if (!changed.length) return;
      unwrapAll();
      restore(anchor);
      return;
    }
    var full = Math.floor(vv.width) - 2 * GUTTER;
    if (full < 40) return;
    if (Math.abs(vv.scale - lastScale) > 0.01) afterPinch = true;
    lastScale = vv.scale;
    var blocks;
    if (afterPinch) {
      blocks = allWrapped();
      for (var i = 0; i < blocks.length; i++) blocks[i].style.maxWidth = full + 'px';
      blocks = blocks.concat(wrapNearby(full));
      layOut();
      restore(anchor);
      // Fit the blocks to a view that starts at the text filling the screen.
      var focus = focusOf(blocks);
      if (!focus) return;
      var viewLeft = focus.getBoundingClientRect().left - GUTTER;
      for (var j = 0; j < blocks.length; j++) {
        if (blocks[j] !== focus) blocks[j].style.maxWidth = fitWidth(blocks[j], full, viewLeft) + 'px';
      }
      layOut();
      restore(anchor);
      panTo(focus.getBoundingClientRect().left - GUTTER);
    } else {
      blocks = wrapNearby(full);
      for (var k = 0; k < blocks.length; k++) {
        blocks[k].style.maxWidth = fitWidth(blocks[k], full, vv.offsetLeft) + 'px';
      }
      if (blocks.length) layOut();
      if (!keepTapped()) restore(anchor);
    }
  }

  // After a tap on wrapped text that made Facebook replace it (See more), puts the view back
  // where it was on the text, which now ends further down, instead of on what was below it.
  function keepTapped() {
    var was = tapped;
    tapped = null;
    if (!was || Date.now() - was.time > 3000 || document.documentElement.contains(was.block)) return false;
    var wrapped = allWrapped();
    var match = null;
    for (var i = 0; i < wrapped.length; i++) {
      var top = wrapped[i].getBoundingClientRect().top + window.scrollY;
      if (Math.abs(top - was.top) < 2) match = wrapped[i];
    }
    if (!match) return false;
    var target = match.getBoundingClientRect().top + window.scrollY + was.offset;
    window.scrollBy(0, target - vv.pageTop);
    return true;
  }

  function settle(isPinch) {
    if (isPinch) pinched = true;
    clearTimeout(timer);
    timer = setTimeout(function() {
      timer = null;
      var afterPinch = pinched;
      pinched = false;
      update(afterPinch);
    }, SETTLE_MS);
  }

  vv.addEventListener('resize', function() { settle(true); });
  vv.addEventListener('scroll', function() { settle(false); });

  // Text Facebook adds or replaces while zoomed in (See more, more comments) is wrapped too.
  // Doesn't postpone a pass already due, so a page that keeps changing can't hold it off.
  new MutationObserver(function() {
    if (changed.length && !timer) settle(false);
  }).observe(document.body, { childList: true, subtree: true });

  // Facebook acts on taps itself, often without a click event.
  document.addEventListener('pointerdown', function(e) {
    var block = changed.length && e.target.closest ? e.target.closest('[' + ATTR + ']') : null;
    if (!block) return;
    var top = block.getBoundingClientRect().top + window.scrollY;
    tapped = { block: block, top: top, offset: vv.pageTop - top, time: Date.now() };
  }, true);

  // update() is exposed for tests: a test WebView can't pinch.
  window.__astryxTextWrap = { update: update };
})();
