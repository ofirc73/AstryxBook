package com.astryx.book
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.astryx.book.notifications.Posting
import com.astryx.book.ui.screens.NativeShare
import com.astryx.book.ui.screens.NativeShareScreen
import com.astryx.book.ui.screens.ShareOpen
import com.astryx.book.ui.screens.renderedFiles
import com.astryx.book.utils.ShareMediaQueue
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.SharedFile
import com.astryx.book.utils.Song
import com.astryx.book.utils.StoryMusic
import com.astryx.book.ui.screens.MusicChoice
import com.astryx.book.ui.screens.MusicActions
import com.astryx.book.utils.endShare
import com.eepiemi.materialbook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Astryxbook"

/**
 * How long a share pressed on the native share screen may take to get Facebook's page posting
 * (the creator or composer loading, the photo going in), counted again from each story that starts.
 */
internal const val NATIVE_SHARE_SEND_MS = 120_000L

/**
 * The native share screen's side in MainActivity ([NativeShareScreen]): a story or feed share
 * shows it, with Facebook's page made ready under it (a story's creator loaded in the Messages
 * layer, its text-story backgrounds skipped). On Share the photos (texts drawn on them) go to that
 * page through the share queue as an auto share, so share_media.js presses Facebook's Share itself;
 * once the page is posting (Posting, its notification) the app goes back to the app the share came
 * from, as the Facebook app does. "Edit in Facebook" hands them over as before, into Facebook's own
 * editor; the close button and Back give the share up and go back there too.
 *
 * [request] asks MaterialbookWebView to open the share's page ([ShareMediaRequest], [ShareOpen]).
 */
internal class NativeShareController(
    private val activity: ComponentActivity,
    private val request: (target: ShareTarget, open: ShareOpen) -> Unit,
) {
    /** The share on the screen, or null when there's none. */
    var share: NativeShare?
        get() = Shared.share
        private set(value) {
            Shared.share = value
        }

    // Asked for before the press: once Facebook's page posts, the app is out of sight, and the
    // notifications are the only sign of how it went.
    private val notificationPermission = activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        share?.let { send(it) }
    }

    // Kept for the process, not the activity: one created again (font size, language) shows the
    // same share, and goes on waiting for one sent.
    private object Shared {
        var share by mutableStateOf<NativeShare?>(null)
        // The posting starts and failures counted when the files went to the page; [sent] counts
        // the auto shares handed over, for the wait in Content.
        var startsAtShare = 0
        var failuresAtShare = 0
        var sent by mutableIntStateOf(0)
    }

    companion object {
        /** Whether a share to [target] shows the native share screen (a story or the feed). */
        fun shows(target: ShareTarget): Boolean = target == ShareTarget.STORY || target == ShareTarget.FEED

        /** The share on the screen, whatever activity shows it. */
        val current: NativeShare? get() = Shared.share

        /** No share on the screen, whatever activity shows it (tests). */
        fun clear() {
            Shared.share = null
        }
    }

    /**
     * Shows the native share screen for [files] shared to [target], when it has one (a story or
     * the feed): true. Otherwise false, and the caller hands them to Facebook's page as before.
     */
    fun offer(target: ShareTarget, files: List<SharedFile>): Boolean {
        if (!shows(target)) return false
        share = NativeShare(target, files)
        StoryMusic.clear()
        // The story creator loads under the screen meanwhile; nothing's offered to it yet.
        if (target == ShareTarget.STORY) {
            ShareMediaQueue.prepare(target)
            request(target, ShareOpen.PRELOAD)
        }
        return true
    }

    /** Share (or Post) pressed: the notification permission asked for first, where it's missing. */
    fun onShare() {
        val share = share ?: return
        if (share.phase != NativeShare.Phase.EDITING) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            share.phase = NativeShare.Phase.SENDING
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            send(share)
        }
    }

    private fun send(share: NativeShare) {
        share.phase = NativeShare.Phase.SENDING
        hand(share, auto = true)
    }

    /** "Edit in Facebook": Facebook's own editor gets the photos (texts drawn on them), the screen goes. */
    fun onEditInFacebook() {
        val share = share ?: return
        // Failed, or the photo already in Facebook's editor (the music picker): the page is shown as
        // it is, for the user to finish there.
        if (share.phase == NativeShare.Phase.FAILED || share.staged != null) {
            if (share.staged != null) request(share.target, ShareOpen.REUSE)
            this.share = null
            return
        }
        hand(share, auto = false)
    }

    /**
     * The music picker (♪): Facebook adds music only to a story in its editor, so the photo (its
     * texts drawn on) goes there out of sight first, then its music panel is opened (StoryMusic).
     */
    fun onMusic() {
        val share = share ?: return
        if (share.staged != null) return
        share.musicReady = false
        activity.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { renderedFiles(share) }
            if (this@NativeShareController.share !== share) return@launch
            if (files == null) {
                Toast.makeText(activity, R.string.native_share_render_failed, Toast.LENGTH_LONG).show()
                share.musicReady = null
                return@launch
            }
            share.staged = files[0].name
            ShareMediaQueue.start(share.target, files, keepFiles = true)
            request(share.target, ShareOpen.STAGE)
            // Not opened: the picker says music isn't available now (Edit in Facebook is the way).
            share.musicReady = if (StoryMusic.open()) true else null
        }
    }

    fun onSearch(text: String) = music { StoryMusic.search(text.trim()) }
    fun onPlay(song: Song) = music { StoryMusic.play(song.id) }

    fun onChoose(song: Song) = music { share ->
        if (StoryMusic.choose(song.id)) share.music = MusicChoice(song)
    }

    // The clip start being moved there (one at a time: a new start cancels the last one's drags).
    private var startMove: Job? = null

    fun onStart(fraction: Float) {
        val share = share ?: return
        val choice = share.music ?: return
        share.music = choice.copy(startFraction = fraction, startMs = null)
        startMove?.cancel()
        startMove = activity.lifecycleScope.launch {
            val startMs = StoryMusic.setStart(fraction)
            share.music?.takeIf { it.song == choice.song }?.let { share.music = it.copy(startMs = startMs ?: it.startMs) }
        }
    }

    /** The picker closed: a chosen song kept (Facebook's Done); without one, the photo taken back. */
    fun onMusicClosed() {
        val share = share ?: return
        if (share.music == null) onRemoveMusic() else music { StoryMusic.done() }
    }

    /**
     * The music removed: Facebook's editor, with the photo in it, is closed and loaded again, out of
     * sight as at first, so the texts can change again.
     */
    fun onRemoveMusic() {
        val share = share ?: return
        share.music = null
        share.staged = null
        share.musicReady = null
        ShareMediaQueue.clear()
        request(share.target, ShareOpen.CLOSE)
        ShareMediaQueue.prepare(share.target)
        request(share.target, ShareOpen.PRELOAD)
    }

    // A music picker step, on the screen's share (gone: nothing).
    private fun music(step: suspend (NativeShare) -> Unit) {
        val share = share ?: return
        activity.lifecycleScope.launch { if (this@NativeShareController.share === share) step(share) }
    }

    /** Closed or Back: the share is given up, and the app goes back to the app it came from. */
    fun onCancel() {
        val share = share ?: return
        this.share = null
        endShare(activity)
        if (share.target == ShareTarget.STORY) request(share.target, ShareOpen.CLOSE)
        activity.moveTaskToBack(true)
    }

    // The photos drawn with their texts (off the main thread), then into the queue for the page. A
    // story already in Facebook's editor (the music picker) only has its Share pressed.
    private fun hand(share: NativeShare, auto: Boolean) {
        share.staged?.let { name ->
            Shared.startsAtShare = Posting.starts
            Shared.failuresAtShare = ShareMediaQueue.failures
            ShareMediaQueue.start(share.target, emptyList(), auto = true, pressOnly = name,
                altTexts = mapOf(name to share.altTexts[0]), aiLabel = share.aiLabel)
            request(share.target, ShareOpen.STAGE)
            Shared.sent++
            return
        }
        activity.lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) { renderedFiles(share) }
            if (this@NativeShareController.share !== share) return@launch
            if (files == null) {
                Log.w(TAG, "Share: a photo couldn't be drawn with its text")
                Toast.makeText(activity, R.string.native_share_render_failed, Toast.LENGTH_LONG).show()
                share.phase = NativeShare.Phase.EDITING
                return@launch
            }
            Shared.startsAtShare = Posting.starts
            Shared.failuresAtShare = ShareMediaQueue.failures
            ShareMediaQueue.start(
                share.target, files, auto = auto, caption = share.caption.trim().takeIf { auto },
                // By the name each file goes to the page under (a photo with texts is a new file).
                altTexts = files.zip(share.altTexts).associate { (file, alt) -> file.name to alt },
                aiLabel = share.aiLabel,
            )
            request(share.target, ShareOpen.REUSE)
            if (auto) Shared.sent++ else this@NativeShareController.share = null
        }
    }

    /** The screen, over the page, while there's a share on it. */
    @Composable
    fun Content(modifier: Modifier = Modifier) {
        val share = share ?: return
        NativeShareScreen(
            share = share,
            onShare = ::onShare,
            onEditInFacebook = ::onEditInFacebook,
            onCancel = ::onCancel,
            modifier = modifier,
            onMusic = ::onMusic,
            music = MusicActions(
                search = ::onSearch, play = ::onPlay, choose = ::onChoose, start = ::onStart,
                close = ::onMusicClosed, remove = ::onRemoveMusic,
            ),
        )
        // Sent: waits for Facebook's page to post each file (a story is one file; the feed's post
        // all of them), or to fail, or for NATIVE_SHARE_SEND_MS without either.
        LaunchedEffect(share, Shared.sent) {
            if (Shared.sent == 0 || share.phase != NativeShare.Phase.SENDING) return@LaunchedEffect
            val expected = if (share.target == ShareTarget.STORY) share.files.size else 1
            var started = 0
            while (started < expected) {
                val progress = withTimeoutOrNull(NATIVE_SHARE_SEND_MS) {
                    snapshotFlow { (Posting.starts - Shared.startsAtShare) to (ShareMediaQueue.failures - Shared.failuresAtShare) }
                        .first { (starts, failures) -> starts > started || failures > 0 }
                }
                if (progress == null || progress.second > 0) {
                    Log.w(TAG, "Share: Facebook's page didn't post (${if (progress == null) "timed out" else "failed"})")
                    share.phase = NativeShare.Phase.FAILED
                    return@LaunchedEffect
                }
                started = progress.first
            }
            Log.i(TAG, "Share: posting, back to the app the share came from")
            this@NativeShareController.share = null
            activity.moveTaskToBack(true)
        }
    }
}
