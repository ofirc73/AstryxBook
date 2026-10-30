// Runs at document start in the main view, before Facebook's own scripts.
//
// m.facebook.com asks for a list's next posts when its loading row (the list's last row, a
// grey placeholder with data-on-first-visibility-action-id) comes on screen: Facebook watches
// it with an IntersectionObserver that has no margin, so a fast scroll reaches the placeholder
// and waits there (read on the phone 2026-10-30: about 28 times in 90 s of flinging, mostly
// 0.5-1 s, up to 7 s). With posts hidden (Remove ads, Hide suggested posts) each batch is
// shorter on screen, so it comes up even more often. This tells Facebook the loading row is
// visible while it's still LEAD below the window, so the next posts are on their way first.
// Only rows of a list (data-type="vscroller"): other first-visibility marks log what the user
// saw and keep their own timing. ES5, like the other document-start scripts.
(function () {
  var Native = window.IntersectionObserver;
  if (!Native || window.__astryxFeedPrefetch) return;
  window.__astryxFeedPrefetch = true;

  var LEAD = '0px 0px 300% 0px'; // three windows below the screen

  function isLoadingRow(target) {
    return !!(target && target.hasAttribute && target.hasAttribute('data-on-first-visibility-action-id') &&
      target.parentElement && target.parentElement.getAttribute('data-type') === 'vscroller');
  }

  function Wrapped(callback, options) {
    var observer = new Native(callback, options);
    var ahead = null; // the same callback, LEAD ahead, for loading rows only
    var observe = observer.observe;
    var unobserve = observer.unobserve;
    var disconnect = observer.disconnect;
    // Facebook's own margin or root, if it ever sets one, is left alone.
    var plain = !(options && (options.root || options.rootMargin));
    observer.observe = function (target) {
      if (plain && target && target.hasAttribute && target.hasAttribute('data-on-first-visibility-action-id')) {
        ahead = ahead || new Native(function (entries) {
          var near = [];
          for (var i = 0; i < entries.length; i++) {
            if (entries[i].isIntersecting && isLoadingRow(entries[i].target)) near.push(entries[i]);
          }
          if (!near.length) return;
          // For the live check: how often, and how far below the window the row last was.
          window.__astryxFeedPrefetched = {
            count: ((window.__astryxFeedPrefetched || {}).count || 0) + 1,
            below: Math.round(near[0].boundingClientRect.top - window.innerHeight)
          };
          callback.call(observer, near, observer);
        }, { rootMargin: LEAD });
        ahead.observe(target);
      }
      return observe.apply(observer, arguments);
    };
    // Facebook unobserves the row once it has asked for more: the early watch stops with it.
    observer.unobserve = function (target) {
      if (ahead) ahead.unobserve(target);
      return unobserve.apply(observer, arguments);
    };
    observer.disconnect = function () {
      if (ahead) ahead.disconnect();
      return disconnect.apply(observer, arguments);
    };
    return observer;
  }
  Wrapped.prototype = Native.prototype;
  window.IntersectionObserver = Wrapped;
})();
