// Runs at document start (before the page's own scripts), on every page.
//
// Facebook's desktop video player pauses any video whose player box is less than 50% inside
// the viewport (rule "evaluateVideoAutoplayPauseOnInvisibleRule:50%", fed by an
// IntersectionObserver on the player). It runs before the "user pressed play" rule, so it
// wins over every play. In a PiP window the desktop page is laid out at the window's size
// (about 130x227 CSS px), smaller than the player box, so the reel is never 50% visible and
// gets paused on every play. The mobile site has no such rule.
//
// While PiP is active (window.__astryxPipActive, set by pipKeepPlayingActivateJs), report
// the PiP video's player as fully visible to the page's IntersectionObservers.
// window.__astryxPipVisibleReplay() re-delivers that to observers that already got a
// "not visible" entry for it; called on PiP activation. After PiP the real entries come
// back on their own (the ratio crosses thresholds again as the window grows).
// ES5 on purpose: PiP scripts must parse on old WebViews too.
(function () {
  var Native = window.IntersectionObserver;
  if (!Native || window.__astryxIoWrapped) return;
  window.__astryxIoWrapped = true;

  function pipVideo() {
    if (!window.__astryxPipActive || !window.__astryxPipVideo) return null;
    return window.__astryxPipVideo();
  }

  function holdsPipVideo(target, video) {
    return !!(video && target && (target === video || (target.contains && target.contains(video))));
  }

  function fullyVisible(target, rootBounds) {
    var rect = target.getBoundingClientRect();
    return {
      target: target,
      time: window.performance ? window.performance.now() : Date.now(),
      rootBounds: rootBounds || null,
      boundingClientRect: rect,
      intersectionRect: rect,
      intersectionRatio: 1,
      isIntersecting: true,
      isVisible: true
    };
  }

  // Observations are kept on the observed element itself (only the PiP video and its
  // ancestors are ever looked up), so nothing outlives the elements.
  function forget(target, observer) {
    var list = target.__astryxIo;
    if (!list) return;
    for (var i = list.length - 1; i >= 0; i--) {
      if (list[i][0] === observer) list.splice(i, 1);
    }
  }

  function Wrapped(callback, options) {
    var targets = [];
    var observer = new Native(function (entries, obs) {
      var video = pipVideo();
      if (video) {
        var out = [];
        for (var i = 0; i < entries.length; i++) {
          var e = entries[i];
          out.push(holdsPipVideo(e.target, video) ? fullyVisible(e.target, e.rootBounds) : e);
        }
        entries = out;
      }
      return callback.call(this, entries, obs);
    }, options);
    var observe = observer.observe;
    var unobserve = observer.unobserve;
    var disconnect = observer.disconnect;
    observer.observe = function (target) {
      if (target && typeof target === 'object') {
        forget(target, observer);
        (target.__astryxIo = target.__astryxIo || []).push([observer, callback]);
        targets.push(target);
      }
      return observe.apply(observer, arguments);
    };
    observer.unobserve = function (target) {
      if (target && typeof target === 'object') {
        forget(target, observer);
        var i = targets.indexOf(target);
        if (i >= 0) targets.splice(i, 1);
      }
      return unobserve.apply(observer, arguments);
    };
    observer.disconnect = function () {
      for (var i = 0; i < targets.length; i++) forget(targets[i], observer);
      targets = [];
      return disconnect.apply(observer, arguments);
    };
    return observer;
  }
  Wrapped.prototype = Native.prototype;
  window.IntersectionObserver = Wrapped;

  window.__astryxPipVisibleReplay = function () {
    var video = pipVideo();
    var count = 0;
    for (var el = video; el; el = el.parentElement) {
      var list = el.__astryxIo;
      if (!list) continue;
      for (var i = 0; i < list.length; i++) {
        try {
          list[i][1].call(list[i][0], [fullyVisible(el, null)], list[i][0]);
          count++;
        } catch (err) {}
      }
    }
    return count;
  };
})();
