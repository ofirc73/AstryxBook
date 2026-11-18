package com.astryx.book.utils
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Photos and videos shared into the app from another app's share sheet: the entries named like
 * the Facebook app's own (Your story, Feed, Set as profile picture, Your groups, Reels; the
 * ShareTo... aliases in the manifest). They go into Facebook's own composer, where the user writes
 * the caption and posts. Story and Feed show the app's own share screen first (NativeShareScreen),
 * whose Share has the page press Facebook's Share or Post for the user (ShareMediaQueue.auto);
 * nothing else is posted by the app. See share_media.js for how the page takes them. [key] is the
 * target's name in share_media.js and its bridge.
 *
 * - Story: the desktop site's story creator (m.facebook.com sends stories to the Facebook app),
 *   in the Messages layer's desktop WebView; it takes one photo or video at a time, so several
 *   are offered one story after another.
 * - Feed: m.facebook.com's composer, which takes photos only, several at once.
 * - Profile picture: the mobile profile's Upload photo, one photo; Facebook's preview, then Update.
 * - Group: the user picks one of their groups; its composer takes photos like the feed's.
 * - Reel: the desktop site's reel creator, in the layer like stories; one video.
 */
enum class ShareTarget(val key: String) {
    STORY("story"), FEED("feed"), PROFILE_PICTURE("profile"), GROUP("group"), REEL("reel");

    companion object {
        fun ofKey(key: String?): ShareTarget? = entries.firstOrNull { it.key == key }
    }
}

/** A shared file, copied into the app's cache so it outlives the sharing app's permission. */
data class SharedFile(val file: File, val name: String, val mimeType: String)

/** A shared item as the sharing app describes it, before it's copied. */
data class ShareCandidate(val name: String, val mimeType: String, val size: Long)

/** What a share asked for and what the app can hand on. */
data class ShareSelection(val accepted: List<ShareCandidate>, val skipped: Int)

/** The page Facebook creates stories on (desktop site; not a section that closes the layer). */
const val STORY_CREATE_URL = "https://www.facebook.com/stories/create"

/**
 * The mobile composer, opened directly (empty, "What's on your mind?"): it shows at once and the
 * shared photos go in through its Photos button. Through Home's Photo button the composer opened
 * only once Facebook had read the photos, seconds later. With Home already showing, the share
 * opens the composer from Home's own box instead ([isMobileHomePage]): loaded, the server draws
 * Home's whole feed before it.
 */
const val FEED_COMPOSER_PAGE_URL = "https://m.facebook.com/composer/"

/** Whether [url] is the mobile site's Home (the feed), whose composer box a feed share can tap. */
fun isMobileHomePage(url: String?): Boolean {
    val uri = url?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return false
    val path = uri.path ?: ""
    return uri.host?.lowercase() == "m.facebook.com" && (path.isEmpty() || path == "/" || path == "/home.php")
}

/** The user's own profile on the mobile site, where the profile picture is changed. */
const val PROFILE_PAGE_URL = "https://m.facebook.com/profile.php"

/** The user's groups on the mobile site, to pick the group a group share goes to. */
const val GROUPS_PAGE_URL = "https://m.facebook.com/groups/"

/** The desktop site's reels, whose Create reel opens the reel creator (redirects to /reel/). */
const val REEL_CREATE_URL = "https://www.facebook.com/reels/create"

/** The mobile page a share to [target] opens in the main view, or null (stories and reels open in the layer). */
fun shareStartPageUrl(target: ShareTarget): String? = when (target) {
    ShareTarget.FEED -> FEED_COMPOSER_PAGE_URL
    ShareTarget.PROFILE_PICTURE -> PROFILE_PAGE_URL
    ShareTarget.GROUP -> GROUPS_PAGE_URL
    ShareTarget.STORY, ShareTarget.REEL -> null
}

object ShareMedia {
    const val MAX_FEED_PHOTOS = 30
    const val MAX_STORY_ITEMS = 10
    const val MAX_PHOTO_BYTES = 40L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 250L * 1024 * 1024

    private val ALIASES = mapOf(
        ".ShareToStory" to ShareTarget.STORY,
        ".ShareToFeed" to ShareTarget.FEED,
        ".ShareToProfilePicture" to ShareTarget.PROFILE_PICTURE,
        ".ShareToGroup" to ShareTarget.GROUP,
        ".ShareToReel" to ShareTarget.REEL,
    )

    /** The target of a share that arrived through [componentClassName] (one of the aliases). */
    fun targetOf(componentClassName: String?): ShareTarget? {
        if (componentClassName == null) return null
        return ALIASES.entries.firstOrNull { componentClassName.endsWith(it.key) }?.value
    }

    /** How many items [target] takes from one share. */
    fun maxItems(target: ShareTarget): Int = when (target) {
        ShareTarget.FEED, ShareTarget.GROUP -> MAX_FEED_PHOTOS
        ShareTarget.STORY -> MAX_STORY_ITEMS
        ShareTarget.PROFILE_PICTURE, ShareTarget.REEL -> 1
    }

    /**
     * The items [target] takes, in order, within its count and size limits: stories take photos
     * and videos, reels a video, the others photos. A size of -1 (unknown) passes; the copy
     * enforces the limit then.
     */
    fun select(target: ShareTarget, candidates: List<ShareCandidate>): ShareSelection {
        val fitting = candidates.filter { fits(target, it.mimeType, it.size) }
        val accepted = fitting.take(maxItems(target))
        return ShareSelection(accepted, candidates.size - accepted.size)
    }

    /** Whether a file of [mimeType] and [size] bytes (-1: unknown) goes to [target]. */
    fun fits(target: ShareTarget, mimeType: String, size: Long): Boolean {
        val type = mimeType.lowercase()
        val photo = type.startsWith("image/") && target != ShareTarget.REEL
        val video = type.startsWith("video/") && (target == ShareTarget.STORY || target == ShareTarget.REEL)
        if (!photo && !video) return false
        val max = if (photo) MAX_PHOTO_BYTES else MAX_VIDEO_BYTES
        return size < 0 || size in 1..max
    }

    /** The largest a copy of [mimeType] may grow to. */
    fun maxBytes(mimeType: String): Long =
        if (mimeType.lowercase().startsWith("video/")) MAX_VIDEO_BYTES else MAX_PHOTO_BYTES

    /** A file name safe to keep in the cache and give the page: letters, digits, '.', '-', '_'. */
    fun safeName(name: String, index: Int, mimeType: String): String {
        val cleaned = name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
        if (cleaned.isNotEmpty() && cleaned.length <= 80) return "${index}_$cleaned"
        val extension = mimeType.substringAfter('/', "").substringBefore(';').filter { it.isLetterOrDigit() }
        return "${index}_shared" + if (extension.isNotEmpty()) ".$extension" else ""
    }
}

/**
 * The files of the share in progress, handed to the page one at a time (story) or all at once
 * (feed) through share_media.js. One share at a time: a new one replaces it.
 */
object ShareMediaQueue {
    /** How long a group share waits for the user to pick the group. */
    const val GROUP_PICK_MS = 10 * 60 * 1000L
    /** How long a feed or profile picture share keeps offering its files to the pages that load. */
    const val SHARE_PAGE_MS = 60 * 1000L

    private var target: ShareTarget? = null
    private var files: List<SharedFile> = emptyList()
    private var next = 0
    private var startedAt = 0L
    private var preparing = false

    /** The share in progress's number, telling share_media.js runs for two shares apart. */
    @get:Synchronized
    var id = 0
        private set

    /**
     * Whether the page presses Share (a story) or Post (the feed) itself once it has the files:
     * the user pressed Share on the native share screen (NativeShareScreen). Otherwise the user
     * does, in Facebook's own editor.
     */
    @get:Synchronized
    var auto = false
        private set

    /**
     * The live checks only: an auto share finds Facebook's Share or Post and presses nothing
     * (share_media.js window.__astryxShareNoPress). Never set by the app.
     */
    @Volatile
    var noPress = false

    /** The feed post's text, typed on the native share screen, for the composer (auto shares). */
    @get:Synchronized
    var caption: String? = null
        private set

    /**
     * A story's photos' alternative texts from the native share screen, by the name the page gets
     * each file under, and whether the story gets Facebook's AI label: share_media.js puts them into
     * Facebook's editor (storyExtras).
     */
    @get:Synchronized
    var altTexts: Map<String, String> = emptyMap()
        private set
    @get:Synchronized
    var aiLabel = false
        private set

    /**
     * A story already in Facebook's editor (the native music picker gave it, by this name): the auto
     * share only presses Share to story (share_media.js, window.__astryxSharePressOnly).
     */
    @get:Synchronized
    var pressOnly: String? = null
        private set

    /**
     * The share's files are kept when the page has taken them (the native music picker gave the
     * photo to Facebook's editor and may give it again): finished, only the queue is cleared.
     */
    @get:Synchronized
    var keepFiles = false
        private set

    /**
     * Counts every start, preparation and failure, for Compose: the story creator, loaded while
     * the share was being prepared, runs share_media.js once the files come.
     */
    var version by mutableIntStateOf(0)
        private set

    /** Counts the shares the page couldn't take (share_media.js "failed"), for the native screen. */
    var failures by mutableIntStateOf(0)
        private set

    @Synchronized
    fun start(
        newTarget: ShareTarget,
        newFiles: List<SharedFile>,
        now: Long = System.currentTimeMillis(),
        auto: Boolean = false,
        caption: String? = null,
        altTexts: Map<String, String> = emptyMap(),
        aiLabel: Boolean = false,
        pressOnly: String? = null,
        keepFiles: Boolean = false,
    ) {
        target = newTarget
        files = newFiles
        next = 0
        startedAt = now
        preparing = false
        this.auto = auto
        this.caption = caption?.takeIf { it.isNotBlank() }
        this.altTexts = altTexts.mapValues { it.value.trim() }.filterValues { it.isNotEmpty() }
        this.aiLabel = aiLabel
        this.pressOnly = pressOnly
        this.keepFiles = keepFiles
        id++
        version++
    }

    /**
     * A share to [newTarget] is on the native share screen, its files not ready yet: its page can
     * load meanwhile (the story creator, without its text-story backgrounds). Nothing is offered.
     */
    @Synchronized
    fun prepare(newTarget: ShareTarget, now: Long = System.currentTimeMillis()) {
        start(newTarget, emptyList(), now)
        preparing = true
    }

    /** A share to [forTarget] is being prepared on the native share screen ([prepare]). */
    @Synchronized
    fun preparing(forTarget: ShareTarget): Boolean = preparing && target == forTarget

    /** The page couldn't take the share's files ([failures]). */
    fun failed() {
        failures++
    }

    /** Whether the share in progress started more than [ms] ago. */
    @Synchronized
    fun olderThan(ms: Long, now: Long = System.currentTimeMillis()): Boolean = target != null && now - startedAt > ms

    @Synchronized
    fun pending(forTarget: ShareTarget): Boolean = target == forTarget && next < files.size

    /** The next file for [forTarget], or null when there's none left. */
    @Synchronized
    fun take(forTarget: ShareTarget): SharedFile? {
        if (target != forTarget || next >= files.size) return null
        return files[next++]
    }

    /** All the files left for [forTarget] (the feed's composer takes them at once). */
    @Synchronized
    fun takeAll(forTarget: ShareTarget): List<SharedFile> {
        if (target != forTarget) return emptyList()
        val rest = files.drop(next)
        next = files.size
        return rest
    }

    /** Some of [forTarget]'s files were given to a page already (a story share past its first). */
    @Synchronized
    fun started(forTarget: ShareTarget): Boolean = target == forTarget && next > 0

    /** How many of [forTarget]'s files no page has taken yet. */
    @Synchronized
    fun remaining(forTarget: ShareTarget): Int = if (target == forTarget) files.size - next else 0

    /**
     * The share is over, or given up (timed out, the layer closed): the copies no page took are
     * deleted. One a page took stays until that page is through with it ([endShare]).
     */
    @Synchronized
    fun clear() {
        files.drop(next).forEach { it.file.delete() }
        target = null
        files = emptyList()
        next = 0
        preparing = false
        auto = false
        caption = null
        altTexts = emptyMap()
        aiLabel = false
        pressOnly = null
        keepFiles = false
        version++
    }
}

/**
 * Whether [url] is the desktop site's reels, where a reel share's creator opens (reels/create
 * redirects to /reel/). The layer keeps them while it's open for a reel share; otherwise the
 * Reels tab belongs in the main view.
 */
fun isReelCreatePage(url: String?): Boolean {
    val uri = url?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return false
    val path = uri.path ?: ""
    return uri.host?.lowercase() == "www.facebook.com" &&
        (path == "/reel" || path.startsWith("/reel/") || path == "/reels" || path.startsWith("/reels/"))
}

/** Whether [url] is the desktop story creator, where a story share's files are offered. */
fun isStoryCreatePage(url: String?): Boolean {
    val uri = url?.let { runCatching { java.net.URI(it) }.getOrNull() } ?: return false
    return uri.host?.lowercase() == "www.facebook.com" && (uri.path ?: "").startsWith("/stories/create")
}
