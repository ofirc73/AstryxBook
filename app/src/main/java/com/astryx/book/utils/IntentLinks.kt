package com.astryx.book.utils
import java.net.URLDecoder

/**
 * The web fallback of an Android `intent://` link (`S.browser_fallback_url`), if it has an
 * http(s) one. Facebook answers full loads of some pages (profiles, for one) with a redirect
 * to `intent://…;package=com.facebook.katana;S.browser_fallback_url=…`, and the fallback is
 * the same page on the web (marked `from_intent_redirect=1`, so it doesn't redirect again).
 */
fun intentFallbackUrl(url: String): String? {
    if (!url.startsWith("intent:", ignoreCase = true)) return null
    val raw = url.substringAfter("#Intent;", "")
        .split(';')
        .firstOrNull { it.startsWith("S.browser_fallback_url=") }
        ?.substringAfter('=')
        ?: return null
    val fallback = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
    return fallback.takeIf { it.startsWith("https://") || it.startsWith("http://") }
}

private val FACEBOOK_WEB_URL =
    Regex("""^https?://(?!(?:l|lm)\.)(?:[^/?#:@\\]+\.)?(?:facebook|messenger)\.com(?::\d+)?(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

/**
 * A Facebook/Messenger page the app shows itself (the host is facebook.com, messenger.com or
 * a subdomain); outbound l./lm.facebook.com redirects and everything else go to the system.
 * No user info (`https://facebook.com:443@other.example/` is other.example) and only a numeric port.
 */
fun isFacebookWebUrl(url: String): Boolean = FACEBOOK_WEB_URL.containsMatchIn(url)

private val FB_WATCH_URL = Regex("""^https://fb\.watch(?:[/?#]|$)""", RegexOption.IGNORE_CASE)

/**
 * The page to open for a link another app (or a launcher shortcut) sent: only Facebook pages
 * and fb.watch short links, the hosts in the manifest's link filter. The activity is exported,
 * so an explicit intent could carry any URL, and a URL loaded at start doesn't pass through the
 * request interceptor; anything else opens the home page instead.
 */
fun appLinkUrl(url: String?): String? =
    url?.takeIf { it.startsWith("https://", ignoreCase = true) && (isFacebookWebUrl(it) || FB_WATCH_URL.containsMatchIn(it)) }
