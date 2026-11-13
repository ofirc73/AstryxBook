// What download_content.js and copy_to_clipboard.js share (run before both): Facebook's media
// viewers (a story, a highlight, a photo), the picture to take from one when its main media isn't
// found, the round button each puts over the viewer, and the watch that shows and hides it. The two
// were copies of each other that had drifted. Which media is "the one on screen" stays each
// script's own: a download wants the video mostly on screen, a copy an image wholly on it.
(function() {
  if (window.__astryxMediaViewer) return;

  const BUTTON_Z_INDEX = 999999;
  // A story highlight's viewer on the desktop site.
  const HIGHLIGHT = 'div.x1ey2m1c.x9f619.xds687c.x17qophe.x10l6tqk.x13vifvy[role="presentation"]';

  // What holds a viewer's media, nearest first.
  const CONTAINERS = [
    'div[role="dialog"]',
    'div[data-pagelet="Story"]',
    'div[aria-label*="story"]',
    '.story-viewer',
    '.story_viewer',
    HIGHLIGHT,
    'div[data-pagelet="ProfilePhoto"]',
    'div[aria-label*="photo"]',
    'div[data-pagelet*="ProfileAppSection"]'
  ];

  // A viewer open in the page, whatever its address.
  const INDICATORS = [
    'div[data-sigil="story-viewer"]',
    'div[data-sigil="story-popup-header"]',
    'div[data-sigil="story-tray-item"]',
    ".story_body_container",
    ".story_viewer",
    ".story-container",
    'div[aria-label*="highlight"]',
    'div[aria-label*="Highlight"]',
    HIGHLIGHT,
    'div[data-pagelet="ProfilePhoto"]'
  ];

  // Wholly on screen, top to bottom.
  const isElementVisible = (element) => {
    const rect = element.getBoundingClientRect();
    return (
      rect.top >= 0 &&
      rect.left >= 0 &&
      rect.bottom <= (window.innerHeight || document.documentElement.clientHeight)
    );
  };

  const area = (element) => {
    const rect = element.getBoundingClientRect();
    return rect.width * rect.height;
  };

  // The viewer's box around the media.
  const findContentContainer = (element) => {
    if (!element) return null;

    for (const selector of CONTAINERS) {
      const container = element.closest(selector);
      if (container) return container;
    }

    return element.parentElement;
  };

  // A story's, a highlight's or a photo's own address: not a Page's Photos tab (/NASA/photos/),
  // where the buttons used to float over the page header.
  const atPhotoOrStory = () => {
    const path = window.location.pathname;
    return /\/(stories|highlights)\//.test(path) ||
      /^\/photo(\.php)?\/?$/.test(path) ||
      /\/photos\/(?:[^/]+\/)?\d+/.test(path);
  };

  const viewerOpen = () => INDICATORS.some(selector => document.querySelector(selector));

  // The highlight viewer, when the media in it matching mediaSelector is on screen.
  const highlightShowing = (mediaSelector) => {
    const highlight = document.querySelector(HIGHLIGHT);
    const media = highlight && highlight.querySelector(mediaSelector);
    return media && isElementVisible(media) ? highlight : null;
  };

  // The address of the largest image on screen in container (100 px or more each way), then of a
  // background image on Facebook's servers; null when there's neither. Not skip (the one just
  // taken); facebookOnly leaves out images from elsewhere.
  const fallbackImageUrl = (container, skip, facebookOnly) => {
    const image = Array.from(container.querySelectorAll("img"))
      .filter(img =>
        img.src &&
        !img.src.includes("data:image") &&
        img.src !== skip &&
        (!facebookOnly || img.src.includes("fbcdn"))
      )
      .filter(img => {
        const rect = img.getBoundingClientRect();
        return rect.width >= 100 && rect.height >= 100 && isElementVisible(img);
      })
      .sort((a, b) => area(b) - area(a))[0];
    if (image) return image.src;

    for (const el of container.querySelectorAll("*")) {
      const bgImage = window.getComputedStyle(el).backgroundImage;
      if (!bgImage || bgImage === "none" || !(bgImage.includes("fbcdn.net") || bgImage.includes("fbsbx.com"))) continue;
      const url = bgImage.replace(/^url\(['"](.+)['"]\)$/, "$1");
      if (url !== skip) return url;
    }
    return null;
  };

  // The round button over the viewer's top right (id; right, its px from the right edge; label;
  // icon, a 960x960 SVG path; onClick): made the first time, hidden until it gets the class visible.
  const button = (spec) => {
    const existing = document.getElementById(spec.id);
    if (existing) return existing;

    const style = document.createElement("style");
    style.textContent = `
      #${spec.id} {
        position: fixed;
        top: 70px;
        right: ${spec.right}px;
        width: 40px;
        height: 40px;
        background-color: rgba(0, 0, 0, 0.7);
        color: white;
        border-radius: 50%;
        z-index: ${BUTTON_Z_INDEX};
        border: none;
        display: none;
        align-items: center;
        justify-content: center;
        font-size: 20px;
        box-shadow: 0 2px 5px rgba(0,0,0,0.3);
        cursor: pointer;
        background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 960 960" fill="white"><path d="${spec.icon}"/></svg>');
        background-repeat: no-repeat;
        background-position: center;
        background-size: 24px;
      }
      #${spec.id}.visible {
        display: flex !important;
      }
    `;
    document.head.appendChild(style);

    const btn = document.createElement("button");
    btn.id = spec.id;
    btn.setAttribute("aria-label", spec.label);
    btn.addEventListener("click", spec.onClick);
    document.body.appendChild(btn);
    return btn;
  };

  // Runs update now and after each change that can open or close a viewer: nodes added, or a
  // src, style or class change on an element with one of tags.
  const watch = (update, tags) => {
    const start = () => {
      update();
      new MutationObserver(mutations => {
        const relevant = mutations.some(mutation =>
          (mutation.type === "childList" && mutation.addedNodes.length > 0) ||
          (mutation.type === "attributes" && tags.includes(mutation.target.tagName))
        );
        if (relevant) update();
      }).observe(document.body, {
        childList: true,
        subtree: true,
        attributes: true,
        attributeFilter: ["src", "style", "class"]
      });
    };

    if (document.readyState === "loading") {
      document.addEventListener("DOMContentLoaded", start);
    } else {
      start();
    }
  };

  window.__astryxMediaViewer = {
    HIGHLIGHT, isElementVisible, findContentContainer, atPhotoOrStory, viewerOpen, highlightShowing, fallbackImageUrl, button, watch
  };
})();
