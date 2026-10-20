// Zoom Disable Script
(function() {
    // The Messages layer stays zoomable: the desktop photo viewer shows a photo sent in a chat
    // small at phone width, with no zoom of its own and no address of its own to except it by.
    if (window.__astryxMessagesLayer) return;
    function applyViewportLock() {
        let viewport = document.querySelector('meta[name="viewport"]');
        if (!viewport) {
            viewport = document.createElement('meta');
            viewport.name = "viewport";
            document.head.appendChild(viewport);
        }
        // allow zooming on photos.
        if (!window.location.href.includes("facebook.com/photo.php"))
            viewport.content = "width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no";
    }

    applyViewportLock();

    const observer = new MutationObserver(() => {
        applyViewportLock();
    });

    observer.observe(document.head || document.documentElement, {
        childList: true,
        subtree: true
    });
})();