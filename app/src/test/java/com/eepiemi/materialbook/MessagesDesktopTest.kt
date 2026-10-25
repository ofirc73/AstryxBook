package com.eepiemi.materialbook

import com.eepiemi.materialbook.utils.MessagesLayerRoute
import com.eepiemi.materialbook.utils.intentFallbackUrl
import com.eepiemi.materialbook.utils.isDesktopMessagesUrl
import com.eepiemi.materialbook.utils.isFacebookWebUrl
import com.eepiemi.materialbook.utils.isMessagesLink
import com.eepiemi.materialbook.utils.isMobilePostPage
import com.eepiemi.materialbook.utils.isMobileReelPage
import com.eepiemi.materialbook.utils.messagesDesktopUrl
import com.eepiemi.materialbook.utils.messagesLayerExit
import com.eepiemi.materialbook.utils.messagesLayerRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// What m.facebook.com answers a full profile load with (Facebook app installed or not).
private const val PROFILE_APP_REDIRECT =
    "intent://profile/4?wtsid=x#Intent;scheme=fb;package=com.facebook.katana;" +
        "S.browser_fallback_url=https%3A%2F%2Fm.facebook.com%2F4%2F%3Fwtsid%3Dx%26from_intent_redirect%3D1;end"

class MessagesDesktopTest {
    @Test
    fun intentLinkFallbacks() {
        assertEquals("https://m.facebook.com/4/?wtsid=x&from_intent_redirect=1", intentFallbackUrl(PROFILE_APP_REDIRECT))
        assertNull(intentFallbackUrl("intent://x#Intent;scheme=fb;package=com.facebook.katana;end"))
        assertNull(intentFallbackUrl("intent://x#Intent;S.browser_fallback_url=javascript%3Aalert(1);end"))
        assertNull(intentFallbackUrl("https://m.facebook.com/4/"))
        assertTrue(isFacebookWebUrl("https://m.facebook.com/4/"))
        assertTrue(isFacebookWebUrl("https://www.messenger.com"))
        assertFalse(isFacebookWebUrl("https://l.facebook.com/l.php?u=x"))
        assertFalse(isFacebookWebUrl("https://facebook.com.evil.example/"))
        assertFalse(isFacebookWebUrl("https://evilfacebook.com/"))
        assertFalse(isFacebookWebUrl("https://evil.example/?next=https://m.facebook.com/"))
    }

    @Test
    fun messagesLinks() {
        assertTrue(isMessagesLink("https://m.facebook.com/messages/"))
        assertTrue(isMessagesLink("https://www.facebook.com/messages/t/123"))
        assertTrue(isMessagesLink("https://m.me/someone"))
        assertTrue(isMessagesLink("https://www.messenger.com/"))
        assertTrue(isMessagesLink("fb-messenger://threads"))
        assertTrue(isMessagesLink("intent://x#Intent;scheme=fb-messenger;package=com.facebook.orca;end"))
        assertFalse(isMessagesLink("https://m.facebook.com/home.php"))
        assertFalse(isMessagesLink("https://l.facebook.com/messages"))
        assertFalse(isMessagesLink("https://example.com/messages"))
    }

    @Test
    fun layerRoutes() {
        val inbox = "https://www.facebook.com/messages/"
        // The desktop Messages page itself, and what it may bounce through, stay in the layer.
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/messages/e2ee/t/1", true))
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/login/?next=x", true))
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/checkpoint/1", true))
        // Subframes are never routed.
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.fbsbx.com/maw_proxy_page/", false))
        // Other forms of a Messages link open their desktop equivalent in the layer.
        assertEquals(MessagesLayerRoute.Remap("https://www.facebook.com/messages/t/someone"), messagesLayerRoute("https://m.me/someone", true))
        assertEquals(MessagesLayerRoute.Remap(inbox), messagesLayerRoute("https://m.facebook.com/messages/", true))
        // What the m.facebook.com Messages tab actually sends.
        assertEquals(MessagesLayerRoute.Remap(inbox), messagesLayerRoute("fb-messenger://threads?vcuid=1&entry_point=jewel", true))
        // Other Facebook pages opened from a chat stay in the layer, so Back returns to the chat.
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/share/r/1AbCdEf/?mibextid=x", true))
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/profile.php?id=1", true))
        // Facebook's redirect to its app opens the web fallback in the layer.
        assertEquals(
            MessagesLayerRoute.Remap("https://m.facebook.com/4/?wtsid=x&from_intent_redirect=1"),
            messagesLayerRoute(PROFILE_APP_REDIRECT, true)
        )
        // Everything else is external, outbound l.facebook.com redirects included.
        assertEquals(MessagesLayerRoute.External("https://example.com/"), messagesLayerRoute("https://example.com/", true))
        assertEquals(MessagesLayerRoute.External("https://l.facebook.com/l.php?u=x"), messagesLayerRoute("https://l.facebook.com/l.php?u=x", true))
        assertEquals(MessagesLayerRoute.External("tel:123"), messagesLayerRoute("tel:123", true))
    }

    @Test
    fun leavingForAFacebookSectionClosesTheLayer() {
        // Home (the logo, the Home button) goes back to the main view's own feed.
        assertEquals(MessagesLayerRoute.Leave(null), messagesLayerRoute("https://www.facebook.com/", true))
        assertEquals(MessagesLayerRoute.Leave(null), messagesLayerRoute("https://www.facebook.com/?sk=h_chr", true))
        assertEquals(MessagesLayerRoute.Leave(null), messagesLayerRoute("https://www.facebook.com/home.php", true))
        // Other sections open their mobile page in the main view.
        assertEquals(MessagesLayerRoute.Leave("https://m.facebook.com/friends/"), messagesLayerExit("https://www.facebook.com/friends/"))
        assertEquals(MessagesLayerRoute.Leave("https://m.facebook.com/friends/requests/"), messagesLayerExit("https://www.facebook.com/friends/requests"))
        assertEquals(MessagesLayerRoute.Leave("https://m.facebook.com/groups/feed/"), messagesLayerExit("https://www.facebook.com/groups/feed/"))
        assertEquals(MessagesLayerRoute.Leave("https://m.facebook.com/reel/?s=tab"), messagesLayerExit("https://www.facebook.com/reel/?s=tab"))
        assertEquals(MessagesLayerRoute.Leave("https://m.facebook.com/watch/"), messagesLayerExit("https://www.facebook.com/watch/"))
        // What a chat links to stays in the layer: a video, a group, a profile (a post leaves,
        // aPostSharedInAChat_opensInTheMainView).
        assertNull(messagesLayerExit("https://www.facebook.com/watch/?v=123"))
        assertNull(messagesLayerExit("https://www.facebook.com/groups/somegroup/"))
        assertNull(messagesLayerExit("https://www.facebook.com/marketplace/item/1/"))
        assertNull(messagesLayerExit("https://www.facebook.com/profile.php?id=1"))
        assertNull(messagesLayerExit("https://www.facebook.com/groups/1/posts/2/"))
        // Messages itself, and anything that isn't Facebook, aren't exits.
        assertNull(messagesLayerExit("https://www.facebook.com/messages/t/1"))
        assertNull(messagesLayerExit("https://example.com/"))
        assertNull(messagesLayerExit("https://l.facebook.com/l.php?u=x"))
    }

    @Test
    fun aPostSharedInAChat_opensInTheMainView() {
        // Opened in the layer, Back reloaded the chat at its latest messages and then jumped back
        // to the post: like a reel's, the chat is kept behind it instead.
        val share = "https://www.facebook.com/share/p/14zVaXX6eBS/"
        assertEquals(MessagesLayerRoute.Leave("https://m.facebook.com/share/p/14zVaXX6eBS/"), messagesLayerRoute(share, true))
        assertEquals(
            MessagesLayerRoute.Leave("https://m.facebook.com/YechielRiva/posts/pfbid0kSa8/"),
            messagesLayerExit("https://www.facebook.com/YechielRiva/posts/pfbid0kSa8?rdid=x".substringBefore('?'))
        )
        assertEquals(
            MessagesLayerRoute.Leave("https://m.facebook.com/permalink.php?story_fbid=1&id=2"),
            messagesLayerExit("https://www.facebook.com/permalink.php?story_fbid=1&id=2")
        )
        assertEquals(
            MessagesLayerRoute.Leave("https://m.facebook.com/story.php?story_fbid=1&id=2"),
            messagesLayerExit("https://www.facebook.com/story.php?story_fbid=1&id=2")
        )
        // The pages it opens are the ones Back returns to the chat from.
        assertTrue(isMobilePostPage("https://m.facebook.com/share/p/14zVaXX6eBS/"))
        assertTrue(isMobilePostPage("https://m.facebook.com/YechielRiva/posts/pfbid0kSa8/"))
        assertTrue(isMobilePostPage("https://m.facebook.com/permalink.php?story_fbid=1&id=2"))
        assertFalse(isMobilePostPage("https://www.facebook.com/share/p/14zVaXX6eBS/"))
        assertFalse(isMobilePostPage("https://m.facebook.com/"))
        assertFalse(isMobilePostPage("https://m.facebook.com/reel/1360854416127071/"))
        assertFalse(isMobilePostPage(null))
    }

    @Test
    fun aReelWhoseShareLinkComesBackAsAStory_opensItsReelPage() {
        // A reel posted as a video in a post: its share link came back as an app link whose web
        // fallback is the story's address (a Galaxy A56, 2026-10-25). Shown as Home with the reel
        // as a post until then.
        val story = "https://m.facebook.com/story.php?story_fbid=1427052772729171&id=61594936681030" +
            "&mibextid=wwXIfr&rdid=tn6coCA8vWOJ5T3p&share_url=https%3A%2F%2Fwww.facebook.com%2Fshare%2Fr%2F19ZswCuYt5" +
            "%2F%3Fmibextid%3DwwXIfr&wtsid=rdr_0O3twjCOepRu9kjaq&from_intent_redirect=1"
        val reel = MessagesLayerRoute.Leave("https://m.facebook.com/reel/1427052772729171/")
        assertEquals(reel, messagesLayerExit(story))
        assertEquals(reel, messagesLayerRoute(story, true))
        // The app link itself first opens its web fallback in the layer (which then leaves as above).
        val intent = "intent://story.php?story_fbid=1427052772729171#Intent;scheme=fb;package=com.facebook.katana;" +
            "S.browser_fallback_url=" + java.net.URLEncoder.encode(story, "UTF-8") + ";end"
        assertEquals(MessagesLayerRoute.Remap(story), messagesLayerRoute(intent, true))
        // A story that isn't a reel's (no reel share link behind it) is a post.
        assertEquals(
            MessagesLayerRoute.Leave("https://m.facebook.com/story.php?story_fbid=1&id=2&share_url=https%3A%2F%2Fwww.facebook.com%2Fshare%2Fp%2F1%2F"),
            messagesLayerExit("https://www.facebook.com/story.php?story_fbid=1&id=2&share_url=https%3A%2F%2Fwww.facebook.com%2Fshare%2Fp%2F1%2F")
        )
        assertEquals(
            MessagesLayerRoute.Leave("https://m.facebook.com/story.php?story_fbid=1&id=2"),
            messagesLayerExit("https://www.facebook.com/story.php?story_fbid=1&id=2")
        )
    }

    @Test
    fun aReelSharedInAChat_opensInTheMainViewsReelPlayer() {
        // The desktop reel page drew it small, with no way to enlarge it, and silent.
        val reel = "https://www.facebook.com/reel/1360854416127071"
        val mobileReel = "https://m.facebook.com/reel/1360854416127071/"
        assertEquals(MessagesLayerRoute.Leave(mobileReel), messagesLayerExit(reel))
        assertEquals(MessagesLayerRoute.Leave(mobileReel), messagesLayerExit("$reel/"))
        assertEquals(MessagesLayerRoute.Leave(mobileReel), messagesLayerExit("$reel/?s=chat&fs=e"))
        // A full load of it (the request interceptor) and an in-page move to it alike.
        assertEquals(MessagesLayerRoute.Leave(mobileReel), messagesLayerRoute(reel, true))
        // Facebook's share link for it still opens in the layer first, then redirects to the reel.
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute("https://www.facebook.com/share/r/1AbCdEf/", true))
        // Not a reel's own page: the Reels tab (a section), and things that only look like one.
        assertEquals(MessagesLayerRoute.Leave("https://m.facebook.com/reel/"), messagesLayerExit("https://www.facebook.com/reel/"))
        assertNull(messagesLayerExit("https://www.facebook.com/reel/abc"))
        assertNull(messagesLayerExit("https://www.facebook.com/reel/123/comments"))
    }

    @Test
    fun theMobileReelPage_isWhatBackReturnsToTheChatFrom() {
        assertTrue(isMobileReelPage("https://m.facebook.com/reel/1360854416127071/"))
        assertTrue(isMobileReelPage("https://m.facebook.com/reel/1360854416127071"))
        assertTrue(isMobileReelPage("https://m.facebook.com/reel/1360854416127071/?referral_source=x"))
        assertFalse(isMobileReelPage("https://www.facebook.com/reel/1360854416127071"))
        assertFalse(isMobileReelPage("https://m.facebook.com/reel/"))
        assertFalse(isMobileReelPage("https://m.facebook.com/reels/"))
        assertFalse(isMobileReelPage("https://m.facebook.com/profile.php?id=1"))
        assertFalse(isMobileReelPage(null))
    }

    @Test
    fun storyViewerAfterPostingStaysInTheLayer() {
        // Facebook lands on the posted story after sharing. That page stays above the app's
        // existing main page; only an explicit navigation to Home closes the sharing layer.
        val postedStory = "https://www.facebook.com/stories/123456789012345/"
        assertEquals(MessagesLayerRoute.Allow, messagesLayerRoute(postedStory, true))
        assertNull(messagesLayerExit(postedStory))
        assertEquals(
            MessagesLayerRoute.Leave(null),
            messagesLayerRoute("https://www.facebook.com/", true)
        )
    }

    @Test
    fun deepLinksKeepTheirConversation() {
        val inbox = "https://www.facebook.com/messages/"
        assertEquals("https://www.facebook.com/messages/t/12345", messagesDesktopUrl("https://www.facebook.com/messages/t/12345"))
        assertEquals("https://www.facebook.com/messages/t/12345", messagesDesktopUrl("https://m.facebook.com/messages/t/12345/?ref=notif"))
        assertEquals("https://www.facebook.com/messages/e2ee/t/777", messagesDesktopUrl("https://www.facebook.com/messages/e2ee/t/777/"))
        assertEquals("https://www.facebook.com/messages/t/someone", messagesDesktopUrl("https://m.me/someone"))
        assertEquals("https://www.facebook.com/messages/t/987", messagesDesktopUrl("https://www.messenger.com/t/987/"))
        assertEquals("https://www.facebook.com/messages/t/42", messagesDesktopUrl("fb-messenger://user/42"))
        assertEquals(inbox, messagesDesktopUrl("https://m.me/j/abcdef"))
        assertEquals(inbox, messagesDesktopUrl("https://m.me/"))
        assertEquals(inbox, messagesDesktopUrl("https://m.facebook.com/messages/"))
        assertEquals(inbox, messagesDesktopUrl("fb-messenger://threads"))
        assertEquals(inbox, messagesDesktopUrl("intent://x#Intent;package=com.facebook.orca;end"))
    }

    @Test
    fun desktopMessagesPage() {
        assertTrue(isDesktopMessagesUrl("https://www.facebook.com/messages/t/1"))
        assertFalse(isDesktopMessagesUrl("https://m.facebook.com/messages/t/1"))
        assertFalse(isDesktopMessagesUrl("https://www.facebook.com/home.php"))
    }
}
