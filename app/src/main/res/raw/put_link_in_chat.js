// Messages layer: puts a page's link (a post sent with Send as a message) in the open chat's message
// box, for the user to send. The desktop site's own Send in Messenger dialog sent an attachment an
// end-to-end encrypted chat showed empty (2026-10-28); typed into the box, Messenger builds its own
// preview of the link. The box is Messenger's Lexical editor (role=textbox, contenteditable), which
// takes the browser's insertText command once the page has the focus (a tap on the chat gives it).
// Run with window.__astryxLinkToSend set; answers "placed" once the box holds the link, "no box",
// "no focus", or "inserted" (Lexical shows it a moment later: asked again).
(function () {
  var link = window.__astryxLinkToSend;
  if (!link) return 'no link';
  var box = null;
  var boxes = document.querySelectorAll('[contenteditable="true"][role="textbox"]');
  for (var i = 0; i < boxes.length; i++) {
    var r = boxes[i].getBoundingClientRect();
    if (r.width > 0 && r.height > 0) box = boxes[i];
  }
  if (!box) return 'no box';
  if ((box.innerText || '').indexOf(link) >= 0) return 'placed';
  // One insert at a time: Lexical shows the text a moment after the command.
  var tried = window.__astryxLinkTriedAt || 0;
  if (window.__astryxLinkTried === link && Date.now() - tried < 2000) return 'inserted';
  box.focus();
  if (!document.hasFocus()) return 'no focus';
  window.__astryxLinkTried = link;
  window.__astryxLinkTriedAt = Date.now();
  document.execCommand('insertText', false, link);
  return 'inserted';
})()
