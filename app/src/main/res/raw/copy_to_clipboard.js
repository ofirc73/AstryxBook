/*
 * Script to add copy to clipboard buttons for images on Facebook
 * Based on download_content.js
 */

(function() {
  // The viewers, the fallback picture, the button and the watch are shared with
  // download_content.js (media_viewer.js, run before this).
  const viewer = window.__astryxMediaViewer;

  let currentContentContainer = null;
  let lastCopiedUrl = null;
  const COPY_BTN_ID = "materialbook-clipboard-copier";

  // A viewer's image, by priority.
  const IMAGES = [
    'div[role="dialog"] img[src*="fbcdn"]:not([width="16"]):not([hidden])',
    viewer.HIGHLIGHT + ' img[src*="fbcdn"]',
    'div[data-pagelet="Story"] img[src*="fbcdn"]',
    'div[aria-label*="reel"] img[src*="fbcdn"]',
    'div[data-pagelet="ProfilePhoto"] img[src*="fbcdn"]'
  ];

  // Get the current visible image element
  const getCurrentImageElement = () => {
    // Try each selector in order of priority
    for (const selector of IMAGES) {
      const elements = document.querySelectorAll(selector);

      // Find the first visible element
      for (const element of elements) {
        if (viewer.isElementVisible(element) && element.src) {
          return element;
        }
      }
    }

    // Fallback: look for any large visible image
    return Array.from(
      document.querySelectorAll('img[src*="fbcdn"]:not([width="16"]):not([hidden])')
    ).find(el => {
      const rect = el.getBoundingClientRect();
      return viewer.isElementVisible(el) && rect.width > 150 && rect.height > 150 && el.src;
    });
  };

  // A photo's or a story's own address, or a viewer open in the page.
  const isInContentView = () => {
    const path = window.location.pathname;
    // Reels and videos have nothing to copy: the only image there is the video's poster. A reel
    // from the feed opens at /<page>/videos/<id>/, the Reels tab at /reel/<id>/. (A Like there
    // brought the button up.)
    if (
      /^\/reels?(\/|$)/.test(path) ||
      /^\/watch\/?$/.test(path) ||
      /\/videos\/(?:[^/]+\/)?\d+/.test(path)
    ) {
      return false;
    }
    return viewer.atPhotoOrStory() || viewer.viewerOpen();
  };

  // Copy image to clipboard
  const copyImageToClipboard = (url) => {
    fetch(url)
      .then(response => response.blob())
      .then(blob => {
        // AstryxClipboard: the app's Facebook-only message listener;
        // ClipboardBridge: older WebViews and older app versions.
        if (window.AstryxClipboard || (window.ClipboardBridge && window.ClipboardBridge.copyImageToClipboard)) {
          const reader = new FileReader();
          reader.onloadend = function() {
            if (reader.result) {
              const type = blob.type || "image/jpeg";
              if (window.AstryxClipboard) {
                window.AstryxClipboard.postMessage(JSON.stringify({ data: reader.result, mimeType: type }));
              } else {
                window.ClipboardBridge.copyImageToClipboard(reader.result, type);
              }
            }
          };
          reader.readAsDataURL(blob);
        } else {
          try {
            navigator.clipboard.write([
              new ClipboardItem({
                [blob.type]: blob
              })
            ]).then(() => {
              // Success - bridge will show toast
            }).catch(err => {
              console.error("Clipboard API error:", err);
            });
          } catch (err) {
            console.error("Clipboard not supported:", err);
          }
        }
      })
      .catch(err => {
        console.error("Error copying image:", err);
      });
  };

  // Extract and copy image
  const extractAndCopyImage = () => {
    // The image on screen, else the largest one in the viewer, then a background image.
    const imageElement = getCurrentImageElement();
    const url = imageElement && imageElement.src && imageElement.src !== lastCopiedUrl
      ? imageElement.src
      : viewer.fallbackImageUrl(currentContentContainer || document.body, lastCopiedUrl, true);
    if (url) {
      copyImageToClipboard(url);
      lastCopiedUrl = url;
    }
  };

  const BUTTON = {
    id: COPY_BTN_ID,
    right: 65,
    label: "Copy image to clipboard",
    icon: "M360,720Q327,720 303.5,696.5Q280,673 280,640L280,160Q280,127 303.5,103.5Q327,80 360,80L720,80Q753,80 776.5,103.5Q800,127 800,160L800,640Q800,673 776.5,696.5Q753,720 720,720L360,720ZM360,640L720,640Q720,640 720,640Q720,640 720,640L720,160Q720,160 720,160Q720,160 720,160L360,160Q360,160 360,160Q360,160 360,160L360,640Q360,640 360,640Q360,640 360,640ZM240,840Q207,840 183.5,816.5Q160,793 160,760L160,240L240,240L240,760Q240,760 240,760Q240,760 240,760L600,760L600,840L240,840ZM360,640Q360,640 360,640Q360,640 360,640L360,160Q360,160 360,160Q360,160 360,160L360,160L360,640L360,640Q360,640 360,640Q360,640 360,640Z",
    onClick: () => {
      // Reset state
      currentContentContainer = null;
      lastCopiedUrl = null;

      // Find current image and container
      const imageElement = getCurrentImageElement();
      if (imageElement) {
        currentContentContainer = viewer.findContentContainer(imageElement);
      }

      extractAndCopyImage();
    }
  };

  // Shows the button over a viewer with an image on screen (or a highlight's), hides it elsewhere.
  const updateButtonVisibility = () => {
    const btn = viewer.button(BUTTON);
    let container = null;

    if (isInContentView()) {
      const imageElement = getCurrentImageElement();
      container = imageElement
        ? viewer.findContentContainer(imageElement)
        : viewer.highlightShowing('img[src*="fbcdn"]');
    }

    currentContentContainer = container;
    btn.classList.toggle("visible", !!container);
  };

  viewer.watch(updateButtonVisibility, ["IMG"]);
})();
