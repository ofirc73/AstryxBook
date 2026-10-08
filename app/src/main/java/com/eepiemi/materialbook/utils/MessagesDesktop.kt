package com.eepiemi.materialbook.utils

import java.net.URI

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

/**
 * Leaving Messages for one of Facebook's sections (Home, Friends, Watch, the Reels tab, ...)
 * from inside the layer: that belongs in the main view, on the mobile site. Kept in the layer,
 * the desktop feed looked just like the main one, and reels opened from it played in the
 * desktop layout. Returns null for anything else, including pages opened from a chat (a reel,
 * a profile, a post), which stay in the layer.
 */
fun messagesLayerExit(url: String): MessagesLayerRoute.Leave? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val host = uri.host?.lowercase() ?: return null
    if ((scheme != "http" && scheme != "https") || !isFacebookHost(host)) return null
    val path = (uri.rawPath ?: "").trimEnd('/')
    val query = uri.rawQuery
    return when {
        path.isEmpty() || path == "/home.php" -> MessagesLayerRoute.Leave(null)
        path == "/watch" && query?.contains("v=") == true -> null
        path in SECTION_PATHS || path.startsWith("/friends/") ->
            MessagesLayerRoute.Leave("https://m.facebook.com$path/" + (query?.let { "?$it" } ?: ""))
        else -> null
    }
}

/** The desktop Messages page (the one loaded with the desktop user agent). */
fun isDesktopMessagesUrl(url: String): Boolean {
    val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
    return uri.host?.lowercase() == "www.facebook.com" && (uri.path ?: "").startsWith("/messages")
}

private val THREAD_PATH = Regex("^/messages/(?:e2ee/)?t/[^/]+")

/**
 * Desktop equivalent of any Messages/Messenger link, so deep links keep their conversation:
 * `facebook.com/messages/t/<id>`, `m.me/<name>`, `messenger.com/t/<id>` and
 * `fb-messenger://user/<id>` open that thread; anything else opens the inbox.
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
