package com.astryx.book.utils
import java.net.URI

/**
 * The mobile site's address for a desktop-site page the main view landed on in mobile mode, or
 * null when [url] isn't one (or is a desktop Messages page, which has none).
 *
 * m.facebook.com answers a desktop browser with a permanent redirect to www.facebook.com, and the
 * WebView caches it: after Desktop layout (or a Messages layer page) had loaded an m.facebook.com
 * address, the mobile main view followed the cached redirect to the desktop site, Home included
 * (seen on the emulator, 2026-10-30). `_rdr`, the redirect's own mark, is dropped.
 */
fun mobileSiteUrl(url: String?): String? {
    val uri = url?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (uri.scheme != "https" || uri.host?.lowercase() != "www.facebook.com") return null
    if (isDesktopMessagesUrl(url)) return null
    val query = uri.rawQuery?.split('&')?.filter { it.isNotEmpty() && it != "_rdr" && !it.startsWith("_rdr=") }
        ?.joinToString("&")?.ifEmpty { null }
    return "https://m.facebook.com" + (uri.rawPath ?: "/").ifEmpty { "/" } + (query?.let { "?$it" } ?: "") +
        (uri.rawFragment?.let { "#$it" } ?: "")
}
