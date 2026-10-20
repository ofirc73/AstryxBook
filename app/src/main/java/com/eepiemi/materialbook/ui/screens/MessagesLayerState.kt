package com.eepiemi.materialbook.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.eepiemi.materialbook.utils.STORY_CREATE_URL
import com.eepiemi.materialbook.utils.ShareMediaQueue
import com.eepiemi.materialbook.utils.ShareTarget
import com.eepiemi.materialbook.utils.isMobileReelPage
import com.eepiemi.materialbook.utils.rendererRecoveryUrl

/**
 * Where MaterialbookWebView's Messages layer (MessagesLayer, a desktop WebView over the main one)
 * is: the page it's open at, the chat to go back to from a reel, and the state of a reel being
 * handed to the main view. Messages links open the layer, Facebook's own sections and a reel's page
 * close it (messagesLayerExit); see MaterialbookWebView for who sets what.
 */
@Stable
internal class MessagesLayerState(url: String?, returnUrl: String?) {
    /**
     * The page the layer opens at, or null while it's closed. Saved, so the layer comes back
     * after the activity is recreated.
     */
    var url by mutableStateOf(url)

    /** Bumped to recreate the layer at [url] (a new share, a lost renderer): part of its key. */
    var generation by mutableIntStateOf(0)

    /** The chat a reel was opened from (see messagesLayerExit): Back on that reel opens it again. Saved. */
    var returnUrl by mutableStateOf(returnUrl)

    /**
     * A reel opened from a chat loads in the main view while the chat stays on screen: closing the
     * layer at once showed the main view's feed for a second before the reel came (seen on a
     * Galaxy A56). The layer closes once the reel has loaded, or after LAYER_CLOSE_WAIT_MS.
     */
    var closeOnReel by mutableStateOf(false)

    /** When the layer's renderer was last lost, for rendererRecoveryUrl. */
    var lastRendererLossAt: Long? = null

    /**
     * The layer is kept, out of sight, behind the reel it left for: Back shows the same chat again,
     * where it was. Opened anew at the chat's address, an end-to-end encrypted chat landed on the
     * chat list instead (seen on a Galaxy A56).
     */
    var hidden by mutableStateOf(false)

    /** The layer exists (shown or hidden). */
    val isOpen: Boolean get() = url != null

    /** The layer is on screen. */
    val isShown: Boolean get() = url != null && !hidden

    /** Opens the layer at [url] (shows it, if it's the hidden one at that page). */
    fun open(url: String) {
        this.url = url
        hidden = false
    }

    /** Closes the layer, shown or hidden. */
    fun close() {
        url = null
        hidden = false
    }

    /**
     * Back in the main view, at [mainUrl]: on the reel a chat opened, opens that chat again and
     * returns true. Either way the chat is forgotten (Back on another page leaves it).
     */
    fun backToChat(mainUrl: String?): Boolean {
        val onReel = isMobileReelPage(mainUrl)
        if (hidden) {
            if (onReel) {
                hidden = false
                returnUrl = null
                return true
            }
            // The user went on from the reel: the chat kept behind it goes.
            close()
        }
        val chat = returnUrl
        returnUrl = null
        if (chat == null || !onReel) return false
        // The layer kept behind the reel is gone (the activity was recreated): the chat anew.
        open(chat)
        return true
    }

    /** The layer closed itself: the stories (or the reel) still waiting to be offered are given up. */
    fun onClosed() {
        if (ShareMediaQueue.pending(ShareTarget.STORY) || ShareMediaQueue.pending(ShareTarget.REEL)) {
            ShareMediaQueue.clear()
        }
        close()
    }

    /**
     * The layer's page left for the main view ([mainUrl], which [loadMain] opens there; null for
     * Home), from [fromUrl] (the last chat). A reel keeps the chat on screen until it has loaded
     * ([reelShown]); anything else closes the layer at once.
     */
    fun onLeave(mainUrl: String?, fromUrl: String?, loadMain: (String) -> Unit) {
        // A story was shared and Facebook moved on (to its feed): the next shared file gets the
        // story creator again instead.
        if (ShareMediaQueue.pending(ShareTarget.STORY)) {
            url = STORY_CREATE_URL
            generation++
            return
        }
        // Both the interceptor and the in-page watcher can report the same exit.
        if (url != null && !closeOnReel) {
            if (isMobileReelPage(mainUrl)) {
                returnUrl = fromUrl
                closeOnReel = true
            } else {
                close()
                returnUrl = null
            }
            mainUrl?.let(loadMain)
        }
    }

    /** The reel the layer left for is on screen in the main view: the layer goes out of sight. */
    fun reelShown() {
        closeOnReel = false
        hidden = true
    }

    /** The layer's renderer was lost at [pageUrl] ([layerUrl] it opened at): a new layer opens there. */
    fun onRendererGone(pageUrl: String?, layerUrl: String, nowMillis: Long) {
        url = rendererRecoveryUrl(pageUrl, layerUrl, lastRendererLossAt, nowMillis)
        lastRendererLossAt = nowMillis
        generation++
    }

    companion object {
        val Saver = listSaver<MessagesLayerState, String?>(
            // A hidden layer isn't brought back: Back opens its chat anew (returnUrl).
            save = { listOf(it.url.takeUnless { _ -> it.hidden }, it.returnUrl) },
            restore = { MessagesLayerState(url = it[0], returnUrl = it[1]) },
        )
    }
}

@Composable
internal fun rememberMessagesLayerState(): MessagesLayerState =
    rememberSaveable(saver = MessagesLayerState.Saver) { MessagesLayerState(url = null, returnUrl = null) }
