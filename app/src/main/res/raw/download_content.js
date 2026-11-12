/*
 * Script to add download buttons for stories, stories highlights and reels on Facebook
 * Original Author: @YeiversonYurgaky
 */

(function() {
  // The viewers, the fallback picture, the button and the watch are shared with
  // copy_to_clipboard.js (media_viewer.js, run before this).
  const viewer = window.__astryxMediaViewer;

  let currentContentContainer = null;
  let lastDownloadedUrl = null;
  const DOWNLOAD_BTN_ID = "materialbook-global-downloader";

  // A viewer's media, by priority.
  const MEDIA_ELEMENTS = [
    'div[role="dialog"] video:not([hidden])',
    'div[role="dialog"] img[src*="fbcdn"]:not([width="16"]):not([hidden])',
    viewer.HIGHLIGHT + ' video',
    viewer.HIGHLIGHT + ' img[src*="fbcdn"]',
    'div[data-pagelet="Story"] video',
    'div[aria-label*="reel"] video',
    'div[data-pagelet="ProfilePhoto"] img[src*="fbcdn"]'
  ];

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

  // Of elements, the one taking the most of the screen (at least half of it showing).
  const mostShown = (elements) => Array.from(elements)
    .filter(el => sourceOf(el) && shareOnScreen(el) >= MOSTLY_ON_SCREEN)
    .sort((a, b) => onScreenArea(b) - onScreenArea(a))[0];

  // The media on screen: by selector priority, then the one taking the most of the screen (on
  // Reels the next and previous reels are in the page too, mostly off screen), videos first.
  const getCurrentMediaElement = () => {
    for (const selector of MEDIA_ELEMENTS) {
      const element = mostShown(document.querySelectorAll(selector));
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

  // A viewer's own address (a reel's and a video's too, not a Page's Videos tab, /NASA/videos/),
  // or a viewer open in the page.
  const isInStoryOrReelView = () => {
    const path = window.location.pathname;
    return viewer.atPhotoOrStory() ||
      /\/reel\//.test(path) ||
      (/\/watch\/?$/.test(path) && window.location.search.length > 1) ||
      /\/videos\/(?:[^/]+\/)?\d+/.test(path) ||
      viewer.viewerOpen();
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
    const videoElement = mostShown(container.querySelectorAll("video:not([hidden])"));
    if (videoElement && downloadElement(videoElement)) return;

    // No video: the largest image there, then a background image.
    const imageUrl = viewer.fallbackImageUrl(container, lastDownloadedUrl, false);
    if (imageUrl) {
      downloadMedia(imageUrl, false);
      lastDownloadedUrl = imageUrl;
    }
  };

  const BUTTON = {
    id: DOWNLOAD_BTN_ID,
    right: 15,
    label: "Download content",
    icon: "M480,640L280,440L336,384L440,488L440,160L520,160L520,488L624,384L680,440L480,640ZM240,800Q207,800 183.5,776.5Q160,753 160,720L160,600L240,600L240,720Q240,720 240,720Q240,720 240,720L720,720Q720,720 720,720Q720,720 720,720L720,600L800,600L800,720Q800,753 776.5,776.5Q753,800 720,800L240,800Z",
    onClick: () => {
      // Reset state
      currentContentContainer = null;
      lastDownloadedUrl = null;

      // Find current media and container
      const mediaElement = getCurrentMediaElement();
      if (mediaElement) {
        currentContentContainer = viewer.findContentContainer(mediaElement);
      }

      extractAndDownloadMedia();
    }
  };

  // Shows the button over a viewer with media on screen (or a highlight's), hides it elsewhere.
  // Facebook's "Open app" buttons are hidden by scripts.js, with or without this script.
  const updateButtonVisibility = () => {
    const btn = viewer.button(BUTTON);
    let container = null;

    if (isInStoryOrReelView() && !isFeed()) {
      const mediaElement = getCurrentMediaElement();
      container = mediaElement
        ? viewer.findContentContainer(mediaElement)
        : viewer.highlightShowing('video, img[src*="fbcdn"]');
    }

    currentContentContainer = container;
    btn.classList.toggle("visible", !!container);
  };

  viewer.watch(updateButtonVisibility, ["VIDEO", "IMG"]);
})();