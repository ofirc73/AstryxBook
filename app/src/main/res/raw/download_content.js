/*
 * Script to add download buttons for stories, stories highlights and reels on Facebook
 * Original Author: @YeiversonYurgaky
 */

(function() {
  // Configuration
  const CONFIG = {
    buttonZIndex: 999999,
    debug: false
  };

  // Global state
  let isProcessing = false;
  let currentContentContainer = null;
  let lastDownloadedUrl = null;
  const DOWNLOAD_BTN_ID = "materialbook-global-downloader";

  // Selectors for finding media content
  const SELECTORS = {
    mediaElements: [
      'div[role="dialog"] video:not([hidden])',
      'div[role="dialog"] img[src*="fbcdn"]:not([width="16"]):not([hidden])',
      'div.x1ey2m1c.x9f619.xds687c.x17qophe.x10l6tqk.x13vifvy[role="presentation"] video',
      'div.x1ey2m1c.x9f619.xds687c.x17qophe.x10l6tqk.x13vifvy[role="presentation"] img[src*="fbcdn"]',
      'div[data-pagelet="Story"] video',
      'div[aria-label*="reel"] video',
      'div[data-pagelet="ProfilePhoto"] img[src*="fbcdn"]'
    ],
    containers: [
      'div[role="dialog"]',
      'div[data-pagelet="Story"]',
      'div[aria-label*="story"]',
      '.story-viewer',
      '.story_viewer',
      'div.x1ey2m1c.x9f619.xds687c.x17qophe.x10l6tqk.x13vifvy[role="presentation"]',
      'div[data-pagelet="ProfilePhoto"]',
      'div[aria-label*="photo"]',
      'div[data-pagelet*="ProfileAppSection"]'
    ],
    storyIndicators: [
      'div[data-sigil="story-viewer"]',
      'div[data-sigil="story-popup-header"]',
      'div[data-sigil="story-tray-item"]',
      ".story_body_container",
      ".story_viewer",
      ".story-container",
      'div[aria-label*="highlight"]',
      'div[aria-label*="Highlight"]',
      'div.x1ey2m1c.x9f619.xds687c.x17qophe.x10l6tqk.x13vifvy[role="presentation"]',
      'div[data-pagelet="ProfilePhoto"]'
    ]
  };

  // Utility functions
  const debugLog = (...args) => CONFIG.debug && console.log("[ContentDownloader]", ...args);

  const isElementVisible = (element) => {
    const rect = element.getBoundingClientRect();
    return (
      rect.top >= 0 &&
      rect.left >= 0 &&
      rect.bottom <= (window.innerHeight || document.documentElement.clientHeight)
    );
  };


  // The share of the element that's on screen (0 to 1). A reel's video is taller than the screen,
  // so "entirely on screen" never held for it and the download fell through to a still image.
  const shareOnScreen = (element) => {
    const rect = element.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return 0;
    const w = Math.min(rect.right, window.innerWidth) - Math.max(rect.left, 0);
    const h = Math.min(rect.bottom, window.innerHeight) - Math.max(rect.top, 0);
    return w > 0 && h > 0 ? (w * h) / (rect.width * rect.height) : 0;
  };
  const onScreenArea = (element) => {
    const rect = element.getBoundingClientRect();
    return shareOnScreen(element) * rect.width * rect.height;
  };
  const MOSTLY_ON_SCREEN = 0.5;

  const sourceOf = (element) => element.currentSrc || element.src || "";

  // Find the appropriate container for the content
  const findContentContainer = (element) => {
    if (!element) return null;

    for (const selector of SELECTORS.containers) {
      const container = element.closest(selector);
      if (container) return container;
    }

    return element.parentElement;
  };

  // The media on screen: by selector priority, then the one taking the most of the screen (on
  // Reels the next and previous reels are in the page too, mostly off screen), videos first.
  const getCurrentMediaElement = () => {
    const mostShown = (elements) => elements
      .filter(el => sourceOf(el) && shareOnScreen(el) >= MOSTLY_ON_SCREEN)
      .sort((a, b) => onScreenArea(b) - onScreenArea(a))[0];

    for (const selector of SELECTORS.mediaElements) {
      const element = mostShown(Array.from(document.querySelectorAll(selector)));
      if (element) return element;
    }

    // Fallback: any large media on screen.
    const large = (el) => {
      const rect = el.getBoundingClientRect();
      return rect.width > 150 && rect.height > 150;
    };
    return mostShown(Array.from(document.querySelectorAll('video:not([hidden])')).filter(large)) ||
      mostShown(Array.from(document.querySelectorAll('img[src*="fbcdn"]:not([width="16"]):not([hidden])')).filter(large));
  };

  // Check if we are in a story or reel view
  const isInStoryOrReelView = () => {
    // A viewer's own URL, not a Page's Videos or Photos tab (/NASA/videos/), where the
    // button used to float over the page header.
    const path = window.location.pathname;
    if (
      /\/(stories|reel|highlights)\//.test(path) ||
      (/\/watch\/?$/.test(path) && window.location.search.length > 1) ||
      /^\/photo(\.php)?\/?$/.test(path) ||
      /\/photos\/(?:[^/]+\/)?\d+/.test(path) ||
      /\/videos\/(?:[^/]+\/)?\d+/.test(path)
    ) {
      return true;
    }

    // Element selectors check
    for (const selector of SELECTORS.storyIndicators) {
      if (document.querySelector(selector)) {
        return true;
      }
    }

    return false;
  };

  // Facebook plays videos through Media Source Extensions, so a <video>'s src is a blob: URL
  // that fetch() can't read. The player around it (data-mcomponent="MVideo") carries the whole
  // file, video and audio in one MP4, in data-video-url: download that, whenever it's there
  // (a reel's own https source may be a video-only stream).
  const mediaUrl = (element) => {
    const src = sourceOf(element);
    if (element.tagName !== "VIDEO") return src;
    const player = element.closest("[data-video-url]");
    const url = player && player.getAttribute("data-video-url");
    if (url && url.startsWith("https://")) return url;
    return src && !src.startsWith("blob:") ? src : null;
  };

  // Tells the app a video couldn't be downloaded, so the user sees a message instead of nothing.
  const reportVideoFailure = () => {
    const listener = window.AstryxDownloads;
    const bridge = window.DownloadBridge;
    if (listener) {
      listener.postMessage(JSON.stringify({ failed: "video" }));
    } else if (bridge && bridge.downloadFailed) {
      bridge.downloadFailed();
    }
  };

  // Download media from URL. An https address goes to the app (the app's message listener), which
  // fetches it itself and writes it to Downloads as it arrives: read here and handed over whole as
  // one base64 string, a long video ran the app out of memory. A blob: or data: one is read here.
  const downloadMedia = (url, isVideo) => {
    if (window.AstryxDownloads && /^https:\/\//.test(url)) {
      window.AstryxDownloads.postMessage(JSON.stringify({ url: url, video: !!isVideo }));
      return;
    }
    fetch(url)
      .then(response => {
        if (!response.ok) throw new Error("HTTP " + response.status);
        return response.blob();
      })
      .then(blob => {
        // AstryxDownloads: the app's Facebook-only message listener;
        // DownloadBridge: older WebViews and older app versions.
        const listener = window.AstryxDownloads;
        const bridge = window.DownloadBridge;
        if (listener || (bridge && bridge.downloadBase64File)) {
          const reader = new FileReader();
          reader.onloadend = function() {
            if (!reader.result) return;
            const mimeType = blob.type || (isVideo ? "video/mp4" : "image/jpeg");
            if (listener) {
              listener.postMessage(JSON.stringify({ data: reader.result, mimeType: mimeType }));
            } else {
              bridge.downloadBase64File(reader.result, mimeType);
            }
          };
          reader.readAsDataURL(blob);
        }
      })
      .catch(err => {
        console.error("Error downloading media:", err);
        if (isVideo) reportVideoFailure();
      });
  };

  // Downloads a <video> or <img>; false when it's the one just downloaded.
  const downloadElement = (element) => {
    const url = mediaUrl(element);
    if (!url) {
      reportVideoFailure();
      return true;
    }
    if (url === lastDownloadedUrl) return false;
    downloadMedia(url, element.tagName === "VIDEO");
    lastDownloadedUrl = url;
    return true;
  };

  // Extract and download videos or images
  const extractAndDownloadMedia = () => {
    // Find current media element
    const mediaElement = getCurrentMediaElement();

    if (mediaElement && sourceOf(mediaElement) && downloadElement(mediaElement)) return;

    // Get container to search in
    const container = currentContentContainer || document.body;

    // Find videos first: the one on screen, not the first in the page.
    const videoElement = Array.from(container.querySelectorAll("video:not([hidden])"))
      .filter(video => sourceOf(video) && shareOnScreen(video) >= MOSTLY_ON_SCREEN)
      .sort((a, b) => onScreenArea(b) - onScreenArea(a))[0];
    if (videoElement && downloadElement(videoElement)) return;

    // If no video, try with images
    const images = Array.from(container.querySelectorAll("img"))
      .filter(img =>
        img.src &&
        !img.src.includes("data:image") &&
        img.src !== lastDownloadedUrl
      )
      .filter(img => {
        const rect = img.getBoundingClientRect();
        return rect.width >= 100 && rect.height >= 100 && isElementVisible(img);
      })
      .sort((a, b) => {
        const areaA = a.getBoundingClientRect().width * a.getBoundingClientRect().height;
        const areaB = b.getBoundingClientRect().width * b.getBoundingClientRect().height;
        return areaB - areaA; // Largest first
      });

    if (images.length > 0) {
      downloadMedia(images[0].src, false);
      lastDownloadedUrl = images[0].src;
      return;
    }

    // Try background images as last resort
    const backgroundElements = Array.from(container.querySelectorAll("*"));

    for (const el of backgroundElements) {
      const style = window.getComputedStyle(el);
      const bgImage = style.backgroundImage;

      if (
        bgImage &&
        bgImage !== "none" &&
        (bgImage.includes("fbcdn.net") || bgImage.includes("fbsbx.com"))
      ) {
        const imageUrl = bgImage.replace(/^url\(['"](.+)['"]\)$/, "$1");

        if (imageUrl !== lastDownloadedUrl) {
          downloadMedia(imageUrl, false);
          lastDownloadedUrl = imageUrl;
          return;
        }
      }
    }

    // Nothing found
    debugLog("No media content found to download");
  };

  // Create and manage download button
  const createDownloadButton = () => {
    // Add CSS for the button
    const css = `
      #${DOWNLOAD_BTN_ID} {
        position: fixed;
        top: 70px;
        right: 15px;
        width: 40px;
        height: 40px;
        background-color: rgba(0, 0, 0, 0.7);
        color: white;
        border-radius: 50%;
        z-index: ${CONFIG.buttonZIndex};
        border: none;
        display: none;
        align-items: center;
        justify-content: center;
        font-size: 20px;
        box-shadow: 0 2px 5px rgba(0,0,0,0.3);
        cursor: pointer;
        background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 960 960" fill="white"><path d="M480,640L280,440L336,384L440,488L440,160L520,160L520,488L624,384L680,440L480,640ZM240,800Q207,800 183.5,776.5Q160,753 160,720L160,600L240,600L240,720Q240,720 240,720Q240,720 240,720L720,720Q720,720 720,720Q720,720 720,720L720,600L800,600L800,720Q800,753 776.5,776.5Q753,800 720,800L240,800Z"/></svg>');
        background-repeat: no-repeat;
        background-position: center;
        background-size: 24px;
      }
      #${DOWNLOAD_BTN_ID}.visible {
        display: flex !important;
      }
    `;

    const style = document.createElement("style");
    style.textContent = css;
    document.head.appendChild(style);

    // Create button element
    const btn = document.createElement("button");
    btn.id = DOWNLOAD_BTN_ID;
    btn.setAttribute("aria-label", "Download content");

    btn.addEventListener("click", () => {
      // Reset state
      currentContentContainer = null;
      lastDownloadedUrl = null;

      // Find current media and container
      const mediaElement = getCurrentMediaElement();
      if (mediaElement) {
        currentContentContainer = findContentContainer(mediaElement);
      }

      extractAndDownloadMedia();
    });

    document.body.appendChild(btn);

    return btn;
  };

  // Show/hide download button based on context
  const updateButtonVisibility = () => {
    let btn = document.getElementById(DOWNLOAD_BTN_ID);
    if (!btn) btn = createDownloadButton();

    if (isInStoryOrReelView() && !isFeed()) {
      const mediaElement = getCurrentMediaElement();

      // Always hide "Open in App" buttons
      hideOpenAppButtons();

      if (mediaElement) {
        currentContentContainer = findContentContainer(mediaElement);
        btn.classList.add("visible");
        return;
      }

      // Special case for highlighted stories
      const highlightedStoryContainer = document.querySelector(
        'div.x1ey2m1c.x9f619.xds687c.x17qophe.x10l6tqk.x13vifvy[role="presentation"]'
      );

      if (highlightedStoryContainer) {
        const mediaInHighlight = highlightedStoryContainer.querySelector(
          'video, img[src*="fbcdn"]'
        );

        if (mediaInHighlight && isElementVisible(mediaInHighlight)) {
          currentContentContainer = highlightedStoryContainer;
          btn.classList.add("visible");
          return;
        }
      }
    }

    // Hide button if not in relevant view
    btn.classList.remove("visible");
    currentContentContainer = null;
  };

  const hideOpenAppButtons = (root = document) => {
        // Find all div[role="button"] elements
        const buttons = root.querySelectorAll('div[role="button"]');

        buttons.forEach(button => {
          // Check if it contains div.fl.ac with a span containing the 󱥬 symbol
          const flAcDiv = button.querySelector('div.fl.ac');

          if (flAcDiv) {
            const span = flAcDiv.querySelector('span');
            if (span && span.textContent.includes('󱥬')) {
              button.style.display = 'none';
            }
          }
        });
  };

  // Main processing function
  const processPage = () => {
    if (isProcessing) return;
    isProcessing = true;

    try {
      updateButtonVisibility();
    } finally {
      isProcessing = false;
    }
  };

  // Initialize
  const init = () => {
    // Reset state
    currentContentContainer = null;
    lastDownloadedUrl = null;

    // Initial check
    processPage();

    // Set up DOM observer
    const observer = new MutationObserver(mutations => {
      const hasRelevantChanges = mutations.some(
        mutation =>
          (mutation.type === "childList" && mutation.addedNodes.length > 0) ||
          (mutation.type === "attributes" &&
            (mutation.target.tagName === "VIDEO" ||
             mutation.target.tagName === "IMG"))
      );
      if (hasRelevantChanges) processPage();
    });

    observer.observe(document.body, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ["src", "style", "class"]
    });
  };

  // Start when document is ready
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();