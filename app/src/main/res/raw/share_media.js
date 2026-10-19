// Shares photos and videos from another app into Facebook's own composer (ShareMedia.kt).
// Run by the app, with window.__astryxShareTarget set: 'feed' on m.facebook.com's Home, 'profile'
// on the user's mobile profile, 'group' on the mobile groups (the user picks one), 'story' on the
// desktop site's story creator (www.facebook.com/stories/create) and 'reel' on its reels page
// (both in the Messages layer). The user writes the caption and posts (or saves the profile
// picture); nothing here posts anything.
//
// The files come from the app through the AstryxShare message listener (ShareMediaBridge.kt):
// a header (JSON) and then each file's bytes (an ArrayBuffer, or base64 on WebViews without
// binary messages). Facebook's pages take files through a hidden <input type="file">:
// - Feed: the Photo button sets the input up and clicks it, which would open Android's picker.
//   The click is intercepted and the input gets the shared photos instead. The button is found
//   by its icon (a font glyph, the same in every language), or its English label.
//   A group's composer works the same way, once the user has opened the group.
// - Profile picture: the profile picture (a large square button) opens a menu whose Upload photo
//   (found by its icon) clicks the input; that click gets the shared photo, and Facebook shows its
//   preview with Update.
// - Story: the creator's input is ready when the page shows; it gets one file at a time. When
//   the page is back to choosing a story (after Share to story or Discard), the next one comes.
//   (story_layout.js lays that page out like the Facebook app's story editor.)
// - Reel: the reels page's Create reel (found by its icon) opens the creator, whose video input
//   gets the shared video.
// ES5 on purpose, like the PiP scripts.
(function () {
  var target = window.__astryxShareTarget;
  var bridge = window.AstryxShare;
  if (!target || !bridge || window.__astryxShareRunning) return;
  window.__astryxShareRunning = true;

  var PHOTO_ICON = String.fromCharCode(0xDB8C, 0xDE3C); // U+F323C, the Photo button's icon
  var UPLOAD_ICON = String.fromCharCode(0xDB85, 0xDEE6); // U+F16E6, the profile menu's Upload photo
  // The composer's own "add a photo" buttons: "Add more photos" (U+F18BB, feed) and "Add or remove
  // photos" (U+F20C5, a group).
  var ADD_PHOTO_ICONS = [String.fromCharCode(0xDB86, 0xDCBB), String.fromCharCode(0xDB88, 0xDCC5)];
  var ADD_STEP_MS = 1500;
  // The start of the path of the desktop Create reel button's icon (a clapperboard).
  var REEL_ICON_PATH = 'M5.79 1.016';
  var GROUP_PICK_MS = 10 * 60 * 1000; // the app gives up a group share after that too
  var FIND_MS = window.__astryxShareFindMs || 20000; // tests shorten it
  var TAKE_MS = 5000;
  var STORY_MIN_WIDTH = 600;
  var STORY_MAX_WIDTH = 1200;

  var inbox = [];
  var waiting = null;
  bridge.onmessage = function (event) {
    inbox.push(event.data);
    flush();
  };
  function flush() {
    if (waiting && inbox.length) {
      var callback = waiting;
      waiting = null;
      callback(inbox.shift());
    }
  }
  function receive(callback) {
    waiting = callback;
    flush();
  }
  function send(message) {
    message.target = target;
    bridge.postMessage(JSON.stringify(message));
  }

  function toBlob(data, type, base64) {
    if (!base64) return new Blob([data], { type: type });
    var binary = atob(data);
    var bytes = new Uint8Array(binary.length);
    for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    return new Blob([bytes], { type: type });
  }

  // Asks the app for files ('all' or 'next'); calls back with the File objects and the header.
  function receiveFiles(call, done) {
    send({ call: call });
    receive(function (text) {
      var header;
      try { header = JSON.parse(text); } catch (e) { done([], {}); return; }
      if (header.done) { done([], header); return; }
      var files = [];
      var index = 0;
      (function nextFile() {
        if (index >= header.count) { done(files, header); return; }
        receive(function (data) {
          var meta = header.files[index++];
          var blob = toBlob(data, meta.type, header.base64);
          if (blob.size > 0) files.push(new File([blob], meta.name, { type: meta.type, lastModified: Date.now() }));
          nextFile();
        });
      })();
    });
  }

  // A note over the composer while Facebook prepares the shared photos (it resizes each one and
  // draws its preview, a few seconds for a phone's full-size photo), until their previews are
  // all there or ADDING_MS. The previews are data: images, so this works in any language. Its
  // text comes from the app (window.__astryxShareAdding).
  var ADDING_MS = 30000;
  var ADDING_ID = 'astryx-share-adding';
  function previewCount() {
    return document.querySelectorAll('img[src^="data:image"]').length;
  }
  function showAdding(count, before) {
    var text = window.__astryxShareAdding;
    if (!text || document.getElementById(ADDING_ID)) return;
    var note = document.createElement('div');
    note.id = ADDING_ID;
    note.setAttribute('role', 'status');
    note.textContent = text;
    note.style.cssText = 'position:fixed;left:50%;bottom:24px;transform:translateX(-50%);z-index:2147483647;' +
      'max-width:calc(100% - 32px);box-sizing:border-box;padding:10px 18px;border-radius:20px;' +
      'background:rgba(32,33,36,0.92);color:#fff;font:500 14px/20px system-ui,sans-serif;' +
      'text-align:center;box-shadow:0 2px 8px rgba(0,0,0,0.3);pointer-events:none;';
    document.body.appendChild(note);
    var started = Date.now();
    (function check() {
      if (previewCount() - before >= count || Date.now() - started > ADDING_MS) {
        if (note.parentNode) note.parentNode.removeChild(note);
        return;
      }
      setTimeout(check, 250);
    })();
  }

  function giveFiles(input, files) {
    var transfer = new DataTransfer();
    for (var i = 0; i < files.length; i++) transfer.items.add(files[i]);
    input.files = transfer.files;
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // Calls back with what find() returns once it returns something, or null after ms.
  function waitFor(find, ms, callback) {
    var started = Date.now();
    (function check() {
      var found = find();
      if (found) { callback(found); return; }
      if (ms && Date.now() - started > ms) { callback(null); return; }
      setTimeout(check, 200);
    })();
  }

  // The composer's Photo button: a button (Home) or a bare action container (a group's page, no
  // button role), whose text starts with the Photo icon. The innermost one: a container holding
  // the whole composer starts with that icon only when the button is its first content.
  function photoButton() {
    var buttons = document.querySelectorAll('[role="button"], [data-action-id]');
    var found = null;
    for (var i = 0; i < buttons.length; i++) {
      var text = (buttons[i].textContent || '').replace(/^\s+/, '');
      if (text.indexOf(PHOTO_ICON) === 0 || buttons[i].getAttribute('aria-label') === 'Photo') {
        if (!found || found.contains(buttons[i])) found = buttons[i];
      }
    }
    return found;
  }

  // Presses button, whose page then clicks a hidden file input (which would open Android's
  // picker): that click gets the files instead. Reports whether the page took them.
  // A single-file input (a group's composer) gets the first file; the composer that opens then
  // takes the others one at a time through its own add-photo button (it keeps one file per
  // change, even once its input allows several).
  function answerFileClick(button, files) {
    var rest = [];
    var before = previewCount();
    var taken = whenFileClicked(function (input) {
      if (input.multiple) {
        giveFiles(input, files);
      } else {
        giveFiles(input, files.slice(0, 1));
        rest = files.slice(1);
      }
    });
    button.click();
    // The composer opens with the first photo given (before TAKE_MS): the note shows from then.
    waitFor(taken, TAKE_MS, function (given) { if (given) showAdding(files.length, before); });
    setTimeout(function () {
      if (!taken()) { send({ call: 'failed', reason: 'notTaken' }); return; }
      addOneByOne(rest);
    }, TAKE_MS);
  }

  // Answers the page's next click on a file input (which would open Android's picker) with
  // answer(input); returns a function telling whether that happened. Gives up after TAKE_MS.
  function whenFileClicked(answer) {
    var nativeClick = HTMLInputElement.prototype.click;
    var taken = false;
    HTMLInputElement.prototype.click = function () {
      if (this.type !== 'file') return nativeClick.apply(this, arguments);
      HTMLInputElement.prototype.click = nativeClick;
      taken = true;
      answer(this);
    };
    setTimeout(function () {
      if (HTMLInputElement.prototype.click !== nativeClick && !taken) HTMLInputElement.prototype.click = nativeClick;
    }, TAKE_MS);
    return function () { return taken; };
  }

  function addPhotoButton() {
    var buttons = document.querySelectorAll('[role="button"], [data-action-id]');
    var found = null;
    for (var i = 0; i < buttons.length; i++) {
      var text = (buttons[i].textContent || '').replace(/^\s+/, '');
      var byIcon = ADD_PHOTO_ICONS[0] === text.slice(0, 2) || ADD_PHOTO_ICONS[1] === text.slice(0, 2);
      if (byIcon || /^(Add more photos|Add or remove photos)$/.test((buttons[i].innerText || '').replace(/^\s+|\s+$/g, ''))) {
        if (!found || found.contains(buttons[i])) found = buttons[i];
      }
    }
    return found;
  }

  function addOneByOne(files) {
    if (!files.length) { send({ call: 'finished' }); return; }
    waitFor(addPhotoButton, TAKE_MS, function (button) {
      if (!button) { send({ call: 'failed', reason: 'notTaken' }); return; }
      var taken = whenFileClicked(function (input) { giveFiles(input, files.slice(0, 1)); });
      button.click();
      setTimeout(function () {
        if (!taken()) { send({ call: 'failed', reason: 'notTaken' }); return; }
        addOneByOne(files.slice(1));
      }, ADD_STEP_MS);
    });
  }

  // The feed share opens the composer itself (FEED_COMPOSER_PAGE_URL): its Photos button, found
  // by the add-photo icon. Home's Photo button still works where the composer isn't open.
  function feedPhotoButton() {
    if (/^\/composer\/?$/.test(window.location.pathname)) return addPhotoButton();
    return photoButton();
  }

  function shareToFeed() {
    waitFor(feedPhotoButton, FIND_MS, function (button) {
      if (!button) { send({ call: 'failed', reason: 'noComposer' }); return; }
      receiveFiles('all', function (files) {
        if (!files.length) { send({ call: 'finished' }); return; }
        answerFileClick(button, files);
      });
    });
  }

  // A group's own page (not the groups list or one of its tabs) with its composer's Photo button.
  function groupPhotoButton() {
    var path = window.location.pathname;
    if (!/^\/groups\/[^\/]+\/?$/.test(path)) return null;
    if (/^\/groups\/(feed|discover|joins|create|notifications)\/?$/.test(path)) return null;
    return photoButton();
  }

  // The user picks one of their groups; its page may open in-page or as a new load (where the
  // app runs this again).
  function shareToGroup() {
    waitFor(groupPhotoButton, GROUP_PICK_MS, function (button) {
      if (!button) return;
      receiveFiles('all', function (files) {
        if (!files.length) { send({ call: 'finished' }); return; }
        answerFileClick(button, files);
      });
    });
  }

  // The profile picture: on the user's own profile, the large square button at the top.
  function profilePictureButton() {
    var labelled = document.querySelector('[role="button"][aria-label="Edit profile photo"]');
    if (labelled) return labelled;
    var buttons = document.querySelectorAll('[role="button"]');
    for (var i = 0; i < buttons.length; i++) {
      var box = buttons[i].getBoundingClientRect();
      if (box.width >= 100 && Math.abs(box.width - box.height) < 4 && buttons[i].querySelector('img')) return buttons[i];
    }
    return null;
  }

  function uploadPhotoItem() {
    var buttons = document.querySelectorAll('[role="button"]');
    for (var i = 0; i < buttons.length; i++) {
      var text = (buttons[i].textContent || '').replace(/^\s+/, '');
      if (text.indexOf(UPLOAD_ICON) === 0 || text === 'Upload photo') return buttons[i];
    }
    return null;
  }

  function shareToProfilePicture() {
    waitFor(profilePictureButton, FIND_MS, function (picture) {
      if (!picture) { send({ call: 'failed', reason: 'noComposer' }); return; }
      picture.click();
      waitFor(uploadPhotoItem, TAKE_MS, function (upload) {
        if (!upload) { send({ call: 'failed', reason: 'noComposer' }); return; }
        receiveFiles('next', function (files) {
          if (!files.length) { send({ call: 'finished' }); return; }
          answerFileClick(upload, files);
        });
      });
    });
  }

  function createReelButton() {
    var buttons = document.querySelectorAll('[role="button"], a');
    for (var i = 0; i < buttons.length; i++) {
      var path = buttons[i].querySelector('svg path');
      var d = path ? path.getAttribute('d') || '' : '';
      if (d.indexOf(REEL_ICON_PATH) === 0 || (buttons[i].textContent || '').replace(/^\s+|\s+$/g, '') === 'Create reel') {
        return buttons[i];
      }
    }
    return null;
  }

  function reelInput() {
    var inputs = document.querySelectorAll('input[type="file"]');
    for (var i = 0; i < inputs.length; i++) {
      if ((inputs[i].accept || '').indexOf('video') !== -1 && !inputs[i].__astryxShared) return inputs[i];
    }
    return null;
  }

  function shareToReel() {
    fitStoryPage();
    waitFor(createReelButton, FIND_MS, function (button) {
      if (!button) { send({ call: 'failed', reason: 'noComposer' }); return; }
      button.click();
      waitFor(reelInput, TAKE_MS, function (input) {
        if (!input) { send({ call: 'failed', reason: 'notTaken' }); return; }
        receiveFiles('next', function (files) {
          if (!files.length) { send({ call: 'finished' }); return; }
          // The creator has several video inputs and only one of them is wired to it (nothing
          // tells which). Facebook clears (or removes) the input it reads; one that ignores the
          // video keeps it. Try them in turn until one takes it.
          (function tryNext() {
            var next = reelInput();
            if (!next) { send({ call: 'failed', reason: 'notTaken' }); return; }
            next.__astryxShared = true;
            giveFiles(next, files.slice(0, 1));
            setTimeout(function () {
              if (!document.contains(next) || !next.files || !next.files.length) send({ call: 'finished' });
              else tryNext();
            }, 1500);
          })();
        });
      });
    });
  }

  // The story creator is choosing a story: its file input is there and hasn't got ours.
  function readyStoryInput() {
    if (window.location.pathname.indexOf('/stories/create') !== 0) return null;
    var input = document.querySelector('input[type="file"]');
    if (!input || input.__astryxShared) return null;
    if (input.files && input.files.length) return null;
    return input;
  }

  // The desktop story creator is wider than a phone (its preview sits right of the controls) and
  // its viewport says device-width: lay it out at its own width and fit that to the screen. The
  // layer allows a wide viewport on this page. Kept up when another script (pinch_to_zoom.js)
  // rewrites the viewport.
  function fitStoryPage() {
    var width = 0;
    var content = '';
    function apply() {
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
    // The page grows when a story opens in the editor (its preview): follow it, widening only.
    function fit() {
      var wanted = Math.min(STORY_MAX_WIDTH, Math.max(STORY_MIN_WIDTH, document.documentElement.scrollWidth));
      if (wanted <= width) return;
      width = wanted;
      var scale = Math.min(1, (window.outerWidth || window.innerWidth) / width).toFixed(3);
      content = 'width=' + width + ', initial-scale=' + scale + ', minimum-scale=' + scale + ', user-scalable=yes';
      apply();
    }
    fit();
    new MutationObserver(apply).observe(document.head || document.documentElement,
      { childList: true, subtree: true, attributes: true, attributeFilter: ['content'] });
    setInterval(fit, 500);
  }

  function shareToStory(first) {
    // The first file waits for the page to load; the next ones for the user to finish a story.
    // The first file waits for the creator (a cold start can take a while), up to 45 s.
    waitFor(readyStoryInput, first ? Math.max(FIND_MS, window.__astryxShareFindMs ? 0 : 45000) : 0, function (input) {
      if (!input) { send({ call: 'failed', reason: 'noComposer' }); return; }
      receiveFiles('next', function (files, header) {
        if (!files.length) { send({ call: 'finished' }); return; }
        input.__astryxShared = true;
        giveFiles(input, files.slice(0, 1));
        if (header.remaining > 0) shareToStory(false);
        else {
          send({ call: 'finished' });
          closeWhenDone();
        }
      });
    });
  }

  // After the last shared item: once the creator is back to choosing a story (the user discarded
  // it), the share is over, so the story screen closes, as the Facebook app does. Facebook's Home
  // closes the layer (it's one of the sections that belong in the main view). Waits for the
  // chooser's fresh file input to hold for a moment: after Share to story the page uploads and
  // moves on by itself, and nothing here may interrupt that.
  function closeWhenDone() {
    var seen = 0;
    var timer = setInterval(function () {
      seen = readyStoryInput() ? seen + 1 : 0;
      if (seen < 3) return;
      clearInterval(timer);
      if (window.__astryxShareClose) window.__astryxShareClose(); // tests
      else window.location.assign('https://www.facebook.com/');
    }, 500);
  }

  if (target === 'feed') shareToFeed();
  else if (target === 'group') shareToGroup();
  else if (target === 'profile') shareToProfilePicture();
  else if (target === 'story') shareToStory(true);
  else if (target === 'reel') shareToReel();
})();
