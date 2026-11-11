package com.astryx.book.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale

// Facebook pages the app fetches itself, outside the WebView, with the WebView's cookies (the
// Facebook login): the notification counts (FacebookCountsWorker) and a memory photo's post
// (PhotoPost).

private const val TAG = "Astryxbook"
private const val TIMEOUT_MS = 20_000
private const val MAX_REDIRECTS = 5
private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

/**
 * The headers a browser sends when it opens a page, with the login's [cookie]: with only a few of
 * them, Facebook answers 400. [mobile]: as the app's WebView on the phone, else as a desktop
 * browser.
 */
fun facebookPageHeaders(cookie: String, userAgent: String, mobile: Boolean): Map<String, String> = mapOf(
    "Cookie" to cookie,
    "User-Agent" to userAgent,
    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language" to Locale.getDefault().toLanguageTag(),
    "Upgrade-Insecure-Requests" to "1",
    "Sec-Fetch-Dest" to "document",
    "Sec-Fetch-Mode" to "navigate",
    "Sec-Fetch-Site" to "none",
    "Sec-Fetch-User" to "?1",
    "Sec-CH-UA-Mobile" to if (mobile) "?1" else "?0",
    "Sec-CH-UA-Platform" to if (mobile) "\"Android\"" else "\"Windows\"",
)

/**
 * Where a fetch that carries the Facebook login may follow a redirect from [from] to [location]
 * (the answer's Location header, which may be relative): only to facebook.com's own https pages,
 * the site the cookies are for. Null for anywhere else, and the fetch ends there.
 */
fun facebookRedirectUrl(from: String, location: String?): String? {
    if (location.isNullOrBlank()) return null
    val target = runCatching { URI(from).resolve(location.trim()) }.getOrNull() ?: return null
    val host = target.host?.lowercase() ?: return null
    if (target.scheme?.lowercase() != "https" || target.userInfo != null) return null
    if (host != "facebook.com" && !host.endsWith(".facebook.com")) return null
    return target.toString()
}

/**
 * Opens [url] with [headers] and returns the connection of its answer, redirects followed (the
 * caller reads it and disconnects it), or null when Facebook sent the fetch somewhere it doesn't
 * follow. The redirects are followed here, one by one, not by HttpURLConnection: it sends the
 * request's headers on to whichever site the redirect names, the login's cookies with them.
 */
fun openFacebookPage(url: String, headers: Map<String, String>): HttpURLConnection? {
    var address = url
    repeat(MAX_REDIRECTS + 1) {
        val connection = URL(address).openConnection() as HttpURLConnection
        val next = try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = false
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (connection.responseCode !in REDIRECT_CODES) return connection
            facebookRedirectUrl(address, connection.getHeaderField("Location"))
        } catch (e: Exception) {
            connection.disconnect()
            throw e
        }
        connection.disconnect()
        if (next == null) {
            Log.w(TAG, "Fetch: a redirect off Facebook's pages wasn't followed")
            return null
        }
        address = next
    }
    Log.w(TAG, "Fetch: too many redirects")
    return null
}
