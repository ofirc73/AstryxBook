package com.astryx.book.utils
import android.webkit.WebView
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicInteger

// The desktop story creator's text-story backgrounds: its page has the browser preload all of
// them (<link rel="preload" as="image">, StoriesCreateQueryRelayPreloader), 30 full-size
// 1350x2400 JPEGs, 16 MB (read on the emulator, 2026-11-03), which a photo or video story never
// shows. On a weak link they took the whole connection: at 1 Mbps the editor was usable after 84 s
// and its music list after another 22 s; without them, 3.9 s and 3.5 s.
private val STORY_BACKGROUND_URL =
    Regex("""^https://scontent[a-z0-9.-]*\.fbcdn\.net/v/t39\.10873-6/""", RegexOption.IGNORE_CASE)

/** Whether [url] is one of the story creator's text-story background pictures. */
fun isStoryBackgroundUrl(url: String): Boolean = STORY_BACKGROUND_URL.containsMatchIn(url)

/**
 * The Messages layer WebViews opened for photos or videos shared to a story: while the share's
 * files are still waiting (the page preloads the backgrounds as it loads, before it takes them),
 * their backgrounds are answered empty (RequestBlockingClient). Once the files are taken, a text
 * story there loads its backgrounds as usual. Create story on the feed isn't one of them.
 */
object StoryBackgrounds {
    private val sharing: MutableSet<WebView> = Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))

    /** The backgrounds answered empty since the app started (the tests and the live check read it). */
    internal val skipped = AtomicInteger()

    fun skipIn(webView: WebView) {
        sharing += webView
    }

    /** Whether [view]'s request for [url] is a background to skip; called off the main thread. */
    fun skips(view: WebView?, url: String): Boolean =
        view != null && view in sharing && ShareMediaQueue.pending(ShareTarget.STORY) && isStoryBackgroundUrl(url)
}
