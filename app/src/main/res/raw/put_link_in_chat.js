// Messages layer: puts a page's link (a post sent with Send as a message) in the open chat's message
// box, for the user to send. The desktop site's own Send in Messenger dialog sent an attachment an
// end-to-end encrypted chat showed empty (2026-10-28); typed into the box, Messenger builds its own
// preview of the link. The box is Messenger's Lexical editor (role=textbox, contenteditable), which
// takes the browser's insertText command once the page has the focus (a tap on the chat gives it).
// Run with window.__astryxLinkToSend set (null stops it). Watches the page from then on and puts the
// link in as soon as a chat's box appears: asked every 0.7 s from the app instead, the link came some
// 4 s after the chat was tapped (2026-10-28). Answers "placed" once the box holds the link, "no box",
// "no focus", or "inserted" (Lexical shows it a moment later).
(function () {
  var link = window.__astryxLinkToSend;
  if (!link) return 'no link';

  function findBox() {
    var box = null;
    var boxes = document.querySelectorAll('[contenteditable="true"][role="textbox"]');
    for (var i = 0; i < boxes.length; i++) {
      var r = boxes[i].getBoundingClientRect();
      if (r.width > 0 && r.height > 0) box = boxes[i];
    }
    return box;
  }

  function tryPlace() {
    var box = findBox();
    if (!box) return 'no box';
    if ((box.innerText || '').indexOf(link) >= 0) return 'placed';
    // One insert at a time. An insert that takes shows within some 10 ms; one made as the box appears,
    // before Lexical has taken it over, is lost, and the next try (0.2 s on) takes (2026-10-28).
    if (window.__astryxLinkTried === link && Date.now() - (window.__astryxLinkTriedAt || 0) < 200) return 'inserted';
    box.focus();
    if (!document.hasFocus()) return 'no focus';
    window.__astryxLinkTried = link;
    window.__astryxLinkTriedAt = Date.now();
    document.execCommand('insertText', false, link);
    return 'inserted';
  }

  if (window.__astryxLinkWatch !== link) {
    window.__astryxLinkWatch = link;
    var queued = false;
    var observer = null;
    var timer = null;
    var stop = function () {
      if (observer) observer.disconnect();
      window.removeEventListener('focus', kick, true);
      clearInterval(timer);
      window.__astryxLinkWatch = null;
    };
    var check = function () {
      queued = false;
      if (window.__astryxLinkToSend !== link) return stop();
      if (tryPlace() === 'placed') stop();
    };
    // Every change to the page (the chat's box appearing) and the page getting the focus, at most
    // once a frame; and every 250 ms, for a box that changes without the page changing around it.
    var kick = function () {
      if (queued) return;
      queued = true;
      requestAnimationFrame(check);
    };
    observer = new MutationObserver(kick);
    observer.observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener('focus', kick, true);
    timer = setInterval(kick, 250);
  }
  return tryPlace();
})()
