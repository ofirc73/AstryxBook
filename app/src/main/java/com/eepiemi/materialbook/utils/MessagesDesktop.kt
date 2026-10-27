package com.eepiemi.materialbook.utils

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

const val MESSAGES_DESKTOP_URL = "https://www.facebook.com/messages/"

private fun hostAndPath(url: String): Pair<String, String>? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    return (uri.host ?: return null).lowercase() to (uri.path ?: "")
}

private fun isFacebookHost(host: String) =
    (host == "facebook.com" || host.endsWith(".facebook.com")) &&
        !host.startsWith("l.") && !host.startsWith("lm.")

/**
 * Every way Facebook's mobile site (m.facebook.com) tries to open Messages/Messenger: the facebook.com/messages
 * page, m.me and messenger.com links, and the fb-messenger:// and intent:// deep links
 * that start the Messenger app (none of which render usefully in the mobile web view).
 */
fun isMessagesLink(url: String): Boolean {
    val scheme = url.substringBefore(':', "").lowercase()
    if (scheme == "fb-messenger" || scheme == "fb-messenger-share") return true
    if (scheme == "intent") return Regex("package=[^;]*(orca|messenger|mlite)").containsMatchIn(url)
    val (host, path) = hostAndPath(url) ?: return false
    if (host == "m.me" || host.endsWith(".m.me")) return true
    if (host == "messenger.com" || host.endsWith(".messenger.com")) return true
    return isFacebookHost(host) && path.startsWith("/messages")
}

/** Where a main-frame navigation inside the Messages layer goes. */
sealed interface MessagesLayerRoute {
    /**
     * Stays in the layer as is: the desktop Messages page and any other Facebook page opened
     * from it (a shared reel, a profile), so Back returns to the chat.
     */
    data object Allow : MessagesLayerRoute

    /** Another form of a Messages link, or an app redirect: open its web page in the layer. */
    data class Remap(val url: String) : MessagesLayerRoute

    /** Not Facebook: hand it to the system, as the main view does. */
    data class External(val url: String) : MessagesLayerRoute

    /**
     * One of Facebook's own sections (see [messagesLayerExit]): close the layer and show it
     * in the main view, [mainUrl] there (null: the feed it already shows).
     */
    data class Leave(val mainUrl: String?) : MessagesLayerRoute
}

fun messagesLayerRoute(url: String, isMainFrame: Boolean): MessagesLayerRoute {
    if (!isMainFrame || isDesktopMessagesUrl(url)) return MessagesLayerRoute.Allow
    if (isMessagesLink(url)) return MessagesLayerRoute.Remap(messagesDesktopUrl(url))
    intentFallbackUrl(url)?.takeIf { isFacebookWebUrl(it) }?.let { return MessagesLayerRoute.Remap(it) }
    messagesLayerExit(url)?.let { return it }
    if (isFacebookWebUrl(url)) return MessagesLayerRoute.Allow
    return MessagesLayerRoute.External(url)
}

// Facebook's own sections, as linked from the desktop page's header and menus. Pages inside
// them (a group, a video, a marketplace item) can be shared in a chat, so only these exact
// paths count.
private val SECTION_PATHS = setOf(
    "/friends", "/groups", "/groups/feed", "/groups/discover", "/watch", "/marketplace",
    "/gaming", "/notifications", "/bookmarks", "/reel", "/reels", "/saved", "/memories"
)

// A reel's own page (www.facebook.com/reel/<id>), as a reel shared in a chat opens.
private val REEL_PAGE_PATH = Regex("^/reel/\\d+$")

// A reel's share link (/share/r/<id>).
private val SHARE_REEL_URL = Regex("^https?://(?:[a-z0-9-]+\\.)*facebook\\.com/share/r/", RegexOption.IGNORE_CASE)

/**
 * The reel behind a story's address: a reel's share link can come back as one
 * (story.php?story_fbid=<the reel's id>&id=<its owner>&share_url=<the /share/r/ link>, the web
 * fallback of an app link Facebook answered it with, for a reel posted as a video in a post; seen
 * on a Galaxy A56, 2026-10-25), which the mobile site shows as Home with the reel as a post. Its
 * own reel page instead, as a reel's share link that goes to /reel/<id> opens.
 */
private fun reelOfStory(path: String, query: String?): String? {
    if (path != "/story.php" && path != "/permalink.php") return null
    val params = query?.split('&')?.associate { it.substringBefore('=') to it.substringAfter('=', "") } ?: return null
    val shareUrl = params["share_url"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() } ?: return null
    if (!SHARE_REEL_URL.containsMatchIn(shareUrl)) return null
    return params["story_fbid"]?.takeIf { id -> id.isNotEmpty() && id.all { it.isDigit() } }
}

// A post shared in a chat: its share link (/share/p/<id>, as the chat links it) or its own page.
private val POST_PAGE_PATH = Regex("^/(?:share/p/[^/]+|[^/]+/posts/[^/]+|permalink\\.php|story\\.php)$")

/**
 * Leaving Messages for one of Facebook's sections (Home, Friends, Watch, the Reels tab, ...)
 * from inside the layer: that belongs in the main view, on the mobile site. Kept in the layer,
 * the desktop feed looked just like the main one, and reels opened from it played in the
 * desktop layout. A reel opened from a chat leaves too: the desktop reel page drew it small in
 * the layer's phone-wide layout, with no way to enlarge it, and it played without sound (seen on
 * a Galaxy A56); the mobile site's reel player shows it full size, with sound and PiP. A post
 * shared in a chat leaves the same way, so the chat is kept behind it like a reel's: opened in the
 * layer, Back reloaded the chat, which showed its latest messages and then jumped back to the
 * post. Facebook's stories page (www.facebook.com/stories, no story named, [isStoriesPage]) counts
 * as Home. After Share to story the creator moves there in-page (seen on a Galaxy A56, 2026-10-27):
 * the story screen takes the next shared file, or shows the new story on that page (MessagesLayer:
 * the feed's stories tray gets it only some 30 s later, and reloading the feed didn't make that
 * sooner, v1.35.22), and closes when that's left. Returns null for anything else, including other pages opened from a chat (a profile) and
 * a story's own page, which stay in the layer.
 */
fun messagesLayerExit(url: String): MessagesLayerRoute.Leave? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase() ?: return null
    if ((scheme != "http" && scheme != "https") || !isFacebookHost(host)) return null
    val path = (uri.rawPath ?: "").trimEnd('/')
    val query = uri.rawQuery
    return when {
        path.isEmpty() || path == "/home.php" || path == "/stories" -> MessagesLayerRoute.Leave(null)
        path == "/watch" && query?.contains("v=") == true -> null
        REEL_PAGE_PATH.matches(path) -> MessagesLayerRoute.Leave("https://m.facebook.com$path/")
        reelOfStory(path, query) != null -> MessagesLayerRoute.Leave("https://m.facebook.com/reel/${reelOfStory(path, query)}/")
        POST_PAGE_PATH.matches(path) -> MessagesLayerRoute.Leave(
            "https://m.facebook.com$path" + (if (path.endsWith(".php")) "" else "/") + (query?.let { "?$it" } ?: "")
        )
        path in SECTION_PATHS || path.startsWith("/friends/") ->
            MessagesLayerRoute.Leave("https://m.facebook.com$path/" + (query?.let { "?$it" } ?: ""))
        else -> null
    }
}

/** Facebook's stories page with no story named (www.facebook.com/stories), as Share to story moves to. */
fun isStoriesPage(url: String?): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    return isFacebookHost(uri.host?.lowercase() ?: return false) && (uri.rawPath ?: "").trimEnd('/') == "/stories"
}

/** A reel's page on the mobile site (m.facebook.com/reel/<id>), as [messagesLayerExit] opens one. */
fun isMobileReelPage(url: String?): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    return uri.host?.lowercase() == "m.facebook.com" && REEL_PAGE_PATH.matches((uri.rawPath ?: "").trimEnd('/'))
}

/**
 * A post's page on the mobile site, as [messagesLayerExit] opens one (m.facebook.com then shows it
 * as Home with the post at the top, at Home's address).
 */
fun isMobilePostPage(url: String?): Boolean {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return false
    return uri.host?.lowercase() == "m.facebook.com" && POST_PAGE_PATH.matches((uri.rawPath ?: "").trimEnd('/'))
}

/** The desktop Messages page (the one loaded with the desktop user agent). */
fun isDesktopMessagesUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    return uri.host?.lowercase() == "www.facebook.com" && (uri.path ?: "").startsWith("/messages")
}

private val THREAD_PATH = Regex("^/messages/(?:e2ee/)?t/[^/]+")

/**
 * The page a Messenger share link sends: m.facebook.com's "Send as a message" (a post's Share)
 * opens `intent://share/?link=<the post>…#Intent;scheme=fb-messenger;package=com.facebook.orca;end`
 * (seen on the emulator, 2026-10-27), `fb-messenger://share/?link=…` in the Messenger app's own
 * form. Null for any other link, or one whose `link` isn't a web page. The layer opens the chat
 * list for it, and the chat picked gets the page's link in its message box (MessagesLayerState
 * .linkToSend): the desktop site's own Send in Messenger dialog (`share_as_message`) sent an
 * attachment an end-to-end encrypted chat showed as an empty message, and the other side didn't
 * get at all (tried 2026-10-28).
 */
fun messengerShareLink(url: String): String? {
    if (!isMessagesLink(url)) return null
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "intent" && scheme != "fb-messenger" && scheme != "fb-messenger-share") return null
    if (uri.host?.lowercase() != "share") return null
    val link = uri.rawQuery?.split('&')?.firstOrNull { it.startsWith("link=") }?.substringAfter('=')
        ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() } ?: return null
    val linkScheme = runCatching { URI(link).scheme?.lowercase() }.getOrNull()
    return link.takeIf { linkScheme == "http" || linkScheme == "https" }
}

/**
 * The Messenger app's link for sending [url]'s page (see [messengerShareLink]), for the Open
 * Messenger choice: an intent:// link is never launched as it is.
 */
fun messengerShareAppLink(url: String): String? =
    messengerShareLink(url)?.let { "fb-messenger://share/?link=" + URLEncoder.encode(it, "UTF-8") }

/**
 * Desktop equivalent of any Messages/Messenger link, so deep links keep their conversation:
 * `facebook.com/messages/t/<id>`, `m.me/<name>`, `messenger.com/t/<id>` and
 * `fb-messenger://user/<id>` open that thread; anything else (a share link, [messengerShareLink],
 * included) opens the inbox.
 */
fun messagesDesktopUrl(url: String): String {
    val inbox = MESSAGES_DESKTOP_URL
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return inbox
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase() ?: ""
    val path = uri.path ?: ""
    fun thread(id: String) = if (id.isBlank()) inbox else "https://www.facebook.com/messages/t/$id"
    return when {
        scheme == "fb-messenger" && (host == "user" || host == "threads") ->
            thread(path.trim('/').substringBefore('/'))
        scheme != "http" && scheme != "https" -> inbox
        host == "m.me" || host.endsWith(".m.me") -> {
            val first = path.trim('/').substringBefore('/')
            // m.me/j/<code> is a group invite: no desktop equivalent
            if (first == "j") inbox else thread(first)
        }
        host == "messenger.com" || host.endsWith(".messenger.com") ->
            if (path.startsWith("/t/")) thread(path.removePrefix("/t/").substringBefore('/')) else inbox
        else -> THREAD_PATH.find(path)?.let { "https://www.facebook.com" + it.value } ?: inbox
    }
}
