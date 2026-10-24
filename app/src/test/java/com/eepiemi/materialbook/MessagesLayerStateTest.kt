package com.eepiemi.materialbook

import com.eepiemi.materialbook.ui.screens.MainPlace
import com.eepiemi.materialbook.ui.screens.MessagesLayerState
import com.eepiemi.materialbook.utils.STORY_CREATE_URL
import com.eepiemi.materialbook.utils.ShareMediaQueue
import com.eepiemi.materialbook.utils.ShareTarget
import com.eepiemi.materialbook.utils.SharedFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

// Where the Messages layer goes: a reel handed to the main view, Back to the chat, a story share
// coming back to the creator, a lost renderer.
class MessagesLayerStateTest {

    private val chat = "https://www.facebook.com/messages/t/4567/"
    private val reel = "https://m.facebook.com/reel/1360854416127071/"
    private val loaded = mutableListOf<String>()

    @Before
    fun setUp() {
        ShareMediaQueue.clear()
        loaded.clear()
    }

    private fun openAt(url: String) = MessagesLayerState(url = url, returnUrl = null)

    @Test
    fun aReel_loadsInTheMainView_whileTheChatStaysOpen() {
        val layer = openAt(chat)
        layer.onLeave(reel, chat) { loaded += it }
        assertEquals(listOf(reel), loaded)
        assertTrue("the chat closed before the reel was there", layer.isOpen)
        assertTrue(layer.closeOnReel)

        layer.reelShown()
        assertTrue("the chat behind the reel was closed", layer.isOpen)
        assertFalse("the chat stayed on screen over the reel", layer.isShown)
        assertFalse(layer.closeOnReel)
        assertEquals(chat, layer.returnUrl)
    }

    @Test
    fun theSameExitReportedTwice_loadsTheReelOnce() {
        // The interceptor and messages_layer_nav.js can both report it.
        val layer = openAt(chat)
        layer.onLeave(reel, chat) { loaded += it }
        layer.onLeave(reel, chat) { loaded += it }
        assertEquals(listOf(reel), loaded)
    }

    @Test
    fun backOnTheReel_showsTheSameChatAgain_once() {
        val layer = openAt(chat)
        val generation = layer.generation
        layer.onLeave(reel, chat) { loaded += it }
        layer.reelShown()

        assertTrue(layer.backToChat(reel))
        assertTrue("the chat isn't on screen again", layer.isShown)
        // The same layer, not a new one at the chat's address (an encrypted chat landed on the list).
        assertEquals(chat, layer.url)
        assertEquals(generation, layer.generation)
        // The next Back, on the reel again, is the main view's own.
        layer.close()
        assertFalse(layer.backToChat(reel))
    }

    @Test
    fun backOnAnotherPage_closesTheChatKeptBehindTheReel() {
        val layer = openAt(chat)
        layer.onLeave(reel, chat) { loaded += it }
        layer.reelShown()

        assertFalse(layer.backToChat("https://m.facebook.com/profile.php?id=4"))
        assertFalse("the hidden chat was kept after the user went on", layer.isOpen)
        assertNull(layer.returnUrl)
        assertFalse(layer.backToChat(reel))
    }

    @Test
    fun afterTheActivityIsRecreated_backOnTheReel_opensTheChatAnew() {
        // A hidden layer isn't saved; its chat's address is.
        val layer = openAt(chat)
        layer.onLeave(reel, chat) { loaded += it }
        layer.reelShown()
        val saved = with(MessagesLayerState.Saver) { androidx.compose.runtime.saveable.SaverScope { true }.save(layer) }!!
        val restored = MessagesLayerState.Saver.restore(saved)!!
        assertFalse(restored.isOpen)

        assertTrue(restored.backToChat(reel))
        assertTrue(restored.isShown)
        assertEquals(chat, restored.url)
    }

    @Test
    fun openingMessagesAgain_showsTheHiddenChat() {
        val layer = openAt(chat)
        layer.onLeave(reel, chat) { loaded += it }
        layer.reelShown()
        layer.open(chat)
        assertTrue(layer.isShown)
    }

    @Test
    fun backOnTheReel_handsBackWhereTheMainViewWasBeforeIt() {
        val layer = openAt(chat)
        val feed = MainPlace(3, "https://m.facebook.com/")
        layer.onLeave(reel, chat, feed) { loaded += it }
        layer.reelShown()
        // MaterialbookWebView reads it before backToChat and takes the main view back there.
        assertEquals(feed, layer.mainBeforeReel)

        assertTrue(layer.backToChat(reel))
        assertNull("the main view's place outlived the Back that used it", layer.mainBeforeReel)
    }

    @Test
    fun aSectionOrAPage_keepsNoMainViewPlace() {
        val layer = openAt(chat)
        layer.onLeave("https://m.facebook.com/friends/", chat, MainPlace(3, "https://m.facebook.com/")) { loaded += it }
        assertNull(layer.mainBeforeReel)
    }

    @Test
    fun aSection_closesTheLayerAtOnce() {
        val layer = openAt(chat)
        layer.onLeave("https://m.facebook.com/friends/", chat) { loaded += it }
        assertFalse(layer.isOpen)
        assertNull("Back mustn't reopen the chat from a section", layer.returnUrl)
        assertEquals(listOf("https://m.facebook.com/friends/"), loaded)

        // Home: closed, nothing to load (the main view's feed is already there).
        val home = openAt(chat)
        home.onLeave(null, chat) { loaded += it }
        assertFalse(home.isOpen)
        assertEquals(1, loaded.size)
    }

    @Test
    fun aStoryShare_thatMovesOn_getsTheCreatorAgain() {
        ShareMediaQueue.start(ShareTarget.STORY, listOf(SharedFile(File("a.png"), "a.png", "image/png")))
        val layer = openAt(STORY_CREATE_URL)
        val before = layer.generation
        layer.onLeave(null, null) { loaded += it }
        assertEquals(STORY_CREATE_URL, layer.url)
        assertEquals(before + 1, layer.generation)
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun closingTheLayer_givesUpAStoryShare() {
        ShareMediaQueue.start(ShareTarget.STORY, listOf(SharedFile(File("a.png"), "a.png", "image/png")))
        val layer = openAt(STORY_CREATE_URL)
        layer.onClosed()
        assertFalse(layer.isOpen)
        assertFalse(ShareMediaQueue.pending(ShareTarget.STORY))
    }

    @Test
    fun aLostRenderer_reopensTheLayerAtItsPage_butNotInALoop() {
        val layer = openAt(chat)
        val other = "https://www.facebook.com/messages/t/99/"
        layer.onRendererGone(other, chat, nowMillis = 100_000)
        assertEquals(other, layer.url)
        assertEquals(1, layer.generation)
        // Lost again right away: back to where the layer opened, not the same page again.
        layer.onRendererGone(other, chat, nowMillis = 101_000)
        assertEquals(chat, layer.url)
        assertEquals(2, layer.generation)
    }
}
