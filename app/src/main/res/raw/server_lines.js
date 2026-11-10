// Facebook's mobile site lays text out on its server: a text block is `white-space: pre` with the
// line breaks already in its text, so a paragraph comes as lines as wide as the screen it was laid
// out for. window.__astryxServerLines.join(block) gives each of the block's text nodes with the
// server's breaks turned back into spaces (canvas-measured: a break where the line plus the next
// word wouldn't have fit); the breaks the author typed (before an empty line, after a short line,
// after a space) stay. Nothing on the page is changed. text(block) is the whole block's text so
// joined. Used by text_wrap.js and copy_post_text.js. ES5-style, like the PiP scripts.
(function () {
  if (window.__astryxServerLines) return;
  var canvas = null;

  function textWidth(text) {
    return text ? canvas.measureText(text).width : 0;
  }

  // The widest a line in the block could have been when Facebook's server broke the lines:
  // the block's width, or for a block sized to its text (a comment bubble) the box around it
  // less the bubble's margin, taken as the same on both sides.
  function lineLimit(block) {
    var r = block.getBoundingClientRect();
    var fromRight = getComputedStyle(block.parentElement).direction === 'rtl';
    var limit = r.width;
    var el = block.parentElement;
    for (var i = 0; el && el !== document.body && i < 10; i++, el = el.parentElement) {
      var a = el.getBoundingClientRect();
      var inset = fromRight ? a.right - r.right : r.left - a.left;
      if (inset >= 0) limit = Math.max(limit, a.width - 2 * inset);
      if (a.width >= window.innerWidth - 1) break;
    }
    return limit;
  }

  // [{node, text}] for each of the block's text nodes, text being the node's text joined.
  function join(block) {
    var nodes = [];
    var walker = document.createTreeWalker(block, NodeFilter.SHOW_TEXT, null, false);
    var node;
    while ((node = walker.nextNode())) nodes.push(node);
    var joined = [];
    if (block.textContent.indexOf('\n') < 0) {
      for (var n = 0; n < nodes.length; n++) joined.push({ node: nodes[n], text: nodes[n].nodeValue });
      return joined;
    }
    if (!canvas) canvas = document.createElement('canvas').getContext('2d');
    var limit = lineLimit(block);
    var line = 0;      // width of the current line so far
    var tail = '';     // its last character
    for (var i = 0; i < nodes.length; i++) {
      var style = getComputedStyle(nodes[i].parentElement);
      canvas.font = style.font || (style.fontStyle + ' ' + style.fontWeight + ' ' + style.fontSize + ' ' + style.fontFamily);
      var parts = nodes[i].nodeValue.split('\n');
      var text = parts[0];
      line += textWidth(parts[0]);
      if (parts[0]) tail = parts[0].charAt(parts[0].length - 1);
      for (var j = 1; j < parts.length; j++) {
        var after = parts[j];
        for (var k = i + 1; j === parts.length - 1 && !/\S/.test(after) && k < nodes.length; k++) {
          after += nodes[k].nodeValue;
        }
        var word = (after.match(/^[^\s]+/) || [''])[0];
        var soft = word && !/\s/.test(tail) && line + textWidth(' ' + word) > limit + 1;
        text += (soft ? ' ' : '\n') + parts[j];
        line = textWidth(parts[j]);
        tail = parts[j] ? parts[j].charAt(parts[j].length - 1) : soft ? tail : '';
      }
      joined.push({ node: nodes[i], text: text });
    }
    return joined;
  }

  window.__astryxServerLines = {
    join: join,
    text: function (block) {
      return join(block).map(function (part) { return part.text; }).join('');
    }
  };
})();
