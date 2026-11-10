// Messages layer: the open chat's message box, and each chat's unsent message.
//
// The box is Messenger's Lexical editor (role=textbox, contenteditable), which takes the browser's
// insertText command once the page has the focus (a tap on the chat gives it). find() and insert()
// are shared with put_link_in_chat.js (window.__astryxChatBox).
//
// Messenger keeps what's typed in a chat's box only while its page lives: back to the chat list
// and into the chat again keeps it, but Back on the chat list closes the layer, and the chat opened
// again had an empty box (2026-11-10, the user's report). So what's typed is kept per chat in
// localStorage (on input, a key or a click, 0.3 s later: Enter and Send empty the box without an
// input event), and put back when the chat's box next shows empty. Kept 30 days.
(function () {
  if (window.__astryxChatBox) return;
  var KEY = 'astryxChatDrafts';
  var KEEP_MS = 30 * 24 * 3600 * 1000;

  function find() {
    var box = null;
    var boxes = document.querySelectorAll('[contenteditable="true"][role="textbox"]');
    for (var i = 0; i < boxes.length; i++) {
      var r = boxes[i].getBoundingClientRect();
      if (r.width > 0 && r.height > 0) box = boxes[i];
    }
    return box;
  }

  // What the box holds (an empty Lexical editor reads "\n").
  function text(box) {
    return (box.innerText || '').replace(/\n+$/, '');
  }

  // One insert at a time. An insert that takes shows within some 10 ms; one made as the box appears,
  // before Lexical has taken it over, is lost, and the next try (0.2 s on) takes (2026-10-28).
  var tried = null;
  var triedAt = 0;
  function insert(box, value) {
    if (tried === value && Date.now() - triedAt < 200) return 'inserted';
    box.focus();
    if (!document.hasFocus()) return 'no focus';
    tried = value;
    triedAt = Date.now();
    document.execCommand('insertText', false, value);
    return 'inserted';
  }

  // The open chat ("t/<id>", "e2ee/t/<id>"), or null.
  function chat() {
    var m = location.pathname.match(/^\/messages\/((?:e2ee\/)?t\/[^\/]+)/);
    return m ? m[1] : null;
  }

  function drafts() {
    var all = {};
    try { all = JSON.parse(localStorage.getItem(KEY)) || {}; } catch (e) {}
    var now = Date.now();
    for (var c in all) if (!all[c] || now - all[c].at > KEEP_MS) delete all[c];
    return all;
  }

  function keep(all) {
    try { localStorage.setItem(KEY, JSON.stringify(all)); } catch (e) {}
  }

  // Chats whose kept message is back in their box (or that had none, or held something already):
  // from then on what their box holds is what's kept.
  var settled = {};

  function restore() {
    var c = chat();
    if (!c || settled[c]) return;
    var box = find();
    if (!box) return;
    var draft = drafts()[c];
    if (!draft || text(box).trim() !== '') {
      settled[c] = true;
      return;
    }
    insert(box, draft.text);
  }

  function save(c) {
    if (!settled[c] || chat() !== c) return;
    var box = find();
    if (!box) return;
    var all = drafts();
    var t = text(box);
    if (t.trim()) all[c] = { text: t, at: Date.now() };
    else delete all[c];
    keep(all);
  }

  function saveSoon() {
    var c = chat();
    if (c) setTimeout(function () { save(c); }, 300);
  }

  // Every change to the page (a chat's box appearing) and the page getting the focus, at most once
  // a frame.
  var queued = false;
  function kick() {
    if (queued) return;
    queued = true;
    requestAnimationFrame(function () {
      queued = false;
      restore();
    });
  }

  window.__astryxChatBox = { find: find, insert: insert };
  new MutationObserver(kick).observe(document.documentElement, { childList: true, subtree: true });
  window.addEventListener('focus', kick, true);
  document.addEventListener('input', saveSoon, true);
  document.addEventListener('keyup', saveSoon, true);
  document.addEventListener('click', saveSoon, true);
  kick();
})();
