package com.astryx.book.utils

import java.net.URI

/** One entry of the main WebView's history: its address now and the one it was loaded with. */
data class HistoryEntry(val url: String?, val originalUrl: String?)

/**
 * Where Back goes in the main view's history when Facebook bounces pages through `intent://`.
 *
 * A history entry WebView has to load again (after the app's process was restored, or one not
 * kept in the back-forward cache) is a full load, and m.facebook.com answers full loads of a reel
 * (and of other pages) with a redirect to its app (`intent://…`, see [intentFallbackUrl]). That
 * Back step is then cancelled, and the web fallback the app opens instead is a new entry on top:
 * the page Back meant to leave seemed to come back, and every further Back added another copy of
 * it (seen on a Galaxy A56, 2026-10-30: one reel 7 times over).
 *
 * So a Back step that gets bounced is remembered ([onIntentFallback]): the fallback's entry
 * stands for the entry Back was going to, and the next Back goes on from there. And Back from a
 * bounced reel goes past the earlier entries of the same reel, the copies left from before
 * (an app restarted with them in its restored history doesn't know which steps bounced).
 */
class BackHistory {
    private class PendingBack(val index: Int, val pageStarts: Int, val at: Long)

    private var pending: PendingBack? = null

    // A bounced Back step's fallback address (unique: Facebook adds a wtsid) -> the index of the
    // entry it stands for.
    private val bounced = mutableMapOf<String, Int>()

    /**
     * The index Back goes to from [current], or null when there's no earlier page. Remembered as
     * the Back step under way until a page starts ([pageStarts] moves on) or a few seconds pass.
     */
    fun backTarget(entries: List<HistoryEntry>, current: Int, pageStarts: Int, now: Long): Int? {
        if (current !in entries.indices) return null
        var from = current
        while (true) {
            val standsFor = entries[from].originalUrl?.let { bounced[it] }
            if (standsFor == null || standsFor !in 0 until from) break
            from = standsFor
        }
        var target = from - 1
        val reel = reelPath(entries[current])
        if (reel != null && isIntentFallback(entries[current].originalUrl)) {
            while (target >= 0 && reelPath(entries[target]) == reel) target--
        }
        if (target < 0) return null
        pending = PendingBack(target, pageStarts, now)
        return target
    }

    /** The main view opened [fallback] for a page Facebook sent to its app. */
    fun onIntentFallback(fallback: String, pageStarts: Int, now: Long) {
        val back = pending ?: return
        pending = null
        if (back.pageStarts == pageStarts && now - back.at <= PENDING_BACK_MS) bounced[fallback] = back.index
    }

    private fun reelPath(entry: HistoryEntry): String? =
        entry.url?.takeIf { isMobileReelPage(it) }
            ?.let { runCatching { URI(it).rawPath?.trimEnd('/') }.getOrNull() }

    private fun isIntentFallback(url: String?): Boolean =
        url != null && runCatching { URI(url).rawQuery }.getOrNull()
            ?.split('&')?.contains("from_intent_redirect=1") == true

    private companion object {
        // Facebook's redirect comes within a second of Back (0.7 s on the phone).
        const val PENDING_BACK_MS = 5_000L
    }
}
