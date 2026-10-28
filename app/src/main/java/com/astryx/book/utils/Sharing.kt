package com.astryx.book.utils
import com.eepiemi.materialbook.utils.fbRedirectSanitizer
import java.net.URLEncoder

private val WEB_LINK = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
// Punctuation that ends a sentence rather than the link ("see https://example.com.").
private const val TRAILING_PUNCTUATION = ".,;:!?)]}'\""

private val MOBILE_FACEBOOK = Regex("""^(https?://)(?:m|mbasic|touch)\.facebook\.com(?=[/?#:]|$)""", RegexOption.IGNORE_CASE)

/**
 * Text shared into the app from another one (ACTION_SEND): Facebook's share dialog for the
 * first web link in it, or null when there's no link (Facebook's web share takes only links).
 */
fun facebookShareUrl(sharedText: String?): String? {
    val link = sharedText?.let { WEB_LINK.find(it)?.value?.trimEnd { c -> c in TRAILING_PUNCTUATION } }
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    return "https://m.facebook.com/sharer/sharer.php?u=" + URLEncoder.encode(link, "UTF-8")
}

/**
 * The page's link as the share sheet sends it: without trackers (see fbRedirectSanitizer),
 * and on www.facebook.com rather than the mobile site, so it opens well anywhere.
 */
fun shareablePageUrl(pageUrl: String): String =
    MOBILE_FACEBOOK.replace(fbRedirectSanitizer(pageUrl)) { "${it.groupValues[1]}www.facebook.com" }
