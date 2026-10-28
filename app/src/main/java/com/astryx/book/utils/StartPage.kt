package com.astryx.book.utils
/** Facebook's Home feed, in the order Facebook picks. */
const val HOME_PAGE_URL = "https://facebook.com/"

/**
 * Facebook's Feeds (posts from friends, groups and Pages, most recent first). m.facebook.com has
 * no URL for it, only an entry in its menu, so this is the menu, marked for open_feeds.js to
 * press that entry.
 */
const val FEEDS_PAGE_URL = "https://m.facebook.com/bookmarks/#astryx-feeds"

/**
 * The page the app opens on: the link it was opened with, if any, else Feeds with "Start on
 * Feeds" (Settings) on, else Home.
 */
fun startPageUrl(linkUrl: String?, startOnFeeds: Boolean): String =
    linkUrl ?: if (startOnFeeds) FEEDS_PAGE_URL else HOME_PAGE_URL
