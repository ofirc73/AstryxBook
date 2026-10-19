
// Desktop mode identifier
(() => {
    window.isDesktopMode = () => {
        return document.querySelector('html[id="facebook"]') !== null;
    }
})();

// Feed identifier
(() => {
    window.isFeed = () => {
        const isHomeUrl = window.location.pathname === '/' &&
            (window.location.hostname === 'm.facebook.com' || window.location.hostname === 'www.facebook.com');

        if (window.isDesktopMode()) return isHomeUrl;

        return isHomeUrl && window.hasHomeTabs();
    };

    // Home's tab bar, the one sign of the mobile feed at "/".
    window.hasHomeTabs = () => Array.from(document.querySelectorAll('[role="button"] span'))
        .some(span => span.textContent === '󱥆');

    // The Feeds that "Start on Feeds" opened (open_feeds.js): also at "/", without the tab bar.
    window.isStartFeeds = () => !!window.__astryxFeeds && !window.isDesktopMode() &&
        window.location.pathname === '/' && !window.hasHomeTabs();
})();





(function() {
    if (!window.isDesktopMode()) return;

    document.documentElement.style.fontSize = '18px';


    // Do not stick the navbar by default
    (() => {
      const waitForBanner = () => new Promise(resolve => {
        const existing = document.querySelector('div[role="banner"]');
        if (existing) return resolve(existing);

        new MutationObserver((mutations, obs) => {
          for (const { addedNodes } of mutations) {
            for (const node of addedNodes) {
              if (node.nodeType === 1 && node.matches('div[role="banner"]')) {
                obs.disconnect();
                return resolve(node);
              }
            }
          }
        }).observe(document.body, { childList: true, subtree: true });
      });

      const forceAbsolute = el => {
        if (el?.classList.contains('xixxii4')) {
          el.style.setProperty('position', 'absolute', 'important');
        }
      };

      waitForBanner().then(banner => {
        const style = document.createElement('style');
        style.textContent = `
          div[role="banner"].xixxii4,
          div[role="banner"] .xixxii4 {
            position: absolute !important;
          }
        `;
        document.head.appendChild(style);

        forceAbsolute(banner);
        banner.querySelectorAll('.xixxii4').forEach(forceAbsolute);

        new MutationObserver(mutations => {
          for (const m of mutations) {
            if (m.type === 'childList') {
              m.addedNodes.forEach(n => {
                forceAbsolute(n);
                n.querySelectorAll?.('.xixxii4')?.forEach(forceAbsolute);
              });
            } else if (m.type === 'attributes' && m.attributeName === 'class') {
              forceAbsolute(m.target);
            }
          }
        }).observe(banner, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
      });
    })();


    // Remove "send" button to save space
    // Remove the third element in the interaction bar if nb is 4
    (function() {
      const parentSelector = '.xbmvrgn.x1diwwjn';
      const childSelector = '.x10b6aqq.x1yrsyyn.xs83m0k';

      function checkAndRemoveThird(parent) {
        const children = parent.querySelectorAll(childSelector);
        if (children.length === 4) children[2].remove();
      }

      document.querySelectorAll(parentSelector).forEach(checkAndRemoveThird);

      const observer = new MutationObserver(mutations => {
        for (const mutation of mutations) {
          mutation.addedNodes.forEach(node => {
            if (node.nodeType === 1) {
              if (node.matches(parentSelector)) {
                checkAndRemoveThird(node);
              }
              node.querySelectorAll(parentSelector).forEach(checkAndRemoveThird);
            }
          });
        }
      });

      observer.observe(document.body, { childList: true, subtree: true });
    })();
})();


// Scroll to top on back-press at feed
(() => {
    window.backHandlerNB = () => {

        const dialogs = document.querySelectorAll('div[role="dialog"]');
        const isMenu = document.querySelector('div[role="menu"]')

        function scrollToTop() {
            if (window.scrollY !== 0) {
              // to interrupt any current scroll event.
              document.body.style.overflow = 'hidden';
              setTimeout(() => {
                 document.body.style.overflow = '';
                 window.scrollTo({ top: 0, behavior: 'smooth' });
              }, 30);
              return "scrolling";
           } else return "exit";
        }

        // Messenger chat windows on the desktop site aren't role="dialog": each is a
        // fixed-position box holding a message composer, so the checks below would
        // see nothing open and Back would leave the page (or close the app). Close
        // the last one instead: the right-most button of its header row is "Close
        // chat" in every language. Messages scrolled up under the header have
        // buttons there too, so only buttons that are actually on top count.
        function closeChatWindow() {
            const windows = [];
            document.querySelectorAll('[contenteditable="true"]').forEach((editor) => {
                let box = editor.parentElement;
                while (box && box !== document.body && getComputedStyle(box).position !== 'fixed')
                    box = box.parentElement;
                if (box && box !== document.body && !box.closest('[role="dialog"]') &&
                        box.getBoundingClientRect().width > 0 && !windows.includes(box))
                    windows.push(box);
            });
            const chat = windows[windows.length - 1];
            if (!chat) return false;
            const top = chat.getBoundingClientRect().top;
            let close = null, closeX = -Infinity;
            chat.querySelectorAll('[role="button"]').forEach((button) => {
                const rect = button.getBoundingClientRect();
                if (rect.width === 0 || rect.top < top || rect.top >= top + 56 || rect.left <= closeX)
                    return;
                const hit = document.elementFromPoint(rect.left + rect.width / 2, rect.top + rect.height / 2);
                if (hit && button.contains(hit)) {
                    close = button;
                    closeX = rect.left;
                }
            });
            if (!close) return false;
            close.click();
            return true;
        }

        if (window.isDesktopMode()) {
            if (!isMenu && !dialogs.length && closeChatWindow())
                return "true";
            // The desktop feed used to keep one role="dialog" element around at
            // rest; it now has none, so "nothing open" means no dialog at all.
            if (window.isFeed() && !isMenu && !dialogs.length)
                return scrollToTop();
            else if (isMenu || dialogs.length > 1) {
                const escapeEvent = new KeyboardEvent('keydown', {
                    key: 'Escape',
                    code: 'Escape',
                    keyCode: 27,
                    which: 27,
                    bubbles: true,
                    cancelable: true
                });
                window.dispatchEvent(escapeEvent);
                return "true";
            } else return "false"
        } else if ((window.isFeed() || window.isStartFeeds()) && !isMenu && !dialogs.length) {
            return scrollToTop();
        } else return "false";
    }
})();

// Enable press and hold caption selection and apply custom selection color
(() => {
  const makeSelectable = (el) => {
    if (el.closest('div[role="button"]')) return;
    el.style.userSelect = 'text';
    el.style.pointerEvents = 'auto';
  };

  const updateText = () => {
    document.querySelectorAll('.native-text').forEach(makeSelectable);
  };

  const selectionStyle = document.createElement('style');
  selectionStyle.textContent = `
    .native-text::selection {
      background: #ccc;
      color: black;
    }
  `;
  document.head.appendChild(selectionStyle);

  updateText();

  new MutationObserver(updateText).observe(document.body, {
    childList: true,
    subtree: true
  });
})();

// Enhance Loading Overlay Script
(function() {
    function applyOverlayStyle() {
        const overlays = document.querySelectorAll('.loading-overlay');
        overlays.forEach(overlay => {
            overlay.style.backgroundColor = 'rgba(0, 0, 0, 0.1)';
        });
    }
    applyOverlayStyle();

    const observer = new MutationObserver((mutations) => {
        mutations.forEach((mutation) => {
            if (mutation.addedNodes.length)
                applyOverlayStyle();
        });
    });

    observer.observe(document.body, {
        childList: true,
        subtree: true
    });
})();

// Hide facebook download button and other distractions at login page
(function() {
  function removeDistr() {
    document.querySelector('div[data-bloks-name="bk.components.Flexbox"][style*="background: rgb(255, 255, 255)"].wbloks_1')?.parentElement?.remove();

    document.querySelectorAll(
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding-top: 20px; padding-bottom: 20px"],' +
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding-left: 4px; padding-right: 4px; padding-bottom: 4px"],' +
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding: 10px 12px; background: rgb(255, 255, 255)"],' +
      'div[data-bloks-name="bk.components.Flexbox"][style*="padding: 20px"]'
    )?.forEach(distr => distr.remove());
  }

  removeDistr();

  new MutationObserver(mutations => {
    for (const m of mutations) {
      if (m.type === 'childList' && m.addedNodes.length) {
        removeDistr();
        break;
      }
    }
  }).observe(document.body, {
    childList: true,
    subtree: true
  });
})();

// Make the loading bar's background transparent
(() => {
    const style = document.createElement('style');
    style.textContent = `
    .revamped-progress-bar-color .loading-bar-background { background: transparent; }
    .loading-bar-background { background-color: transparent; }
    `;
    document.head.appendChild(style);
})();

// Hide annoying bottom banners
// The "Open app" bar under a post's photos (opened in-page, so the URL stays "/"): a lone,
// full-width button holding only its label, in any language. Other bars on "/" (a comment box)
// have icons or a text field.
const isOpenAppBar = (element) => {
  const buttons = element.querySelectorAll('[role="button"]');
  if (buttons.length !== 1 || element.querySelector('img, svg, input, textarea, [contenteditable]')) return false;
  const button = buttons[0];
  return button.getBoundingClientRect().width >= element.getBoundingClientRect().width * 0.9
    && button.querySelectorAll('[data-mcomponent]').length === 1;
};

// Facebook's "Open app" buttons (the Reels bar's blue one, a story's): found by their icon (the same
// in every language). download_content.js hid them too, but only with Download on.
const OPEN_APP_ICON = String.fromCodePoint(0xF196C);
const hideOpenAppButtons = () => {
  document.querySelectorAll('div[role="button"]').forEach(button => {
    if (button.style.display === 'none') return;
    const icon = button.querySelector('div.fl.ac span');
    if (icon && icon.textContent.includes(OPEN_APP_ICON)) button.style.display = 'none';
  });
};

// Facebook's mobile Story viewer and feed send story creation to its app upsell sheet. Open the
// desktop creator in Astryxbook instead: the viewer's plus/photo glyph is shared across languages,
// while the feed's Create story card has a nested accessible button.
const ADD_STORY_ICON = String.fromCodePoint(0xF31CC);
// A story card in the tray at the top of the feed: a tall, narrow button.
const isStoryCardSized = (el) => {
  const r = el.getBoundingClientRect();
  return r.width >= 80 && r.width <= 160 && r.height >= 140 && r.height <= 260;
};
// The feed's Create story card, in any language: the first story-sized card on Home, in the
// stories tray right under the composer (its label is only text, "Create story" translated, and
// its "+" an image). The tray may hold no other card; Reels' cards further down never count.
const CREATE_STORY_MAX_TOP = 450;
const isCreateStoryCard = (card) => {
  if (card.getBoundingClientRect().top + window.scrollY > CREATE_STORY_MAX_TOP) return false;
  const first = Array.from(document.querySelectorAll('[role="button"], [data-action-id]')).find(isStoryCardSized);
  return first === card;
};
document.addEventListener('click', event => {
  const path = location.pathname;
  const inStoryViewer = /^\/stories\/[^/]+(?:\/|$)/.test(path) && !/^\/stories\/create\/?$/.test(path);
  const onFeed = path === '/' || path === '/home.php';
  const target = event.target instanceof Element ? event.target.closest('[role="button"]') : null;
  if (!target) return;
  // The feed's Create story card has a nested, accessibly labelled button (no add-story glyph).
  // Let it use the same in-app creator as Add story from a Story viewer instead of Facebook's
  // mobile-site sheet that asks the user to open the Facebook app.
  let card = target;
  while (card && !isStoryCardSized(card)) {
    card = card.parentElement ? card.parentElement.closest('[role="button"]') : null;
  }
  const createStoryOnFeed = onFeed && (target.getAttribute('aria-label') === 'Create story'
    || !!target.querySelector('[role="button"][aria-label="Create story"]')
    || (!!card && isCreateStoryCard(card)));
  if (!createStoryOnFeed && (!inStoryViewer || !target.hasAttribute('data-action-id'))) return;
  const hasAddStoryIcon = Array.from(target.querySelectorAll('span'))
    .some(span => span.textContent.includes(ADD_STORY_ICON));
  if (!createStoryOnFeed && !hasAddStoryIcon) return;
  if (!window.AstryxPage && !window.LayerBridge) return;

  event.preventDefault();
  event.stopImmediatePropagation();
  const url = 'https://www.facebook.com/stories/create';
  if (window.AstryxPage) {
    window.AstryxPage.postMessage(JSON.stringify({ call: 'layerUrl', url }));
  } else {
    window.LayerBridge.onUrlChanged(url);
  }
}, true);

// Facebook's "Facebook is better on the app" sheet (Get app / Not now), put over the feed when the
// app opens: pressed away with its dismiss button, as the user would. Its shape: a dialog screen
// whose bottom sheet holds a picture, one title and exactly two buttons, and nothing to type in.
// - Where its words are known, per language: that sheet for sure, even right after a tap. English
//   was read from a real sheet (title and button). For Hebrew only the button is known: Facebook's
//   usual Hebrew "Not now". A confirmation the user opened has no such button.
// - In other languages, the shape alone, and only a sheet that came up by itself, not just after a
//   tap: one the user opened (a confirmation such as "Delete post?") is left to them. Its second
//   button is the dismiss one.
// Add a language's exact title and dismiss label once they're read from a real sheet (a language
// without a title is matched by its dismiss label alone).
const APP_PROMPT_TEXTS = {
  en: { title: 'Facebook is better on the app', dismiss: 'Not now' },
  he: { dismiss: '\u05DC\u05D0 \u05E2\u05DB\u05E9\u05D9\u05D5' },
};
const APP_PROMPT_TAP_MS = 2000;
let lastTapAt = 0;
document.addEventListener('pointerdown', () => { lastTapAt = Date.now(); }, true);
const seenPromptSheets = new WeakSet();
const promptText = (element) => (element ? element.textContent : '').replace(/\s+/g, ' ').trim();
const knownPromptDismiss = (sheet, buttons) => {
  const title = promptText(sheet.querySelector('h1'));
  for (const texts of Object.values(APP_PROMPT_TEXTS)) {
    if (texts.title && title !== texts.title) continue;
    const dismiss = Array.from(buttons).find(button =>
      button.getAttribute('aria-label') === texts.dismiss || promptText(button) === texts.dismiss);
    if (dismiss) return dismiss;
  }
  return null;
};
const dismissAppPrompt = () => {
  const sheet = document.querySelector('.dialog-screen .bottom.fixed-container');
  if (!sheet || seenPromptSheets.has(sheet)) return;
  const buttons = sheet.querySelectorAll('[role="button"]');
  if (buttons.length !== 2 || sheet.querySelectorAll('h1').length !== 1 || !sheet.querySelector('img')
    || sheet.querySelector('input, textarea, [contenteditable]')) return;
  seenPromptSheets.add(sheet);
  const known = knownPromptDismiss(sheet, buttons);
  if (known) {
    known.click();
    return;
  }
  if (Date.now() - lastTapAt < APP_PROMPT_TAP_MS) return;
  buttons[1].click();
};

const observer = new MutationObserver(() => {
  hideOpenAppButtons();
  dismissAppPrompt();

  const element = document.querySelector('.bottom.fixed-container');
  if (location.pathname === '/'
  && document.querySelector('div[role="button"][aria-label*="Facebook"]') === null
  && !(element && isOpenAppBar(element))) return;

  if (
    element &&
    !element.hasAttribute('data-shift-on-keyboard-shown')
  ) {
    const heightAttr = element.getAttribute('data-actual-height');
    if (heightAttr && parseInt(heightAttr, 10) < 80) {
      element.style.display = 'none';
    }
  }
});

observer.observe(document.body, { childList: true, subtree: true });


// Hold Effect Script
(function() {
  const style = document.createElement('style');
  style.innerHTML = '* { -webkit-tap-highlight-color: rgba(180, 180, 180, 0.35); }';
  document.head.appendChild(style);
})();


/* The below scripts are specific to com.eepiemi.materialbook application. */

(() => {
  const onReady = (fn) => {
    if (document.readyState === 'loading')
      document.addEventListener('DOMContentLoaded', fn);
     else fn();
  };

  onReady(() => {
    const BUTTON_ID = 'custom-settings-btn';
    const ICON_SVG = `
        <svg width="28" height="28" viewBox="0 -960 960 960"  fill="%FILL%">
            <path d="m370-80-16-128q-13-5-24.5-12T307-235l-119 50L78-375l103-78q-1-7-1-13.5v-27q0-6.5 1-13.5L78-585l110-190 119 50q11-8 23-15t24-12l16-128h220l16 128q13 5 24.5 12t22.5 15l119-50 110 190-103 78q1 7 1 13.5v27q0 6.5-2 13.5l103 78-110 190-118-50q-11 8-23 15t-24 12L590-80H370Zm112-260q58 0 99-41t41-99q0-58-41-99t-99-41q-59 0-99.5 41T342-480q0 58 40.5 99t99.5 41Z"/>
        </svg>`;

    const getFillColor = () => {
      const color = document.querySelector('meta[name="theme-color"]')?.content?.toLowerCase();
      return color === '#ffffff' ? '#080809' : '#e4e6eb';
    };

    // The round grey backdrop of Facebook's own header buttons (Search, Menu), so the gear
    // reads as one of them.
    const getBackdropColor = () => {
      const color = document.querySelector('meta[name="theme-color"]')?.content?.toLowerCase();
      return color === '#ffffff' ? '#e2e5e9' : '#3a3b3c';
    };

    const updateButtonColor = () => {
      const svg = document.querySelector(`#${BUTTON_ID} svg`);
      if (svg) svg.setAttribute('fill', getFillColor());
      const btn = document.getElementById(BUTTON_ID);
      if (btn && btn.style.position === 'fixed') btn.style.background = getBackdropColor();
    };

    // Facebook mirrors its bars in right-to-left languages (Hebrew, Arabic…) with explicit
    // positions, not the page's dir: the side a gear goes on follows the bar's own direction.
    const isRtl = (el) => !!el && getComputedStyle(el).direction === 'rtl';

    const findInsertionPoint = () => {
      const iconSpan = Array.from(document.querySelectorAll('span'))
        .find(span => span.textContent === '󱥊');
      const container = iconSpan?.closest('div[role="button"]')?.parentNode;

      const desktopTarget = document.querySelector(
        '.x6s0dn4.x78zum5.x1s65kcs.x1n2onr6.x1ja2u2z'
      );

      return { container, desktopTarget, rtl: isRtl(iconSpan?.closest('div[role="button"]')) };
    };

    const createButton = () => {
      const btn = document.createElement('button');
      btn.id = BUTTON_ID;
      const { desktopTarget, rtl } = findInsertionPoint();
      const mobile = desktopTarget === null;
      // Next to Facebook's Search and Menu buttons: on the right, or on the left when the
      // header is mirrored (the logo is then on the right).
      btn.setAttribute('style', `
        position: ${mobile ? 'fixed' : 'block'};
        top: ${mobile ? 4 : 8}px;
        ${rtl ? 'left' : 'right'}: ${mobile ? 96 : 100}px;
        width: ${mobile ? '36px' : 'auto'};
        height: ${mobile ? '36px' : 'auto'};
        padding: 0;
        background: ${mobile ? getBackdropColor() : 'transparent'};
        border: none;
        border-radius: 50%;
        cursor: pointer;
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 9999;
        pointer-events: auto;
      `);
      btn.setAttribute('aria-label', 'Astryxbook settings');
      btn.innerHTML = mobile
        ? ICON_SVG.replace('%FILL%', getFillColor()).replace(/"28"/g, '"22"')
        : ICON_SVG.replace('%FILL%', getFillColor());
      btn.onclick = () => window.AstryxPage
        ? window.AstryxPage.postMessage(JSON.stringify({ call: 'openSettings' }))
        : window.SettingsBridge?.onSettingsToggle?.();
      return btn;
    };

    const insertButton = () => {
      // Feed only: the button is fixed to the top of the window, so on another page loaded
      // directly (a notification link) it sat over the tab bar. Other pages get their own
      // gear in their top bar (below). The desktop layout's header is on every page.
      const onFeed = () => isFeed() || window.isDesktopMode();
      if (document.getElementById(BUTTON_ID) || !onFeed()) return;

      const { container, desktopTarget } = findInsertionPoint();
      const button = createButton();

      if (desktopTarget) desktopTarget.insertBefore(button, desktopTarget.firstChild);
      else if (container) container.insertBefore(button, container.firstChild);
    };

    insertButton();

    const observer = new MutationObserver(() => {
      if (!isFeed() && !window.isDesktopMode()) {
        document.getElementById(BUTTON_ID)?.remove();
      } else if (!document.getElementById(BUTTON_ID)) {
        insertButton();
      }
    });

    observer.observe(document.body, { childList: true, subtree: true });

    // The Reels viewer (m.facebook.com/reel/...) has its own top bar instead of the header
    // the button above goes into, so there was no way to open Settings while watching
    // reels. Show a second button at the right end of that bar while on a reel page and
    // no other settings button is showing (the desktop header has one). Checked once a
    // second: Facebook moves between the feed and reels in-page.
    const REELS_BUTTON_ID = 'custom-settings-btn-reels';
    const isReelsPage = () => /^\/reels?(\/|$)/.test(location.pathname);
    const mainButtonShown = () => {
      const main = document.getElementById(BUTTON_ID);
      return !!main && main.getClientRects().length > 0;
    };
    // The right end of the bar, or the left end when it's mirrored (Back is then on the right).
    // Checked on every tick: the bar may not be there yet when the button is first added.
    const placeReelsButton = (btn) => {
      const bar = document.elementFromPoint(window.innerWidth / 2, 30);
      const rtl = isRtl(bar && bar !== btn && !btn.contains(bar) ? bar : document.querySelector('.m'));
      btn.style.left = rtl ? '12px' : '';
      btn.style.right = rtl ? '' : '12px';
    };
    const updateReelsButton = () => {
      const existing = document.getElementById(REELS_BUTTON_ID);
      if (!isReelsPage() || mainButtonShown()) {
        existing?.remove();
        return;
      }
      if (existing) {
        placeReelsButton(existing);
        return;
      }
      const btn = document.createElement('button');
      btn.id = REELS_BUTTON_ID;
      btn.setAttribute('aria-label', 'Settings');
      btn.setAttribute('style', `
        position: fixed;
        top: 12px;
        width: 40px;
        height: 48px;
        padding: 0;
        background: transparent;
        border: none;
        display: flex;
        align-items: center;
        justify-content: center;
        z-index: 9999;
        pointer-events: auto;
      `);
      placeReelsButton(btn);
      // The reels bar is always dark.
      btn.innerHTML = ICON_SVG.replace('%FILL%', '#ffffff');
      btn.onclick = () => window.AstryxPage
        ? window.AstryxPage.postMessage(JSON.stringify({ call: 'openSettings' }))
        : window.SettingsBridge?.onSettingsToggle?.();
      document.body.appendChild(btn);
    };
    updateReelsButton();
    setInterval(updateReelsButton, 1000);

    // Other pages (a Page, a profile, Notifications…) have a top bar with Back, a title and
    // their own buttons (Search, Mark all as read…) instead of the feed's header, so Settings
    // could only be opened from Home. Put a gear between the title and those buttons. Found
    // from the Back button, whose icon is the same on every page. Checked once a second, like
    // the Reels button.
    const PAGE_BUTTON_ID = 'custom-settings-btn-page';
    // Back points left, or right in a mirrored bar.
    const BACK_GLYPHS = ['\u{F30DF}', '\u{F30E1}'];
    // The bar: the nearest ancestor of Back whose first child holds Back, then the title, then
    // the bar's buttons; the gear goes just before the buttons.
    const findPageBar = () => {
      const back = Array.from(document.querySelectorAll('span'))
        .find(s => BACK_GLYPHS.includes(s.textContent) && s.getBoundingClientRect().top < 160);
      let branch = back?.closest('div[role="button"]');
      for (let i = 0; branch && i < 3; i++) {
        const bar = branch.parentNode;
        const items = bar ? Array.from(bar.children).filter(el => el.id !== PAGE_BUTTON_ID) : [];
        if (items.length === 3 && items[0] === branch && items[2].querySelector('[role="button"]')) {
          return { title: items[1], slot: items[2] };
        }
        branch = bar;
      }
      return null;
    };
    const updatePageButton = () => {
      const existing = document.getElementById(PAGE_BUTTON_ID);
      if (mainButtonShown() || document.getElementById(REELS_BUTTON_ID) || isReelsPage()) {
        existing?.remove();
        return;
      }
      const pageBar = findPageBar();
      const slot = pageBar?.slot;
      if (!slot) {
        existing?.remove();
        return;
      }
      if (existing && existing.nextSibling === slot) {
        existing.querySelector('svg')?.setAttribute('fill', getFillColor());
        return;
      }
      existing?.remove();
      // The bar stacks its items with negative top margins and fixed left offsets, not normal
      // flow: give the gear the same, just before the bar's buttons (left of them, right in a mirrored
      // bar), and narrow the title to
      // make room.
      // Each item is pulled up by the previous one's height: the gear's own pull-up cancels the
      // title's, and its height is what the buttons' box then pulls back up.
      const size = 44;
      const height = -parseFloat(slot.style.marginTop) || slot.offsetHeight || 45;
      const pullUp = pageBar.title.offsetHeight || height;
      // A mirrored bar places its items from the right (margin-right) instead of the left.
      const side = slot.style.marginRight && !slot.style.marginLeft ? 'right' : 'left';
      const slotOffset = side === 'right'
        ? parseFloat(slot.style.marginRight)
        : parseFloat(slot.style.marginLeft) || slot.offsetLeft;
      const title = pageBar.title;
      if (!title.dataset.astryxWidth) {
        title.dataset.astryxWidth = title.style.width;
        const width = Math.max(0, title.offsetWidth - size);
        title.style.width = width + 'px';
        title.style.overflow = 'hidden';
        // Its text box has its own fixed width; shrink it too so the ellipsis stays visible.
        title.querySelectorAll('[style*="width"]').forEach(el => {
          const w = parseFloat(el.style.width);
          if (w > width) el.style.width = Math.max(0, w - size) + 'px';
        });
        // The server cut the title to fit the full width and added its own "...", which the
        // narrower box then cut off mid-word ("Comments" became "Comr"). The line ends with
        // an ellipsis of its own now, in the title's font.
        title.querySelectorAll('div').forEach(el => {
          const text = el.querySelector(':scope > span');
          if (!text || !getComputedStyle(el).whiteSpace.startsWith('pre')) return;
          const font = getComputedStyle(text);
          Object.assign(el.style, {
            overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'pre',
            font: font.font, lineHeight: font.lineHeight,
          });
        });
      }
      const btn = document.createElement('button');
      btn.id = PAGE_BUTTON_ID;
      btn.setAttribute('aria-label', 'Astryxbook settings');
      btn.setAttribute('style', `
        margin-top: -${pullUp}px;
        margin-${side}: ${Math.max(0, slotOffset - size)}px;
        width: ${size}px;
        height: ${height}px;
        padding: 0;
        flex: none;
        background: transparent;
        border: none;
        display: flex;
        align-items: center;
        justify-content: center;
        pointer-events: auto;
      `);
      btn.innerHTML = ICON_SVG.replace('%FILL%', getFillColor()).replace(/"28"/g, '"24"');
      btn.onclick = () => window.AstryxPage
        ? window.AstryxPage.postMessage(JSON.stringify({ call: 'openSettings' }))
        : window.SettingsBridge?.onSettingsToggle?.();
      slot.parentNode.insertBefore(btn, slot);
    };
    updatePageButton();
    setInterval(updatePageButton, 1000);

    // observer for theme-color changes
    const themeMeta = document.querySelector('meta[name="theme-color"]');
    if (themeMeta) {
      new MutationObserver(updateButtonColor).observe(themeMeta, {
        attributes: true,
        attributeFilter: ['content'],
      });
    }
  });
})();


// Color Extraction Script
// Tells the app the colour behind the status bar: black on a media viewer, else the first
// opaque background at the top edge of the page, or the theme-color meta when there's none.
// Facebook keeps theme-color white on its black full-screen viewers (Reels, photos) and
// navigates in-page, so the meta alone left a white bar over them. Checked on meta changes and once a second; only
// changes are sent.
(function() {
    const toHex = (c) => {
        const m = c && c.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?\)/);
        if (!m || (m[4] !== undefined && parseFloat(m[4]) < 1)) return null;
        return '#' + [m[1], m[2], m[3]].map(n => (+n).toString(16).padStart(2, '0')).join('');
    };
    const topColor = () => {
        let el = document.elementFromPoint(window.innerWidth / 2, 1);
        for (; el && el !== document.documentElement; el = el.parentElement) {
            const hex = toHex(getComputedStyle(el).backgroundColor);
            if (hex) return hex;
        }
        return null;
    };
    // Facebook's photo, video, reel and story viewers are always black, but paint it without
    // a background the page can read, so they're recognised by URL.
    const isMediaViewer = () => {
        const path = window.location.pathname;
        return /\/(stories|reel|highlights)\//.test(path) ||
            /^\/photo(\.php)?\/?$/.test(path) ||
            /\/photos\/(?:[^/]+\/)?\d+/.test(path) ||
            /\/videos\/(?:[^/]+\/)?\d+/.test(path);
    };
    let last = null;
    const notify = () => {
        const meta = document.querySelector('meta[name="theme-color"]');
        const color = (isMediaViewer() ? '#000000' : topColor()) || meta?.content || '';
        if (!color || color === last) return;
        last = color;
        // AstryxPage: the app's Facebook-only message listener; ThemeBridge: older WebViews.
        if (window.AstryxPage)
            window.AstryxPage.postMessage(JSON.stringify({ call: 'themeColor', color: color }));
        else
            window.ThemeBridge?.onThemeColorChanged?.(color);
    };
    // The app asks again after a system light/dark switch.
    window.__astryxThemeRefresh = () => { last = null; notify(); };
    notify();
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) {
        new MutationObserver(notify)
            .observe(meta, { attributes: true, attributeFilter: ['content'] });
    }
    setInterval(notify, 1000);
})();

// File Download Script
// Saves a blob only when the page starts a download with it: a blob: URL on an
// <a download> link that is clicked. Pages create blob URLs for plenty of other
// things (desktop Messenger decrypts every photo of an encrypted chat into one),
// and those must never end up in the public Downloads folder.
(function() {
    if (window._downloadBridgeInitialized) return;
    window._downloadBridgeInitialized = true;
    const blobs = new Map();
    const originalCreateObjectURL = URL.createObjectURL;
    const originalRevokeObjectURL = URL.revokeObjectURL;
    // The blob URL already keeps its blob alive until revoked, so the map
    // holds nothing longer than the page itself does.
    URL.createObjectURL = function(obj) {
        const url = originalCreateObjectURL.call(URL, obj);
        if (obj instanceof Blob) blobs.set(url, obj);
        return url;
    };
    URL.revokeObjectURL = function(url) {
        blobs.delete(url);
        return originalRevokeObjectURL.call(URL, url);
    };

    const save = function(anchor) {
        if (!anchor || !anchor.hasAttribute('download')) return;
        const blob = blobs.get(anchor.href);
        if (!blob) return;
        const reader = new FileReader();
        reader.onloadend = function() {
            if (!reader.result) return;
            // AstryxDownloads: the app's Facebook-only message listener;
            // DownloadBridge: older WebViews and older app versions.
            if (window.AstryxDownloads)
                AstryxDownloads.postMessage(JSON.stringify({ data: reader.result, mimeType: blob.type }));
            else if (window.DownloadBridge)
                DownloadBridge.downloadBase64File(reader.result, blob.type);
        };
        reader.readAsDataURL(blob);
    };
    // Links in the document, clicked by the user or by script.
    document.addEventListener('click', function(e) {
        save(e.target.closest && e.target.closest('a'));
    }, true);
    // Detached links clicked by script (the usual way to trigger a download);
    // connected ones are already handled by the listener above.
    const originalClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function() {
        if (!this.isConnected) save(this);
        return originalClick.call(this);
    };
})();
// Share via… in Facebook's share sheet
// Facebook's sheet offers Facebook-only targets and Copy link, with no way into other apps.
// This adds a "Share via…" row under Copy link: it runs Copy link, catches the link
// Facebook copies, and hands it to the Android share sheet. Only with the message listener
// (WebView 83+).
(function() {
    if (window._astryxShareViaInitialized) return;
    window._astryxShareViaInitialized = true;
    const ROW_ID = 'astryx-share-via';
    const COPY_GLYPH = '\u{F310F}';
    const ROW_HEIGHT = 56;
    const LABEL = (navigator.language || '').toLowerCase().startsWith('he') ? 'שיתוף באמצעות…' : 'Share via…';
    const SHARE_ICON = '<svg width="20" height="20" viewBox="0 -960 960 960" fill="currentColor"><path d="M720-80q-50 0-85-35t-35-85q0-7 1-14.5t3-13.5L322-392q-17 15-38 23.5t-44 8.5q-50 0-85-35t-35-85q0-50 35-85t85-35q23 0 44 8.5t38 23.5l282-164q-2-6-3-13.5t-1-14.5q0-50 35-85t85-35q50 0 85 35t35 85q0 50-35 85t-85 35q-23 0-44-8.5T638-672L356-508q2 6 3 13.5t1 14.5q0 7-1 14.5t-3 13.5l282 164q17-15 38-23.5t44-8.5q50 0 85 35t35 85q0 50-35 85t-85 35Z"/></svg>';

    // The last text the page put on the clipboard, however it did it.
    let lastCopied = null;
    const remember = (text) => { if (text) lastCopied = String(text); };
    if (navigator.clipboard && navigator.clipboard.writeText) {
        const writeText = navigator.clipboard.writeText.bind(navigator.clipboard);
        navigator.clipboard.writeText = (text) => { remember(text); return writeText(text); };
    }
    const setData = DataTransfer.prototype.setData;
    DataTransfer.prototype.setData = function(type, data) {
        if (/^text(\/plain)?$/i.test(type)) remember(data);
        return setData.call(this, type, data);
    };
    const execCommand = document.execCommand.bind(document);
    document.execCommand = (command, ...args) => {
        if (String(command).toLowerCase() === 'copy') {
            const active = document.activeElement;
            remember(active && 'value' in active ? active.value : String(window.getSelection() || ''));
        }
        return execCommand(command, ...args);
    };

    const shareCopiedLink = (copyButton) => {
        lastCopied = null;
        copyButton.click();
        const started = Date.now();
        const poll = setInterval(() => {
            const link = lastCopied && lastCopied.trim();
            if (link && /^https:\/\//.test(link)) {
                clearInterval(poll);
                window.AstryxPage.postMessage(JSON.stringify({ call: 'shareLink', url: link }));
            } else if (Date.now() - started > 3000) {
                clearInterval(poll);
            }
        }, 100);
    };

    const addRow = () => {
        // Checked here, not at load: the listener object can show up after this script runs.
        if (!window.AstryxPage || document.getElementById(ROW_ID)) return;
        const glyph = Array.from(document.querySelectorAll('[role="presentation"] [role="button"] span'))
            .find(span => span.textContent === COPY_GLYPH);
        const copyButton = glyph && glyph.closest('[role="button"]');
        const row = copyButton && copyButton.parentElement;
        if (!row || !row.parentElement) return;

        const copyLabel = copyButton.getAttribute('aria-label') || '';
        const clone = row.cloneNode(true);
        clone.id = ROW_ID;
        const button = clone.querySelector('[role="button"]');
        button.setAttribute('aria-label', LABEL);
        clone.querySelectorAll('*').forEach(el => {
            if (el.childElementCount) return;
            if (el.textContent === COPY_GLYPH) el.innerHTML = SHARE_ICON;
            else if (el.textContent.trim() === copyLabel) el.textContent = LABEL;
        });
        // The label's box is sized to "Copy link".
        clone.querySelectorAll('[style*="width"]').forEach(el => {
            if (el !== clone && el !== button && el.textContent === LABEL) el.style.width = 'auto';
        });
        // Facebook's own handlers must not see taps on the copy: they'd run Copy link twice.
        ['touchstart', 'touchend', 'pointerdown', 'pointerup', 'mousedown', 'mouseup'].forEach(type =>
            clone.addEventListener(type, e => e.stopPropagation(), true));
        clone.addEventListener('click', e => {
            e.stopPropagation();
            e.preventDefault();
            shareCopiedLink(copyButton);
        }, true);
        row.parentElement.insertBefore(clone, row.nextSibling);

        // The sheet's boxes have fixed heights: make room for the new row.
        let el = row.parentElement;
        for (let i = 0; i < 6 && el && el !== document.body; i++, el = el.parentElement) {
            const height = parseFloat(el.style.height);
            if (height >= ROW_HEIGHT * 2) el.style.height = (height + ROW_HEIGHT) + 'px';
        }
    };

    let scheduled = false;
    new MutationObserver(() => {
        if (scheduled) return;
        scheduled = true;
        requestAnimationFrame(() => { scheduled = false; addRow(); });
    }).observe(document.documentElement, { childList: true, subtree: true });
})();
