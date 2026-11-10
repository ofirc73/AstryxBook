// Messages layer: puts a page's link (a post sent with Send as a message) in the open chat's message
// box, for the user to send. The desktop site's own Send in Messenger dialog sent an attachment an
// end-to-end encrypted chat showed empty (2026-10-28); typed into the box, Messenger builds its own
// preview of the link. The box is found and typed into by chat_box.js (window.__astryxChatBox).
// Run with window.__astryxLinkToSend set (null stops it). Watches the page from then on and puts the
// link in as soon as a chat's box appears: asked every 0.7 s from the app instead, the link came some
// 4 s after the chat was tapped (2026-10-28). Answers "placed" once the box holds the link, "no box",
// "no focus", or "inserted" (Lexical shows it a moment later).
(function () {
  var link = window.__astryxLinkToSend;
  if (!link) return 'no link';

  function tryPlace() {
    // chat_box.js runs as the page finishes loading: until then, no box.
    var chatBox = window.__astryxChatBox;
    var box = chatBox && chatBox.find();
    if (!box) return 'no box';
    if ((box.innerText || '').indexOf(link) >= 0) return 'placed';
    return chatBox.insert(box, link);
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
