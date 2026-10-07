package com.eepiemi.materialbook.utils

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
    Regex("""^https?://(?!(?:l|lm)\.)(?:[^/?#:]+\.)?(?:facebook|messenger)\.com(?:[/?#:]|$)""", RegexOption.IGNORE_CASE)

/**
 * A Facebook/Messenger page the app shows itself (the host is facebook.com, messenger.com or
 * a subdomain); outbound l./lm.facebook.com redirects and everything else go to the system.
 */
fun isFacebookWebUrl(url: String): Boolean = FACEBOOK_WEB_URL.containsMatchIn(url)
