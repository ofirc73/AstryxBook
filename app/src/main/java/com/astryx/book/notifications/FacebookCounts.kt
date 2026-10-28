package com.astryx.book.notifications
/**
 * The unread counters on m.facebook.com's tab bar that "Facebook notifications" (Settings) turns
 * into Android notifications, with the page a tap opens. Facebook labels its tabs in English in
 * every language ("notifications, 3 new, 4 of 5"), in the HTML its server sends, so the counts
 * can be read without a WebView.
 */
enum class FacebookCounter(val tab: String, val url: String, val notificationId: Int) {
    NOTIFICATIONS("notifications", "https://m.facebook.com/notifications/", 2001),
    MESSAGES("messages", "https://m.facebook.com/messages/", 2002),
    FRIEND_REQUESTS("friends", "https://m.facebook.com/friends/", 2003),
}

/** An unread count; [capped] when Facebook shows it as "99+". */
data class UnreadCount(val count: Int, val capped: Boolean = false) {
    val label: String get() = if (capped) "$count+" else count.toString()
}

/** What to do with one counter's Android notification after a check. */
sealed interface CountAction {
    /** Post (or replace) it with an alert: there's more unread than last time. */
    data class Notify(val unread: UnreadCount) : CountAction
    /** Some were read elsewhere but not all: update it quietly if it's showing. */
    data class Update(val unread: UnreadCount) : CountAction
    /** All read: remove it. */
    data object Clear : CountAction
    data object Nothing : CountAction
}

object FacebookCounts {

    // A tab's aria-label: "notifications, 3 new, 4 of 5", with Left-to-Right Marks around it.
    // A tab without unread items has no "N new".
    private const val LABEL = """[\u200E\u200F]?([a-z]+), (?:(\d+)(\+)? new, )?\d+ of \d+[\u200E\u200F]?"""
    private val TAB_LABEL_IN_HTML = Regex("""aria-label="$LABEL"""")
    private val TAB_LABEL = Regex("^$LABEL$")

    /** JavaScript that lists the tab bar's labels on the page that's open, as a JSON array. */
    const val TAB_LABELS_JS =
        "Array.prototype.map.call(document.querySelectorAll('[role=\"tab\"][aria-label]'), " +
            "function (tab) { return tab.getAttribute('aria-label'); })"

    /**
     * The counts on the tab bar of [html] (an m.facebook.com page), or null if it has no tab bar
     * (logged out: the login page; an error page), so nothing is known.
     */
    fun parse(html: String): Map<FacebookCounter, UnreadCount>? = counts(TAB_LABEL_IN_HTML.findAll(html))

    /** The counts in the tab bar's [labels] (from [TAB_LABELS_JS]), or null if there's none. */
    fun parseLabels(labels: List<String>): Map<FacebookCounter, UnreadCount>? =
        counts(labels.mapNotNull { TAB_LABEL.find(it.trim()) }.asSequence())

    private fun counts(matches: Sequence<MatchResult>): Map<FacebookCounter, UnreadCount>? {
        val tabs = matches.associate { match ->
            val (tab, count, plus) = match.destructured
            tab to UnreadCount(count.toIntOrNull() ?: 0, plus.isNotEmpty())
        }
        if (FacebookCounter.entries.none { it.tab in tabs }) return null
        return FacebookCounter.entries.associateWith { tabs[it.tab] ?: UnreadCount(0) }
    }

    // A notification on m.facebook.com/notifications/: a box with data-tracking-duration-id holding
    // a button whose aria-label is the notification's text followed by its date and read state
    // ("… October 8 at 12:13 PM Unread", in Facebook's language), then its options button, whose
    // label quotes the text alone ("…titled "…"", the quotes depending on the language).
    private val ITEM_START = Regex("""data-tracking-duration-id="\d+"""")
    private val ARIA_LABEL = Regex("""aria-label="([^"]*)"""")
    private val BIDI_MARKS = Regex("[\u200E\u200F\u202A-\u202E\u2066-\u2069]")

    /**
     * The texts of the newest [limit] notifications in [html] (m.facebook.com/notifications/,
     * newest first), without their dates. Empty if the page has none or isn't laid out as
     * expected; a notification's text then stays out of the Android notification, not the count.
     */
    fun parseNotificationTexts(html: String, limit: Int): List<String> {
        if (limit <= 0) return emptyList()
        val starts = ITEM_START.findAll(html).map { it.range.first }.toList()
        return starts.asSequence()
            .mapIndexedNotNull { i, start ->
                val end = starts.getOrNull(i + 1) ?: html.length
                val labels = ARIA_LABEL.findAll(html.substring(start, end)).map { decodeHtml(it.groupValues[1]) }.toList()
                labels.firstOrNull()?.let { label -> textOf(label, labels.getOrNull(1)) }
            }
            .filter { it.isNotBlank() }
            .take(limit)
            .toList()
    }

    // The longest start of the item's label that the options label quotes: the text without the
    // date and read state, in any language. Without an options label, the whole label.
    private fun textOf(label: String, optionsLabel: String?): String {
        val text = label.replace(BIDI_MARKS, "").trim()
        val options = optionsLabel?.replace(BIDI_MARKS, "") ?: return text
        var end = text.length
        while (end > 0 && !options.contains(text.substring(0, end))) end--
        return (if (end >= MIN_QUOTED_TEXT) text.substring(0, end) else text).trim()
    }

    private const val MIN_QUOTED_TEXT = 8

    private val HTML_ENTITY = Regex("&(#x[0-9a-fA-F]+|#\\d+|quot|amp|lt|gt|apos);")

    private fun decodeHtml(text: String): String = HTML_ENTITY.replace(text) { match ->
        when (val name = match.groupValues[1]) {
            "quot" -> "\""
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "apos" -> "'"
            else -> {
                val code = if (name.startsWith("#x")) name.drop(2).toIntOrNull(16) else name.drop(1).toIntOrNull()
                code?.let { String(Character.toChars(it)) } ?: match.value
            }
        }
    }

    /** What a check that found [now], where the last one found [before], means for the user. */
    fun action(before: UnreadCount?, now: UnreadCount): CountAction = when {
        now.count == 0 -> if ((before?.count ?: 0) > 0) CountAction.Clear else CountAction.Nothing
        before == null || now.count > before.count || (now.capped && !before.capped) ->
            CountAction.Notify(now)
        now == before -> CountAction.Nothing
        else -> CountAction.Update(now)
    }
}
