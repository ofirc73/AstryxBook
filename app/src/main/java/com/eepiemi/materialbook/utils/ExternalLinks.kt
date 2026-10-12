package com.eepiemi.materialbook.utils

/** What happens to a link a page sends out of the app (see [externalLinkAction]). */
enum class ExternalLinkAction { OPEN, CONFIRM, BLOCK }

// Web, email and phone links open right away, as in a browser.
private val OPEN_SCHEMES = setOf("http", "https", "mailto", "tel")

// Never handed to another app: they'd read local data or run script, not open a page.
private val BLOCKED_SCHEMES = setOf("javascript", "file", "content", "data", "blob", "about")

private val SCHEME = Regex("""^([a-zA-Z][a-zA-Z0-9+.\-]*):""")

/**
 * Whether a link leaving the app opens directly, asks first, or is refused. Anything that
 * isn't a web, email or phone link (another app's deep link, `market:`, `sms:`…) asks the
 * user first, so a post or ad can't push them into another app with no tap of their own.
 * `intent:` links never get here: only their web fallback is opened (see [intentFallbackUrl]).
 */
fun externalLinkAction(url: String): ExternalLinkAction {
    val scheme = SCHEME.find(url.trim())?.groupValues?.get(1)?.lowercase()
        ?: return ExternalLinkAction.BLOCK
    return when (scheme) {
        in OPEN_SCHEMES -> ExternalLinkAction.OPEN
        in BLOCKED_SCHEMES, "intent" -> ExternalLinkAction.BLOCK
        else -> ExternalLinkAction.CONFIRM
    }
}

/** Whether [url] is a link into the Messenger app (`fb-messenger://…`). */
fun isMessengerAppLink(url: String): Boolean =
    SCHEME.find(url.trim())?.groupValues?.get(1)?.lowercase()?.startsWith("fb-messenger") == true

/** The scheme of a link, to name the kind of app it opens without showing the whole link. */
fun linkScheme(url: String): String = SCHEME.find(url.trim())?.groupValues?.get(1) ?: url
