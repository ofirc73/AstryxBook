package com.astryx.book.utils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShareMediaTest {

    @After
    fun tearDown() = ShareMediaQueue.clear()

    private fun photo(name: String = "a.jpg", size: Long = 1000) = ShareCandidate(name, "image/jpeg", size)
    private fun video(name: String = "v.mp4", size: Long = 1000) = ShareCandidate(name, "video/mp4", size)

    @Test
    fun theTarget_comesFromTheShareEntryUsed() {
        assertEquals(ShareTarget.FEED, ShareMedia.targetOf("com.eepiemi.materialbook.ShareToFeed"))
        assertEquals(ShareTarget.STORY, ShareMedia.targetOf("com.eepiemi.materialbook.ShareToStory"))
        assertEquals(ShareTarget.PROFILE_PICTURE, ShareMedia.targetOf("com.eepiemi.materialbook.ShareToProfilePicture"))
        assertEquals(ShareTarget.GROUP, ShareMedia.targetOf("com.eepiemi.materialbook.ShareToGroup"))
        assertEquals(ShareTarget.REEL, ShareMedia.targetOf("com.eepiemi.materialbook.ShareToReel"))
        assertNull(ShareMedia.targetOf("com.eepiemi.materialbook.MainActivity"))
        assertNull(ShareMedia.targetOf(null))
    }

    @Test
    fun eachTarget_hasAKeyForThePage() {
        ShareTarget.entries.forEach { assertEquals(it, ShareTarget.ofKey(it.key)) }
        assertNull(ShareTarget.ofKey("unknown"))
    }

    @Test
    fun aProfilePicture_isOnePhoto_aReelOneVideo_aGroupPhotosLikeTheFeed() {
        val shared = listOf(photo("1.jpg"), photo("2.jpg"), video("v.mp4"), video("w.mp4"))
        assertEquals(listOf("1.jpg"), ShareMedia.select(ShareTarget.PROFILE_PICTURE, shared).accepted.map { it.name })
        assertEquals(3, ShareMedia.select(ShareTarget.PROFILE_PICTURE, shared).skipped)
        assertEquals(listOf("v.mp4"), ShareMedia.select(ShareTarget.REEL, shared).accepted.map { it.name })
        assertEquals(listOf("1.jpg", "2.jpg"), ShareMedia.select(ShareTarget.GROUP, shared).accepted.map { it.name })
        assertFalse(ShareMedia.fits(ShareTarget.REEL, "image/jpeg", 100))
        assertFalse(ShareMedia.fits(ShareTarget.GROUP, "video/mp4", 100))
    }

    @Test
    fun mobileShares_openTheirPage_storiesAndReelsTheLayer() {
        assertEquals(FEED_COMPOSER_PAGE_URL, shareStartPageUrl(ShareTarget.FEED))
        assertEquals(PROFILE_PAGE_URL, shareStartPageUrl(ShareTarget.PROFILE_PICTURE))
        assertEquals(GROUPS_PAGE_URL, shareStartPageUrl(ShareTarget.GROUP))
        assertNull(shareStartPageUrl(ShareTarget.STORY))
        assertNull(shareStartPageUrl(ShareTarget.REEL))
    }

    @Test
    fun theReelsPage_isRecognized() {
        assertTrue(isReelCreatePage(REEL_CREATE_URL))
        assertTrue(isReelCreatePage("https://www.facebook.com/reel/"))
        assertTrue(isReelCreatePage("https://www.facebook.com/reel/123456"))
        assertFalse(isReelCreatePage("https://m.facebook.com/reel/123456"))
        assertFalse(isReelCreatePage("https://www.facebook.com/reelsomething"))
        assertFalse(isReelCreatePage(null))
    }

    @Test
    fun aShare_knowsHowOldItIs() {
        ShareMediaQueue.start(ShareTarget.GROUP, listOf(SharedFile(File("a"), "a", "image/jpeg")), now = 1_000)
        assertFalse(ShareMediaQueue.olderThan(ShareMediaQueue.GROUP_PICK_MS, now = 1_000 + ShareMediaQueue.GROUP_PICK_MS))
        assertTrue(ShareMediaQueue.olderThan(ShareMediaQueue.GROUP_PICK_MS, now = 2_000 + ShareMediaQueue.GROUP_PICK_MS))
        ShareMediaQueue.clear()
        assertFalse(ShareMediaQueue.olderThan(0))
    }

    @Test
    fun theFeed_takesPhotosOnly_storiesTakeVideosToo() {
        val shared = listOf(photo("1.jpg"), video(), photo("2.png").copy(mimeType = "image/png"))
        assertEquals(listOf("1.jpg", "2.png"), ShareMedia.select(ShareTarget.FEED, shared).accepted.map { it.name })
        assertEquals(1, ShareMedia.select(ShareTarget.FEED, shared).skipped)
        assertEquals(3, ShareMedia.select(ShareTarget.STORY, shared).accepted.size)
        assertFalse(ShareMedia.fits(ShareTarget.STORY, "application/pdf", 100))
    }

    @Test
    fun tooBigOrTooMany_areLeftOut_andCounted() {
        assertFalse(ShareMedia.fits(ShareTarget.FEED, "image/jpeg", ShareMedia.MAX_PHOTO_BYTES + 1))
        assertFalse(ShareMedia.fits(ShareTarget.STORY, "video/mp4", ShareMedia.MAX_VIDEO_BYTES + 1))
        assertFalse(ShareMedia.fits(ShareTarget.FEED, "image/jpeg", 0))
        // An unknown size passes: the copy enforces the limit.
        assertTrue(ShareMedia.fits(ShareTarget.FEED, "image/jpeg", -1))

        val many = List(ShareMedia.MAX_STORY_ITEMS + 3) { photo("$it.jpg") }
        val selection = ShareMedia.select(ShareTarget.STORY, many)
        assertEquals(ShareMedia.MAX_STORY_ITEMS, selection.accepted.size)
        assertEquals(3, selection.skipped)
    }

    @Test
    fun safeNames_keepOnlySimpleCharacters_andAreNumbered() {
        assertEquals("0_IMG_2024_01.jpg", ShareMedia.safeName("IMG_2024 01.jpg", 0, "image/jpeg"))
        assertEquals("1_name.png", ShareMedia.safeName("../../evil/name.png", 1, "image/png"))
        assertEquals("2_shared.mp4", ShareMedia.safeName("סרטון", 2, "video/mp4"))
        assertEquals("3_shared.jpeg", ShareMedia.safeName("", 3, "image/jpeg"))
    }

    @Test
    fun theQueue_handsOutFilesOneByOne_orAllAtOnce_forItsTargetOnly() {
        val files = listOf("a", "b", "c").map { SharedFile(File(it), it, "image/jpeg") }
        ShareMediaQueue.start(ShareTarget.STORY, files)
        assertFalse(ShareMediaQueue.pending(ShareTarget.FEED))
        assertNull(ShareMediaQueue.take(ShareTarget.FEED))
        assertEquals("a", ShareMediaQueue.take(ShareTarget.STORY)?.name)
        assertEquals(2, ShareMediaQueue.remaining(ShareTarget.STORY))
        assertEquals(listOf("b", "c"), ShareMediaQueue.takeAll(ShareTarget.STORY).map { it.name })
        assertFalse(ShareMediaQueue.pending(ShareTarget.STORY))
        assertNull(ShareMediaQueue.take(ShareTarget.STORY))
    }

    @Test
    fun aNewShare_replacesTheOneInProgress() {
        ShareMediaQueue.start(ShareTarget.STORY, listOf(SharedFile(File("a"), "a", "image/jpeg")))
        ShareMediaQueue.start(ShareTarget.FEED, listOf(SharedFile(File("b"), "b", "image/jpeg")))
        assertFalse(ShareMediaQueue.pending(ShareTarget.STORY))
        assertEquals("b", ShareMediaQueue.take(ShareTarget.FEED)?.name)
    }

    @Test
    fun theStoryCreator_isRecognized() {
        assertTrue(isStoryCreatePage("https://www.facebook.com/stories/create"))
        assertTrue(isStoryCreatePage("https://www.facebook.com/stories/create/?ref=x"))
        assertFalse(isStoryCreatePage("https://m.facebook.com/stories/create"))
        assertFalse(isStoryCreatePage("https://www.facebook.com/stories/123/"))
        assertFalse(isStoryCreatePage(null))
    }
}
