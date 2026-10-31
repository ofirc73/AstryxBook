// Runs at document start in the main view, before Facebook's own scripts.
//
// m.facebook.com asks for a list's next posts when its loading row (the list's last row, a
// grey placeholder with data-on-first-visibility-action-id) comes on screen: Facebook watches
// it with an IntersectionObserver that has no margin, so a fast scroll reaches the placeholder
// and waits there (read on the phone 2026-10-30: about 28 times in 90 s of flinging, mostly
// 0.5-1 s, up to 7 s). With posts hidden (Remove ads, Hide suggested posts) each batch is
// shorter on screen, so it comes up even more often. This tells Facebook the loading row is
// visible while it's still LEAD below the window, so the next posts are on their way first.
// LEAD was three windows in v1.38.1, which made no visible difference on the phone (2026-10-31,
// nothing hidden): a batch of 7 posts is 3.5-6 windows tall and the server answers each ask in
// 2-7 s, one batch at a time, while a fast fling covers 3+ windows a second, so it reached the
// placeholder under a second after the early ask. Ten windows (v1.38.2) still left waits: read in
// the app (2026-11-01), each came 7-25 windows into a long scroll, after a pause that left only
// 3-14 windows loaded, and the server sat idle (answered, nothing asked) for about 25 s of 160.
// Thirty windows keeps it loading through those pauses, so the next scroll starts with more.
// About once a session an early ask brings nothing until the row reaches the screen (35 s at the
// start of a session, read on the phone 2026-11-01; answered ones come in 0.7-1.4 s, slow ones up
// to 8 s), so a list that hasn't grown RETRY_MS after an ask is asked once more. Facebook takes a
// repeated ask for the same row without sending its posts twice (11 repeats, no duplicate post).
// Only rows of a list (data-type="vscroller"): other first-visibility marks log what the user
// saw and keep their own timing. ES5, like the other document-start scripts.
(function () {
  var Native = window.IntersectionObserver;
  if (!Native || window.__astryxFeedPrefetch) return;
  window.__astryxFeedPrefetch = true;

  var LEAD = '0px 0px 3000% 0px'; // thirty windows below the screen
  var RETRY_MS = 9000; // longer than the slowest answer seen

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
    var retry = null; // the timer for the last early ask
    var retriedAt = -1; // the list's length when it was last asked again
    // Asks again with the same entries if the list hasn't grown when RETRY_MS is up, once per
    // length: at the feed's end ("No more posts to fetch") the last row stays a loading row.
    function retryLater(entries) {
      var row = entries[0].target;
      var list = row.parentElement;
      var rows = list.children.length;
      clearTimeout(retry);
      if (rows === retriedAt) return;
      retry = setTimeout(function () {
        retry = null;
        if (!row.isConnected || list.children.length !== rows) return;
        retriedAt = rows;
        window.__astryxFeedPrefetched.retries = (window.__astryxFeedPrefetched.retries || 0) + 1;
        callback.call(observer, entries, observer);
      }, RETRY_MS);
    }
    observer.observe = function (target) {
      if (plain && target && target.hasAttribute && target.hasAttribute('data-on-first-visibility-action-id')) {
        ahead = ahead || new Native(function (entries) {
          var near = [];
          for (var i = 0; i < entries.length; i++) {
            if (entries[i].isIntersecting && isLoadingRow(entries[i].target)) near.push(entries[i]);
          }
          if (!near.length) return;
          // For the live check: how often, and how far below the window the row last was (and,
          // for the tests, how many asks were made again).
          var was = window.__astryxFeedPrefetched || {};
          window.__astryxFeedPrefetched = {
            count: (was.count || 0) + 1,
            below: Math.round(near[0].boundingClientRect.top - window.innerHeight),
            retries: was.retries || 0
          };
          callback.call(observer, near, observer);
          retryLater(near);
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
      clearTimeout(retry);
      if (ahead) ahead.disconnect();
      return disconnect.apply(observer, arguments);
    };
    return observer;
  }
  Wrapped.prototype = Native.prototype;
  window.IntersectionObserver = Wrapped;
})();
