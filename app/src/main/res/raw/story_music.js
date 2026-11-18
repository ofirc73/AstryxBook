// The native share screen's music picker, on the page side (Messages layer, Facebook's desktop story
// creator; a document-start script, so it also sees what the creator loads with). Read on the
// emulator 2026-11-18 (NATIVE_SHARE_PLAN.md):
// - The songs: Facebook's music panel asks for them with the GraphQL query
//   StoriesCreateMusicSelectorMainPageQuery (its "For you" list as the creator loads, a search on
//   Enter); each answer's items are AudioAssets {id, title, display_artist, cover_artwork,
//   duration_in_ms}. XMLHttpRequest is wrapped to read them, and they go to the app
//   (AstryxShare {call: "music", target: "story", songs, query}).
// - The panel (story_layout.js marks it as a popup): the Add music tool opens it; its search box
//   (input[aria-label="Search music"]) searches on Enter after typed input (insertText, in a focused
//   page; a value set by script isn't searched); its rows (a [role=button] holding the
//   title and the artist, a Play button) preview (Play) and choose (the row). Chosen, Facebook
//   shows the song's card (lyrics styles, Done) and a clip bar under the story: the start is the
//   scroll of its waveform strip (a horizontally scrolling box), which only real drags move, so the
//   app drags it (strip() says where it is). Facebook keeps the start from the scroll, but plays it
//   again from there only on a real mouse button's release: replay() presses the bar's Pause and
//   Play instead, and position() then reads where the preview plays, the start Facebook took. The
//   bar's Done keeps the song.
// The app calls window.__astryxStoryMusic: open(), search(text), play(id), choose(id), style(n),
// strip(), done(); each returns at once, and its result shows in state(). ES5, like the PiP scripts.
(function () {
  if (window.__astryxStoryMusic) return;
  var QUERY = /MusicSelector/;
  var songs = {}; // by id
  var lists = {}; // the ids each query gave, in order ('' : For you)
  var lastQuery = '';
  var playedAt = 0; // when replay() pressed Play

  function inCreator() { return location.pathname.indexOf('/stories/create') === 0; }

  function report(query) {
    var bridge = window.AstryxShare;
    if (!bridge) return;
    bridge.postMessage(JSON.stringify({ call: 'music', target: 'story', query: query, songs: (lists[query] || []).map(function (id) { return songs[id]; }) }));
  }

  // The AudioAssets anywhere in a GraphQL answer (its lines are JSON objects, one per part).
  function take(text, query) {
    var ids = [];
    function walk(o, depth) {
      if (!o || typeof o !== 'object' || depth > 14) return;
      if (o.__typename === 'AudioAsset' && o.id) {
        if (!songs[o.id]) {
          songs[o.id] = {
            id: String(o.id),
            title: (o.title && o.title.text) || (o.display_title && o.display_title.text) || '',
            artist: (o.display_artist && o.display_artist.text) || '',
            cover: (o.cover_artwork && o.cover_artwork.uri) || (o.display_image && o.display_image.uri) || '',
            durationMs: o.duration_in_ms || 0
          };
        }
        if (ids.indexOf(String(o.id)) < 0) ids.push(String(o.id));
        return;
      }
      for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) walk(o[k], depth + 1);
    }
    String(text).split('\n').forEach(function (line) {
      try { walk(JSON.parse(line.replace(/^for \(;;\);/, '')), 0); } catch (e) { /* not JSON */ }
    });
    if (!ids.length) return;
    lists[query] = ids;
    lastQuery = query;
    report(query);
  }

  function searchTextOf(body) {
    var vars = (String(body).match(/(?:^|&)variables=([^&]*)/) || [])[1];
    try { return (JSON.parse(decodeURIComponent(vars)).params || {}).search_text || ''; } catch (e) { return ''; }
  }

  var send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.send = function (body) {
    var text = typeof body === 'string' ? body : (body && typeof body.toString === 'function' ? body.toString() : '');
    if (QUERY.test(text) && inCreator()) {
      var xhr = this, query = searchTextOf(text);
      xhr.addEventListener('load', function () { take(xhr.responseText, query); });
    }
    return send.apply(this, arguments);
  };

  // --- The panel ---

  function popup() { return document.querySelector('[data-astryx-story-popup]'); }
  function searchBox() { return document.querySelector('input[aria-label="Search music"], [data-astryx-story-popup] input[type="text"]'); }
  function musicTool() {
    var main = document.querySelector('[data-astryx-story="main"]');
    if (!main || !main.querySelector('img, video, canvas')) return null;
    var tools = document.querySelectorAll('[data-astryx-story="tool"]');
    for (var i = 0; i < tools.length; i++) if (/music/i.test(tools[i].innerText || '')) return tools[i];
    return tools.length > 1 ? tools[1] : null;
  }
  function rows() {
    var pop = popup();
    if (!pop) return [];
    return [].filter.call(pop.querySelectorAll('[role="button"]'), function (b) {
      return b.getBoundingClientRect().height > 30 && !!b.querySelector('[aria-label="Play"], [aria-label="Pause"]');
    });
  }
  // The panel's row for [id]: its title and artist as the song's.
  function rowOf(id) {
    var song = songs[id];
    if (!song) return null;
    var list = rows();
    for (var i = 0; i < list.length; i++) {
      var lines = (list[i].innerText || '').split('\n').map(function (s) { return s.trim(); });
      if (lines[0] === song.title && (!song.artist || lines.indexOf(song.artist) > 0)) return list[i];
    }
    return null;
  }
  function setValue(field, text) {
    var setter = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(field), 'value');
    if (setter && setter.set) setter.set.call(field, text); else field.value = text;
    field.dispatchEvent(new Event('input', { bubbles: true }));
  }
  function enter(field) {
    ['keydown', 'keypress', 'keyup'].forEach(function (type) {
      field.dispatchEvent(new KeyboardEvent(type, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
    });
  }
  // The clip bar's waveform strip: a scrolling box of many bars (only while a song is chosen).
  function stripBox() {
    return [].filter.call(document.querySelectorAll('div'), function (d) {
      return d.children.length > 50 && getComputedStyle(d).overflowX === 'scroll' && d.getBoundingClientRect().width > 0;
    })[0] || null;
  }
  // The chosen song's card: its lyrics style buttons (in order) and its Done.
  function styleButtons() { return document.querySelectorAll('[aria-label$="lyrics style" i], [aria-label$="Lyrics style"]'); }
  function barDone() {
    var strip = stripBox();
    if (!strip) return null;
    var dones = [].filter.call(document.querySelectorAll('[aria-label="Done"]'), function (b) {
      return Math.abs(b.getBoundingClientRect().top - strip.getBoundingClientRect().top) < 60;
    });
    return dones[0] || null;
  }
  function cardDone() {
    var style = styleButtons()[0];
    if (!style) return null;
    var card = style;
    while (card && !(card.querySelector('[aria-label$="yrics style"]') && /\bDone\b/.test(card.innerText || '') && card.querySelectorAll('[role="button"]').length > 4)) card = card.parentElement;
    if (!card) return null;
    return [].filter.call(card.querySelectorAll('[role="button"]'), function (b) { return (b.innerText || '').trim() === 'Done'; })[0] || null;
  }

  var api = {
    // Opens the music panel once the editor shows the story (the tool is usable).
    open: function () {
      if (popup()) return 'open';
      var tool = musicTool();
      if (!tool || tool.getAttribute('tabindex') === '-1' || tool.getAttribute('aria-disabled') === 'true') return 'notReady';
      tool.click();
      // The For you list the creator loaded with, again for a picker opened after it came.
      if (lists['']) report('');
      return 'opening';
    },
    search: function (text) {
      var box = searchBox();
      if (!box) return 'noPanel';
      if (text === '' && lists['']) { report(''); return 'shown'; }
      if (lists[text]) { lastQuery = text; report(text); }
      // Typed as typing would (insertText, with the page focused: the app focuses its WebView first);
      // the value set as React sees it otherwise. Facebook searches on Enter only after real input.
      box.focus();
      box.select();
      var typed = text ? document.execCommand('insertText', false, text) : document.execCommand('delete');
      if (!typed || box.value !== text) setValue(box, text);
      enter(box);
      return 'searching';
    },
    // A song's preview: its row's Play (Facebook plays it in the page); again to stop.
    play: function (id) {
      var row = rowOf(id);
      var button = row && row.querySelector('[aria-label="Play"], [aria-label="Pause"]');
      if (!button) return 'noRow';
      button.click();
      return 'ok';
    },
    choose: function (id) {
      var row = rowOf(id);
      if (!row) return 'noRow';
      row.click();
      return 'ok';
    },
    style: function (n) {
      var buttons = styleButtons();
      if (!buttons[n]) return 'noStyle';
      buttons[n].click();
      return 'ok';
    },
    // Where the clip bar's strip is (CSS px in the viewport) and how far it's scrolled, for the
    // app's drags; null without one.
    strip: function () {
      var s = stripBox();
      if (!s) return null;
      var r = s.getBoundingClientRect();
      return { left: r.left, top: r.top, width: r.width, height: r.height, scroll: s.scrollLeft,
        range: s.scrollWidth - s.clientWidth, viewport: (window.visualViewport || window).width || window.innerWidth };
    },
    // The chosen song played again from its clip's start: the clip bar's Pause, then (once Facebook
    // shows it) its Play.
    replay: function () {
      var pause = document.querySelector('[aria-label="Pause"]');
      if (pause) pause.click();
      playedAt = 0;
      var tries = 0;
      (function play() {
        var button = document.querySelector('[aria-label="Play"]');
        if (button) { button.click(); playedAt = Date.now(); return; }
        if (++tries < 20) setTimeout(play, 100);
      })();
      return 'ok';
    },
    // Where the chosen song's clip starts, in ms: where its preview plays, less the time since
    // replay() played it (-1 before it has).
    position: function () {
      var audio = document.querySelector('audio');
      if (!audio || !playedAt || audio.paused) return -1;
      return Math.max(0, Math.round(audio.currentTime * 1000 - (Date.now() - playedAt)));
    },
    // The chosen song kept: the card's Done, then the clip bar's.
    done: function () {
      var card = cardDone();
      if (card) card.click();
      var bar = barDone();
      if (bar) bar.click();
      return card || bar ? 'ok' : 'none';
    },
    state: function () {
      return { panel: !!popup(), rows: rows().length, chosen: !!stripBox(), lastQuery: lastQuery,
        known: Object.keys(songs).length, ready: !!musicTool() };
    },
    songs: function (query) { return (lists[query] || []).map(function (id) { return songs[id]; }); }
  };
  window.__astryxStoryMusic = api;
})();
