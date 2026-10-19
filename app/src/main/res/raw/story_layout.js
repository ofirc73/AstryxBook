// The desktop story creator (www.facebook.com/stories/create, in the Messages layer) laid out
// like the Facebook app's story editor, at the phone's own width so its text is a normal size:
// the story fills the screen, the creator's tools (text, music, time, alternative text) are round
// buttons along the bottom of the story, and Facebook's own privacy / Discard / Share to story
// row sits under it. Stickers and text can be dragged with a finger.
//
// Facebook builds the creator from a sidebar (role=navigation, the tools) beside the preview
// (role=main); at phone width it already moves Discard and Share under the story. Everything is
// found by its role and its place in the page, never its (translated) label. Run by the app on the
// story creator page; window.__astryxStoryLayout.stop() undoes it (for live debugging).
// ES5 on purpose, like the PiP scripts.
(function () {
  if (window.__astryxStoryLayout) return;

  // A story open in the editor: the phone's own width. The page choosing a story (before the
  // first one, after Discard): Facebook's desktop layout, 600 px wide, fitted to the screen.
  var EDITING = 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no';
  var CHOOSER_WIDTH = 600;
  function wantedViewport() {
    if (storyFrame()) return EDITING;
    var scale = Math.min(1, (window.outerWidth || screen.width) / CHOOSER_WIDTH).toFixed(3);
    return 'width=' + CHOOSER_WIDTH + ', initial-scale=' + scale + ', minimum-scale=' + scale + ', user-scalable=yes';
  }
  var BAR_HEIGHT = 64; // the bottom bar: privacy, Discard, Share to story
  var TOOLS_GAP = 40; // between the tools (their labels hang below them) and the bottom of the story
  var TOOLS_LEFT_INSET = 4;
  var TOOLS_RIGHT_INSET = 14; // keep the last tool's label clear of the story's right edge

  var timers = [];
  var observers = [];
  var listeners = [];
  var style = null;

  function on(target, type, handler, options) {
    target.addEventListener(type, handler, options);
    listeners.push([target, type, handler, options]);
  }
  function observe(target, options, callback) {
    var observer = new MutationObserver(callback);
    observer.observe(target, options);
    observers.push(observer);
  }

  // Phone width, kept when another script (pinch_to_zoom.js) rewrites the viewport.
  function viewport() {
    var content = wantedViewport();
    var metas = document.querySelectorAll('meta[name="viewport"]');
    if (!metas.length && document.head) {
      var meta = document.createElement('meta');
      meta.name = 'viewport';
      document.head.appendChild(meta);
      metas = [meta];
    }
    for (var i = 0; i < metas.length; i++) {
      if (metas[i].getAttribute('content') !== content) metas[i].setAttribute('content', content);
    }
  }

  // The story's frame: sized by Facebook with an inline width in px and a height relative to its
  // box ("calc(100% - Npx)"), around the photo or video.
  function storyFrame() {
    var boxes = document.querySelectorAll('div[style*="calc(100% - "]');
    for (var i = 0; i < boxes.length; i++) {
      var css = boxes[i].getAttribute('style') || '';
      if (/width:\s*[\d.]+px/.test(css) && boxes[i].querySelector('img, video')) return boxes[i];
    }
    return null;
  }

  // Facebook sizes the frame from the window's height: width = (innerHeight - a constant) x 9/16,
  // height = its box's height - a constant, centred and clipped in the preview. Report a window
  // height that gives the largest 9:16 frame that fits the screen, make its box that tall, and
  // let Facebook resize the frame. The constants are read from the page.
  var ownInnerHeight = Object.getOwnPropertyDescriptor(window, 'innerHeight');
  var reported = 0;
  var margin = 0; // innerHeight - the frame's height, in Facebook's sizing
  var lastWidth = 0;
  var steady = 0;
  function realHeight() {
    return ownInnerHeight && ownInnerHeight.get ? ownInnerHeight.get.call(window) : document.documentElement.clientHeight;
  }
  try {
    Object.defineProperty(window, 'innerHeight', {
      configurable: true,
      get: function () { return reported || realHeight(); }
    });
  } catch (e) { /* the frame keeps Facebook's size */ }

  function fitFrame(frame) {
    if (!frame) {
      lastWidth = 0;
      if (reported) {
        reported = 0;
        window.dispatchEvent(new Event('resize'));
      }
      return;
    }
    var box = frame.parentElement;
    var width = parseFloat(frame.style.width);
    var inset = parseFloat(frame.style.top) || 0;
    var below = parseFloat((/calc\(100% - ([\d.]+)px\)/.exec(frame.style.height) || [])[1]) || 0;
    if (!width) return;
    // Facebook's constant, from the window height it was last told and the width it gave the
    // frame for it, once that width has held for a moment (the page may be mid-layout: a new
    // viewport, a resize not handled yet).
    if (width !== lastWidth) {
      lastWidth = width;
      steady = 0;
      return;
    }
    if (++steady < 2) return;
    margin = (reported || realHeight()) - width * 16 / 9;
    var byWidth = box.clientWidth - 2 * inset;
    var byHeight = (realHeight() - box.getBoundingClientRect().top - inset - below - BAR_HEIGHT) * 9 / 16;
    var wanted = Math.floor(Math.min(byWidth, byHeight));
    if (wanted <= 0) return;
    var height = Math.round(wanted * 16 / 9 + below) + 'px';
    if (box.style.height !== height) box.style.height = height;
    var next = Math.round(wanted * 16 / 9 + margin);
    if (next === reported || Math.abs(width - wanted) < 1) return;
    reported = next;
    steady = 0;
    window.dispatchEvent(new Event('resize'));
  }

  var ATTRIBUTES = ['data-astryx-story', 'data-astryx-story-hide', 'data-astryx-story-path', 'data-astryx-story-popup',
    'data-astryx-story-music-hidden',
    'data-astryx-story-caption', 'data-astryx-story-captioned', 'data-astryx-story-idle', 'data-astryx-story-x'];
  function untag() {
    var tagged = document.querySelectorAll('[' + ATTRIBUTES.join('], [') + ']');
    for (var n = 0; n < tagged.length; n++) {
      for (var a = 0; a < ATTRIBUTES.length; a++) tagged[n].removeAttribute(ATTRIBUTES[a]);
    }
  }

  function mark(el, value) {
    if (el && el.getAttribute('data-astryx-story') !== value) el.setAttribute('data-astryx-story', value);
  }

  // Rows with an on/off switch (the AI label: "Add AI label", its explanation and the switch):
  // never a tool, always hidden, in any language. Their label (the row's first text) is
  // remembered: while posting, Facebook redraws the row without its switch or explanation, and as
  // an icon with a short label it was taken for a tool and squeezed into a slot, its letters in a
  // column beside Share (seen on a Galaxy A56). Never a dialog's switches.
  var switchLabels = {};
  function firstText(el) {
    var walker = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, null, false);
    for (var node = walker.nextNode(); node; node = walker.nextNode()) {
      var text = node.nodeValue.replace(/\s+/g, ' ').replace(/^\s+|\s+$/g, '');
      if (text) return text;
    }
    return '';
  }
  function isSwitchRow(button) {
    return !!button.querySelector('[role="switch"], input[type="checkbox"]') || !!switchLabels[firstText(button)];
  }
  function hideSwitchRows() {
    var switches = document.querySelectorAll('[role="switch"], input[type="checkbox"]');
    for (var i = 0; i < switches.length; i++) {
      if (switches[i].closest('[role="dialog"]')) continue;
      var row = switches[i].closest('[role="button"], button, label') || switches[i].parentElement;
      var label = firstText(row);
      if (label && label.length <= 40) switchLabels[label] = true;
      if (!row.hasAttribute('data-astryx-story-hide')) row.setAttribute('data-astryx-story-hide', '');
    }
    var buttons = document.querySelectorAll('[role="button"], button');
    for (var j = 0; j < buttons.length; j++) {
      var b = buttons[j];
      if (b.closest('[role="dialog"]') || !switchLabels[firstText(b)]) continue;
      if (!b.hasAttribute('data-astryx-story-hide')) b.setAttribute('data-astryx-story-hide', '');
    }
  }

  // During posting, Facebook can move the AI-label control and explanation out of the sidebar.
  // Hide them by their text so they cannot wrap into a vertical column over the photo.
  function hideAIDisclosure() {
    function isDisclosure(el) {
      var content = (el.innerText || '').replace(/\s+/g, ' ').toLowerCase();
      return content.indexOf('we require you to label') !== -1 &&
        (content.indexOf('made with ai') !== -1 || content.indexOf('ai-generated') !== -1 ||
          content.indexOf('significantly altered') !== -1);
    }
    // Facebook can move the AI-label control itself alongside the disclosure while posting.
    // Hide only the control (and its label), never their shared posting overlay.
    var labels = document.querySelectorAll('body [role="button"], body label, body span');
    for (var l = 0; l < labels.length; l++) {
      var labelText = (labels[l].innerText || labels[l].textContent || '').replace(/\s+/g, ' ').toLowerCase();
      if (labelText.indexOf('add ai label') !== 0 || labelText.length > 40) continue;
      // In the editor sidebar this is a real tool; only hide the duplicate control Facebook
      // sometimes moves beside the posting disclosure.
      if (labels[l].closest('[role="navigation"]')) continue;
      if (!labels[l].hasAttribute('data-astryx-story-hide')) labels[l].setAttribute('data-astryx-story-hide', '');
    }
    var candidates = document.querySelectorAll('body div, body span, body p');
    for (var i = 0; i < candidates.length; i++) {
      var candidate = candidates[i];
      if (!isDisclosure(candidate)) continue;
      var descendants = candidate.querySelectorAll('div, span, p');
      var nested = false;
      for (var d = 0; d < descendants.length; d++) {
        if (isDisclosure(descendants[d])) { nested = true; break; }
      }
      if (!nested && !candidate.hasAttribute('data-astryx-story-hide')) {
        candidate.setAttribute('data-astryx-story-hide', '');
      }
    }
  }

  // Tags the preview, the sidebar and its tools for the style sheet, and hides the rest of the
  // sidebar (its header, the AI label, its own Discard / Share).
  function layout() {
    try {
      arrange();
    } catch (e) {
      window.__astryxStoryLayoutError = String(e && e.stack || e);
    }
  }

  // Facebook's small floating panels (a music sticker's styles, the song clip bar with Pause,
  // Undo and Done) are rows of round icon buttons, named only for screen readers. Each gets its
  // name (Facebook's own, in the user's language) written under it. A row wider than the screen
  // (the clip bar: its 300 px waveform pushed Done off the edge) shrinks to fit. Panels with many
  // icon buttons (the song list's play and save buttons) are left alone.
  function labelControls(layer) {
    var buttons = layer.querySelectorAll('[role="button"][aria-label]');
    var icons = [];
    for (var i = 0; i < buttons.length; i++) {
      var b = buttons[i];
      var box = b.getBoundingClientRect();
      if (/\S/.test(b.innerText || '') || !b.querySelector('svg, i, img')) continue;
      if (box.width < 24 || box.width > 64 || box.height < 24 || box.height > 64) continue;
      icons.push(b);
    }
    if (icons.length < 3 || icons.length > 5) return;
    var row = icons[0].parentElement;
    for (var r = 1; r < icons.length; r++) {
      while (row && !row.contains(icons[r])) row = row.parentElement;
    }
    if (!row || row === layer.parentElement) return;
    for (var c = 0; c < icons.length; c++) {
      if (!icons[c].hasAttribute('data-astryx-story-caption')) icons[c].setAttribute('data-astryx-story-caption', '');
      // Inline: some of Facebook's button styles outrank the style sheet's.
      if (icons[c].style.overflow !== 'visible') icons[c].style.setProperty('overflow', 'visible', 'important');
    }
    if (!row.hasAttribute('data-astryx-story-captioned')) row.setAttribute('data-astryx-story-captioned', '');
    // A panel (one with text, like the music sticker's: song, Remove, styles) closes only on a tap
    // elsewhere on the story, which nothing says: it gets a Done button that does that.
    if (layer.hasAttribute('data-astryx-story-popup')) addDone(row);
  }

  var DONE_LABEL = window.__astryxStoryDone || 'Done';
  function addDone(row) {
    var next = row.nextElementSibling;
    if (next && next.hasAttribute('data-astryx-story-done')) return;
    var done = document.createElement('div');
    done.setAttribute('role', 'button');
    done.setAttribute('tabindex', '0');
    done.setAttribute('data-astryx-story-done', '');
    done.textContent = DONE_LABEL;
    done.addEventListener('click', function (e) {
      e.stopPropagation();
      tapStoryBackground();
    });
    row.parentElement.insertBefore(done, next);
  }

  // Done under a tool's editor: closes it the way Facebook does, by tapping its tool again.
  function addSheetDone(sheet, tool) {
    if (sheet.querySelector('[data-astryx-story-done]')) return;
    var done = document.createElement('div');
    done.setAttribute('role', 'button');
    done.setAttribute('tabindex', '0');
    done.setAttribute('data-astryx-story-done', '');
    done.textContent = DONE_LABEL;
    done.addEventListener('click', function (e) {
      e.stopPropagation();
      tool.click();
    });
    sheet.appendChild(done);
  }

  // A mouse event of `type` at (x, y) on target: a pointer event (pointerType mouse) for the
  // pointer types, a mouse event for the others. Facebook's editor follows only the mouse.
  function mouseEvent(target, type, x, y, buttons) {
    var init = { bubbles: !/enter|leave/.test(type), cancelable: true, composed: true, view: window,
      clientX: x, clientY: y, button: 0, buttons: buttons || 0 };
    if (type.indexOf('pointer') === 0) {
      if (!window.PointerEvent) return;
      init.pointerId = 1;
      init.pointerType = 'mouse';
      init.isPrimary = true;
      target.dispatchEvent(new PointerEvent(type, init));
    } else {
      target.dispatchEvent(new MouseEvent(type, init));
    }
  }

  // A mouse click on the story's photo, at a corner (away from its stickers): what closes
  // Facebook's panels. Aimed at the photo itself (its largest image), whatever covers that spot.
  function tapStoryBackground() {
    var frame = storyFrame();
    if (!frame) return;
    var media = frame.querySelectorAll('img, video');
    var photo = null;
    var area = 0;
    for (var m = 0; m < media.length; m++) {
      var r = media[m].getBoundingClientRect();
      if (r.width * r.height > area) { area = r.width * r.height; photo = media[m]; }
    }
    if (!photo) return;
    var box = frame.getBoundingClientRect();
    var x = box.left + 12;
    var y = box.top + 12;
    mouseEvent(photo, 'pointerdown', x, y, 1);
    mouseEvent(photo, 'mousedown', x, y, 1);
    mouseEvent(photo, 'pointerup', x, y, 0);
    mouseEvent(photo, 'mouseup', x, y, 0);
    mouseEvent(photo, 'click', x, y, 0);
  }

  // A music sticker is a button over the photo with its track title, artist and usually album art.
  // Keep the track in Facebook's composer and hide only this visual button.
  function musicSticker(frame) {
    if (!frame) return null;
    function find(root, mustOverlapFrame) {
      var buttons = root.querySelectorAll('[role="button"]');
      var frameRect = mustOverlapFrame ? frame.getBoundingClientRect() : null;
      for (var i = 0; i < buttons.length; i++) {
        var button = buttons[i];
        var text = (button.textContent || '').replace(/\s+/g, ' ').replace(/^\s+|\s+$/g, '');
        if (text.length < 8 || !button.querySelector('img')) continue;
        // Facebook places the music sticker as a sibling of its photo frame on some layouts.
        // Restrict the wider search to buttons that visibly overlap the story photo.
        if (mustOverlapFrame) {
          var rect = button.getBoundingClientRect();
          if (rect.right <= frameRect.left || rect.left >= frameRect.right ||
              rect.bottom <= frameRect.top || rect.top >= frameRect.bottom) continue;
        }
        return button;
      }
      return null;
    }
    return find(frame, false) || find(frame.closest('[role="main"]') || frame, true);
  }

  function arrange() {
    viewport();
    var frame = storyFrame();
    var sticker = musicSticker(frame);
    if (sticker) sticker.setAttribute('data-astryx-story-music-hidden', '');

    // Facebook's popups (the music list, text style, alternative text, a sticker's options):
    // fixed layers placed with an inline transform beside the sidebar's tool, off the screen now.
    // The ones showing something are centred under the header. Closed popups' hosts stay where
    // they are and let taps through (one sat over the story and took its taps).
    var layers = document.querySelectorAll('div[style*="position: fixed"][style*="translate"]');
    for (var l = 0; l < layers.length; l++) {
      var layer = layers[l];
      if (layer.closest('[role="main"]') || layer.closest('[data-astryx-story]')) continue;
      // innerText skips hidden content; a closed popup keeps its (hidden) images.
      var shown = layer.firstElementChild && getComputedStyle(layer.firstElementChild).visibility !== 'hidden';
      var showing = shown && (/\S/.test(layer.innerText || '') || !!layer.querySelector('input, textarea, [contenteditable]'));
      if (showing) layer.setAttribute('data-astryx-story-popup', '');
      else layer.removeAttribute('data-astryx-story-popup');
      if (!shown) layer.setAttribute('data-astryx-story-idle', '');
      else layer.removeAttribute('data-astryx-story-idle');
      if (shown) labelControls(layer);
    }

    fitFrame(frame);
    // A sticker's remove button (×, shown while it's selected): a thin grey cross on 24 px,
    // barely visible on a photo. Found as a small icon button in the preview (the stickers' layer
    // sits beside the photo's frame).
    var preview = frame && frame.closest('[role="main"]');
    if (preview) {
      var small = preview.querySelectorAll('[role="button"]');
      for (var sb = 0; sb < small.length; sb++) {
        var sr = small[sb].getBoundingClientRect();
        if (sr.width > 0 && sr.width <= 32 && sr.height <= 32 && small[sb].querySelector('svg') && !/\S/.test(small[sb].innerText || '')) {
          if (!small[sb].hasAttribute('data-astryx-story-x')) small[sb].setAttribute('data-astryx-story-x', '');
        } else if (small[sb].hasAttribute('data-astryx-story-x')) {
          small[sb].removeAttribute('data-astryx-story-x');
        }
      }
    }
    var main = frame && frame.closest('[role="main"]');
    if (!main) {
      untag(); // choosing a story: Facebook's own page
      return;
    }
    hideSwitchRows();
    hideAIDisclosure();
    var nav = null; // the sidebar beside the preview (the page header is a navigation too)
    for (var sib = main.parentElement.firstElementChild; sib; sib = sib.nextElementSibling) {
      if (sib !== main && sib.getAttribute('role') === 'navigation') nav = sib;
    }
    if (!nav) return;
    // Under Facebook's header bar (56 px on the desktop site).
    var header = document.querySelector('[role="banner"]');
    var top = header ? header.getBoundingClientRect().bottom : 0;
    document.documentElement.style.setProperty('--astryx-story-top', (top > 0 ? top : 56) + 'px');
    mark(main, 'main');
    mark(nav, 'nav');

    // The tools: the sidebar's rows with an icon and a one-line label (the AI label row is
    // taller, with its explanation). Discard and Share to story: its labelled buttons without an
    // icon. The privacy button: its button with an icon and no label (in its header).
    var tools = [];
    var actions = null;
    var privacy = null;
    var buttons = nav.querySelectorAll('[role="button"]');
    for (var i = 0; i < buttons.length; i++) {
      var b = buttons[i];
      var labelled = /\S/.test(b.innerText || '');
      var icon = b.querySelector('svg, i, img');
      // A tool: an icon and a short label, at any height (two-line labels, a submenu arrow).
      // The AI label row has a checkbox and an explanation.
      var text = (b.innerText || '').replace(/\s+/g, ' ');
      if (labelled && icon && text.length <= 40 && !b.querySelector('input') && !isSwitchRow(b)) {
        tools.push(b);
      } else if (labelled && !icon && !actions) {
        actions = b.parentElement;
        while (actions && actions !== nav && actions.querySelectorAll('[role="button"]').length < 2) {
          actions = actions.parentElement;
        }
        if (actions === nav) actions = null;
      } else if (!labelled && icon && !privacy && b.getAttribute('aria-label')) {
        privacy = b;
      }
    }
    if (actions) mark(actions, 'actions');
    if (privacy) mark(privacy, 'privacy');
    // Facebook's narrow layout repeats privacy / Discard / Share in a row of its own (outside the
    // sidebar): it gives way to the bottom bar. Found by the bottom bar's own labels, so other
    // text buttons (a panel's "See all") stay; never a dialog's buttons ("Discard story?").
    hideNarrowRow(nav, frame, actions, privacy);

    if (!tools.length) return;
    var list = null;
    var best = 0;
    for (var t = 0; t < tools.length; t++) {
      var parent = tools[t].parentElement;
      var n = 0;
      for (var u = 0; u < tools.length; u++) if (tools[u].parentElement === parent) n++;
      if (n > best) { best = n; list = parent; }
    }
    var extras = [];
    for (var t2 = 0; t2 < tools.length; t2++) {
      if (tools[t2].parentElement === list) mark(tools[t2], 'tool');
      else { mark(tools[t2], 'tool-extra'); extras.push(tools[t2]); }
    }
    mark(list, 'tools');

    // Everything else in the sidebar is hidden (and in the tools' list whatever isn't a tool).
    // The boxes on the way to what's kept lose their clipping: the sidebar's scroller (overflow
    // hidden, will-change: transform) would clip them and hold them as their fixed position's frame.
    var keep = [list].concat(extras);
    if (actions) keep.push(actions);
    if (privacy) keep.push(privacy);
    function holdsKept(el) {
      for (var k = 0; k < keep.length; k++) if (el.contains(keep[k])) return true;
      return false;
    }
    function hideToolRowDetails(row) {
      function containsTool(el) {
        return el.matches('[data-astryx-story="tool"], [data-astryx-story="tool-extra"]') ||
          !!el.querySelector('[data-astryx-story="tool"], [data-astryx-story="tool-extra"]');
      }
      (function hideSiblings(el) {
        for (var child = el.firstElementChild; child; child = child.nextElementSibling) {
          if (child.matches('[data-astryx-story="tool"], [data-astryx-story="tool-extra"]')) continue;
          if (containsTool(child)) hideSiblings(child);
          else if (!child.hasAttribute('data-astryx-story-hide')) child.setAttribute('data-astryx-story-hide', '');
        }
      })(row);
    }
    (function hide(el) {
      for (var c = el.firstElementChild; c; c = c.nextElementSibling) {
        if (c === list) {
          var lastTool = null;
          for (var item = list.firstElementChild; item; item = item.nextElementSibling) {
            if (item.matches('[data-astryx-story="tool"], [data-astryx-story="tool-extra"]') ||
                item.querySelector('[data-astryx-story="tool"], [data-astryx-story="tool-extra"]')) {
              lastTool = item;
              if (!item.matches('[data-astryx-story="tool"], [data-astryx-story="tool-extra"]')) {
                hideToolRowDetails(item);
              }
            } else if (item.querySelector('textarea') || item.querySelectorAll('input').length >= 2) {
              // A tool's editor, open under it in the sidebar (Alternative text): a panel of its own.
              item.removeAttribute('data-astryx-story-hide');
              mark(item, 'sheet');
              if (lastTool) addSheetDone(item, lastTool);
            } else if (!item.hasAttribute('data-astryx-story-hide')) {
              item.setAttribute('data-astryx-story-hide', '');
            }
          }
        } else if (keep.indexOf(c) !== -1) {
          continue;
        } else if (holdsKept(c)) {
          if (!c.hasAttribute('data-astryx-story-path')) c.setAttribute('data-astryx-story-path', '');
          hide(c);
        } else if (!c.hasAttribute('data-astryx-story-hide')) {
          c.setAttribute('data-astryx-story-hide', '');
        }
      }
    })(nav);

    // A tool shows its first icon only (one that opens a submenu, like Apps, has an arrow too).
    for (var ti = 0; ti < tools.length; ti++) {
      var icons = tools[ti].querySelectorAll('svg, i, img');
      for (var ii = 1; ii < icons.length; ii++) {
        if (!icons[0].contains(icons[ii]) && !icons[ii].hasAttribute('data-astryx-story-hide')) {
          icons[ii].setAttribute('data-astryx-story-hide', '');
        }
      }
    }
    // The tools along the bottom of the story, as wide as it (measured once the rest of their
    // list is hidden); never scrolled sideways.
    var frameBox = frame.getBoundingClientRect();
    var rootStyle = document.documentElement.style;
    var rowLeft = frameBox.left + TOOLS_LEFT_INSET;
    var rowWidth = Math.max(0, frameBox.width - TOOLS_LEFT_INSET - TOOLS_RIGHT_INSET);
    var slot = rowWidth / tools.length;
    var listHeight = list.getBoundingClientRect().height;
    var toolsTop = Math.max(0, frameBox.bottom - listHeight - TOOLS_GAP);
    rootStyle.setProperty('--astryx-tools-left', Math.round(rowLeft + extras.length * slot) + 'px');
    rootStyle.setProperty('--astryx-tools-width', Math.round(rowWidth - extras.length * slot) + 'px');
    rootStyle.setProperty('--astryx-tools-top', toolsTop + 'px');
    rootStyle.setProperty('--astryx-tool-slot', Math.floor(slot) + 'px');
    // An extra (Apps) has no round background behind its icon, so its icon and label sat higher
    // than the others': its icon is lined up with theirs, and its label too.
    var ref = list.querySelector('[data-astryx-story="tool"]');
    var refIcon = ref && ref.querySelector('svg, i, img');
    var refLabel = ref && toolLabel(ref);
    for (var e = 0; e < extras.length; e++) {
      var extra = extras[e];
      extra.style.setProperty('left', Math.round(rowLeft + e * slot) + 'px', 'important');
      var top = toolsTop;
      var icon = extra.querySelector('svg, i, img');
      var label = toolLabel(extra);
      if (refIcon && icon) {
        var iconOffset = refIcon.getBoundingClientRect().top - ref.getBoundingClientRect().top;
        top = toolsTop + iconOffset - (icon.getBoundingClientRect().top - extra.getBoundingClientRect().top);
      }
      extra.style.setProperty('top', Math.round(top) + 'px', 'important');
      if (refIcon && icon && refLabel && label) {
        var shift = (refLabel.getBoundingClientRect().top - refIcon.getBoundingClientRect().top) -
          (label.getBoundingClientRect().top - icon.getBoundingClientRect().top);
        var current = parseFloat(label.style.marginTop) || 0;
        if (Math.abs(shift) >= 1) label.style.setProperty('margin-top', Math.round(current + shift) + 'px', 'important');
      }
    }
    if (list.scrollLeft) list.scrollLeft = 0;

  }

  // A tool's label: its last span with text.
  function toolLabel(tool) {
    var spans = tool.querySelectorAll('span');
    for (var i = spans.length - 1; i >= 0; i--) {
      if (/\S/.test(spans[i].innerText || '')) return spans[i];
    }
    return null;
  }

  function hideNarrowRow(nav, frame, actions, privacy) {
    var privacyLabel = privacy && privacy.getAttribute('aria-label');
    var actionLabels = [];
    if (actions) {
      var actionButtons = actions.querySelectorAll('[role="button"]');
      for (var a = 0; a < actionButtons.length; a++) actionLabels.push((actionButtons[a].innerText || '').replace(/^\s+|\s+$/g, ''));
    }
    var all = document.querySelectorAll('[role="button"]');
    for (var o = 0; o < all.length; o++) {
      var other = all[o];
      if (nav.contains(other) || other.closest('[role="dialog"]') || other.closest('[data-astryx-story-hide]')) continue;
      if (privacyLabel && other.getAttribute('aria-label') === privacyLabel) {
        other.setAttribute('data-astryx-story-hide', ''); // its privacy button, kept in the bottom bar
        continue;
      }
      if (actionLabels.indexOf((other.innerText || '').replace(/^\s+|\s+$/g, '')) === -1) continue;
      var row = other.parentElement;
      while (row && row !== document.body && row.querySelectorAll('[role="button"]').length < 2) row = row.parentElement;
      if (row && row !== document.body && !row.contains(frame) && !row.contains(nav)) {
        row.setAttribute('data-astryx-story-hide', '');
      }
    }
  }

  function addStyle() {
    style = document.createElement('style');
    style.textContent =
      '[data-astryx-story="main"] { position: fixed !important; left: 0 !important; right: 0 !important;' +
      ' top: var(--astryx-story-top, 56px) !important; bottom: ' + BAR_HEIGHT + 'px !important; width: auto !important;' +
      ' max-width: none !important; min-width: 0 !important; margin: 0 !important; z-index: 1 !important;' +
      ' overflow: hidden auto !important; touch-action: none !important; }' +
      '[data-astryx-story="nav"] { position: fixed !important; left: 0 !important; right: 0 !important;' +
      ' bottom: 0 !important; top: auto !important; width: auto !important; max-width: none !important;' +
      ' height: 0 !important; min-height: 0 !important; overflow: visible !important; z-index: 2 !important;' +
      ' background: none !important; border: 0 !important; }' +
      '[data-astryx-story-hide] { display: none !important; }' +
      '[data-astryx-story="actions"] { position: fixed !important; left: 64px !important; right: 12px !important;' +
      ' bottom: 0 !important; top: auto !important; height: ' + BAR_HEIGHT + 'px !important; width: auto !important;' +
      ' display: flex !important; align-items: center !important; justify-content: flex-end !important;' +
      ' gap: 8px !important; padding: 0 !important; margin: 0 !important; background: none !important; }' +
      '[data-astryx-story="actions"] > * { flex: 1 1 0 !important; max-width: 50% !important; margin: 0 !important; }' +
      '[data-astryx-story="privacy"] { position: fixed !important; left: 12px !important;' +
      ' bottom: ' + ((BAR_HEIGHT - 40) / 2) + 'px !important; top: auto !important; width: 40px !important;' +
      ' height: 40px !important; margin: 0 !important; }' +
      // Facebook's popups (the music list, text style, a sticker's options) open beside the
      // sidebar's tool, off the screen now: centred under the header instead, clear of the
      // keyboard. They're fixed layers placed with an inline translate().
      '[data-astryx-story-popup] { left: 50% !important; top: calc(var(--astryx-story-top, 56px) + 8px) !important;' +
      ' transform: translateX(-50%) !important; max-width: calc(100vw - 16px) !important; }' +
      // The app's own Copy and Download buttons (for media pages) don't belong on the story.
      'html body #materialbook-clipboard-copier, html body #materialbook-global-downloader,' +
      ' html body #materialbook-clipboard-copier.visible, html body #materialbook-global-downloader.visible' +
      ' { display: none !important; }' +
      'html body [data-astryx-story-done] { display: block !important; margin: 0 12px 12px !important;' +
      ' padding: 8px 0 !important; border-radius: 8px !important; background: #0866ff !important;' +
      ' color: #fff !important; font: 600 15px/20px system-ui, sans-serif !important;' +
      ' text-align: center !important; cursor: pointer !important; }' +
      // Labelled icon buttons in Facebook's panels (see labelControls).
      // (html body: Facebook's own classes clip the buttons otherwise.) Long names keep two lines.
      'html body [data-astryx-story-caption] { position: relative !important; overflow: visible !important; }' +
      'html body [data-astryx-story-caption]::after { content: attr(aria-label); position: absolute;' +
      ' top: calc(100% + 3px); left: 50%; transform: translateX(-50%); width: 68px;' +
      ' font: 600 10px/12px system-ui, sans-serif; color: #fff; text-align: center;' +
      ' text-shadow: 0 1px 2px rgba(0, 0, 0, .9); white-space: normal; pointer-events: none;' +
      ' display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }' +
      'html body [data-astryx-story-captioned] { margin-bottom: 28px !important;' +
      ' max-width: calc(100vw - 16px) !important; height: auto !important; overflow: visible !important; }' +
      'html body [data-astryx-story-captioned] > * { flex-shrink: 1 !important; min-width: 0 !important;' +
      ' align-self: flex-start !important; }' +
      'html body [data-astryx-story-captioned] > :not(:has([data-astryx-story-caption])) { overflow: hidden !important; }' +
      '[data-astryx-story-path] { overflow: visible !important; will-change: auto !important;' +
      ' transform: none !important; contain: none !important; filter: none !important;' +
      ' perspective: none !important; backdrop-filter: none !important; transform-style: flat !important; }' +
      '[data-astryx-story-idle] { pointer-events: none !important; }' +
      'html body [data-astryx-story-x] { background: rgba(0, 0, 0, .7) !important; border-radius: 50% !important;' +
      ' box-shadow: 0 0 0 2px #fff !important; color: #fff !important; opacity: 1 !important; }' +
      'html body [data-astryx-story-x] svg, html body [data-astryx-story-x] path { fill: #fff !important;' +
      ' stroke: #fff !important; color: #fff !important; }' +
      'html body [data-astryx-story-x]::before { content: ""; position: absolute; inset: -12px; }' +
      '[data-astryx-story-music-hidden], [data-astryx-story-music-hidden] * {' +
      ' visibility: hidden !important; pointer-events: none !important; }' +
      '[data-astryx-story="sheet"] { position: fixed !important; left: 12px !important; right: 12px !important;' +
      ' top: calc(var(--astryx-story-top, 56px) + 8px) !important; bottom: auto !important; width: auto !important;' +
      ' max-height: calc(100vh - var(--astryx-story-top, 56px) - ' + (BAR_HEIGHT + 24) + 'px) !important;' +
      ' overflow: auto !important; z-index: 4 !important; padding: 12px !important; margin: 0 !important;' +
      ' border-radius: 12px !important; background: #242526 !important; color: #e4e6eb !important;' +
      ' box-shadow: 0 4px 16px rgba(0, 0, 0, .5) !important; }' +
      '[data-astryx-story="sheet"] [data-astryx-story-done] { margin: 12px 0 0 !important; }' +
      '[data-astryx-story="tools"] { position: fixed !important; left: var(--astryx-tools-left, 0) !important;' +
      ' right: auto !important; width: var(--astryx-tools-width, 100vw) !important;' +
      ' top: var(--astryx-tools-top, 50vh) !important; bottom: auto !important; display: flex !important;' +
      ' flex-direction: row !important; flex-wrap: nowrap !important; justify-content: center !important;' +
      ' align-items: flex-start !important; gap: 2px !important; overflow: visible !important;' +
      ' padding: 0 !important; margin: 0 !important; height: auto !important;' +
      ' background: none !important; }' +
      '[data-astryx-story="tool"] { display: flex !important; flex-direction: column !important;' +
      ' align-items: center !important; justify-content: flex-start !important; flex: 1 1 0 !important;' +
      ' min-width: 0 !important; max-width: 80px !important; width: auto !important;' +
      ' height: auto !important; padding: 4px 0 !important; margin: 0 !important; background: none !important; }' +
      '[data-astryx-story="tool"] > * { margin: 0 !important; padding: 0 !important; max-width: 100% !important; }' +
      '[data-astryx-story="tool-extra"] { position: fixed !important; z-index: 3 !important;' +
      ' width: var(--astryx-tool-slot, 64px) !important; display: flex !important; flex-direction: column !important;' +
      ' align-items: center !important; justify-content: flex-start !important; height: auto !important;' +
      ' padding: 4px 0 !important; margin: 0 !important; background: none !important; }' +
      '[data-astryx-story="tool-extra"] > * { margin: 0 !important; padding: 0 !important; max-width: 100% !important; }' +
      '[data-astryx-story="tool-extra"] div { flex-direction: column !important; align-items: center !important;' +
      ' margin-left: 0 !important; margin-right: 0 !important; padding-left: 0 !important;' +
      ' padding-right: 0 !important; max-width: 100% !important; }' +
      '[data-astryx-story="tool-extra"] span { font-size: 11px !important; line-height: 13px !important;' +
      ' text-align: center !important; white-space: normal !important; color: #fff !important;' +
      ' text-shadow: 0 1px 3px rgba(0, 0, 0, .9) !important; }' +
      // Icon above label: Facebook's rows inside the button stack, and the boxes around the
      // label (sized for the sidebar) take the label's width.
      '[data-astryx-story="tool"] div { flex-direction: column !important; align-items: center !important;' +
      ' justify-content: flex-start !important;' +
      ' margin-left: 0 !important; margin-right: 0 !important; padding-left: 0 !important;' +
      ' padding-right: 0 !important; max-width: 100% !important; }' +
      '[data-astryx-story="tool"] div:not(:has(svg, i, img)) { width: auto !important; min-width: 0 !important;' +
      ' flex: none !important; height: auto !important; }' +
      '[data-astryx-story="tool"] span { font-size: 11px !important; line-height: 13px !important;' +
      ' text-align: center !important; white-space: normal !important; overflow-wrap: normal !important;' +
      ' word-break: normal !important; hyphens: manual !important; display: block !important;' +
      ' width: auto !important; max-width: none !important; height: auto !important; max-height: none !important;' +
      ' overflow: visible !important; -webkit-line-clamp: unset !important;' +
      ' color: #fff !important; text-shadow: 0 1px 3px rgba(0, 0, 0, .9) !important; }';
    (document.head || document.documentElement).appendChild(style);
  }

  // Facebook's editor moves its stickers and text (and pans the photo) with the mouse only; a
  // finger did nothing. Once a finger has moved, its drag is replayed as a mouse drag (pointer and
  // mouse events, aimed at where it started); taps stay the browser's own. A sticker's controls
  // (its × to remove it, its resize handles) show while the mouse is over it: after a tap, the
  // mouse is put where the finger was (and taken away from where it was before).
  var hovered = null;
  function hoverAt(x, y) {
    var target = document.elementFromPoint(x, y);
    if (hovered && hovered.target !== target) {
      mouseEvent(hovered.target, 'pointerout', hovered.x, hovered.y);
      mouseEvent(hovered.target, 'pointerleave', hovered.x, hovered.y);
      mouseEvent(hovered.target, 'mouseout', hovered.x, hovered.y);
      mouseEvent(hovered.target, 'mouseleave', hovered.x, hovered.y);
      hovered = null;
    }
    var main = document.querySelector('[data-astryx-story="main"]');
    if (!target || !main || !main.contains(target)) return;
    if (!hovered) {
      mouseEvent(target, 'pointerover', x, y);
      mouseEvent(target, 'pointerenter', x, y);
      mouseEvent(target, 'mouseover', x, y);
      mouseEvent(target, 'mouseenter', x, y);
    }
    mouseEvent(target, 'pointermove', x, y);
    mouseEvent(target, 'mousemove', x, y);
    hovered = { target: target, x: x, y: y };
  }

  function dragByTouch() {
    var start = null;
    var dragging = false;
    function fire(touch, mouseType, pointerType, buttons) {
      var init = {
        bubbles: true, cancelable: true, composed: true, view: window,
        clientX: touch.clientX, clientY: touch.clientY, screenX: touch.screenX, screenY: touch.screenY,
        button: 0, buttons: buttons
      };
      if (pointerType && window.PointerEvent) {
        var p = {};
        for (var k in init) p[k] = init[k];
        p.pointerId = 1;
        p.pointerType = 'mouse';
        p.isPrimary = true;
        start.target.dispatchEvent(new PointerEvent(pointerType, p));
      }
      start.target.dispatchEvent(new MouseEvent(mouseType, init));
    }
    on(document, 'touchstart', function (e) {
      var main = document.querySelector('[data-astryx-story="main"]');
      if (e.touches.length !== 1 || !main || !main.contains(e.target)) { start = null; return; }
      var t = e.touches[0];
      start = { target: e.target, x: t.clientX, y: t.clientY, touch: t };
      dragging = false;
    }, { capture: true, passive: true });
    on(document, 'touchmove', function (e) {
      if (!start || e.touches.length !== 1) return;
      var t = e.touches[0];
      if (!dragging) {
        if (Math.abs(t.clientX - start.x) + Math.abs(t.clientY - start.y) < 8) return;
        dragging = true;
        fire(start.touch, 'mousedown', 'pointerdown', 1);
      }
      e.preventDefault();
      fire(t, 'mousemove', 'pointermove', 1);
    }, { capture: true, passive: false });
    on(document, 'touchend', function (e) {
      if (!start) return;
      var end = e.changedTouches[0];
      if (dragging) {
        fire(end, 'mouseup', 'pointerup', 0);
        e.preventDefault(); // no click at the end of a drag
      }
      // After the page has handled the tap (a sticker can re-render), or at the end of a drag.
      var x = end.clientX;
      var y = end.clientY;
      setTimeout(function () { hoverAt(x, y); }, 350);
      start = null;
      dragging = false;
    }, { capture: true, passive: false });
  }

  viewport();
  observe(document.head || document.documentElement,
    { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] }, viewport);
  addStyle();
  layout();
  var pending = null;
  observe(document.body, { childList: true, subtree: true }, function () {
    if (!pending) pending = setTimeout(function () { pending = null; layout(); }, 150);
  });
  timers.push(setInterval(layout, 500));
  dragByTouch();

  window.__astryxStoryLayout = {
    stop: function () {
      for (var i = 0; i < timers.length; i++) clearInterval(timers[i]);
      for (var j = 0; j < observers.length; j++) observers[j].disconnect();
      for (var k = 0; k < listeners.length; k++) {
        var l = listeners[k];
        l[0].removeEventListener(l[1], l[2], l[3]);
      }
      if (style) style.remove();
      untag();
      var added = document.querySelectorAll('[data-astryx-story-done]');
      for (var d = 0; d < added.length; d++) added[d].remove();
      try {
        if (ownInnerHeight) Object.defineProperty(window, 'innerHeight', ownInnerHeight);
        else delete window.innerHeight;
      } catch (e) { /* stays reported */ }
      reported = 0;
      window.dispatchEvent(new Event('resize'));
      delete window.__astryxStoryLayout;
    }
  };
})();
