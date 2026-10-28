package com.astryx.book.utils
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric for org.json (toJson).
@RunWith(RobolectricTestRunner::class)
class PhotoPostTest {

    // Facebook's desktop photo page holds the tracking data as a JSON string inside its JSON, so
    // its quotes come escaped (as read from a memory photo's page, 2026-10-16).
    private fun tracking(post: String, owner: String, vararg photos: String) =
        "\"tracking\":\"{\\\"top_level_post_id\\\":\\\"$post\\\",\\\"content_owner_id_new\\\":$owner," +
            "\\\"photo_attachments_list\\\":[" + photos.joinToString(",") { "\\\"$it\\\"" } + "],\\\"story_location\\\":9}\""

    private val photos = arrayOf("101", "102", "103", "104", "105")

    @Test
    fun aPhotoOfAMultiPhotoPost_opensThePostAtThatPhoto() {
        val page = "<script>{\"data\":{${tracking("900", "654346397", *photos)}}}</script>"
        assertEquals(PhotoPost("https://m.facebook.com/654346397/posts/900/", 2), PhotoPost.parse(page, "103"))
    }

    @Test
    fun aPagesPost_withItsPageIdBeforeTheList() {
        // As read from a NASA post's photo page: the owner's id is quoted, then the Page's id.
        val page = "\"tracking\":\"{\\\"top_level_post_id\\\":\\\"1635511074610929\\\"," +
            "\\\"content_owner_id_new\\\":\\\"100044561550831\\\",\\\"page_id\\\":\\\"54971236771\\\"," +
            "\\\"photo_attachments_list\\\":[\\\"1635511007944269\\\",\\\"1635510967944273\\\"]}\""
        assertEquals(
            PhotoPost("https://m.facebook.com/100044561550831/posts/1635511074610929/", 1),
            PhotoPost.parse(page, "1635510967944273")
        )
    }

    @Test
    fun aPhotoPastTheTiles_opensAtTheLastTile() {
        // A post with 11 photos lists the 5 its tiles show; the 7th photo isn't among them.
        val page = tracking("900", "654346397", *photos)
        assertEquals(PhotoPost("https://m.facebook.com/654346397/posts/900/", 4), PhotoPost.parse(page, "107"))
    }

    @Test
    fun thePhotosOwnPost_isPreferredOverOthersOnThePage() {
        val page = tracking("800", "1", "201", "202") + tracking("900", "2", *photos)
        assertEquals(PhotoPost("https://m.facebook.com/2/posts/900/", 0), PhotoPost.parse(page, "101"))
    }

    @Test
    fun readingThePage_stopsOnceThePhotosPostHasArrived() {
        // Past the post: a reader that fails, as a page cut short would.
        val start = "x".repeat(100_000) + tracking("900", "654346397", *photos)
        val page = object : java.io.Reader() {
            private val head = java.io.StringReader(start)
            override fun read(cbuf: CharArray, off: Int, len: Int): Int {
                val count = head.read(cbuf, off, len)
                if (count < 0) throw AssertionError("read past the photo's post")
                return count
            }
            override fun close() = Unit
        }
        assertEquals(PhotoPost("https://m.facebook.com/654346397/posts/900/", 1), PhotoPost.read(page, "102"))
    }

    @Test
    fun readingThePage_forAPhotoPastTheTiles_readsItAll() {
        val page = "x".repeat(70_000) + tracking("900", "654346397", *photos) + "y".repeat(70_000)
        assertEquals(
            PhotoPost("https://m.facebook.com/654346397/posts/900/", 4),
            PhotoPost.read(java.io.StringReader(page), "107")
        )
    }

    @Test
    fun aListSplitAcrossReads_isStillFound() {
        // The chunk boundary falls inside the list.
        val page = "x".repeat(32 * 1024 - 60) + tracking("900", "654346397", *photos)
        assertEquals(PhotoPost("https://m.facebook.com/654346397/posts/900/", 3), PhotoPost.read(java.io.StringReader(page), "104"))
    }

    @Test
    fun aSinglePhotoPost_orNoPost_isNothing() {
        assertNull(PhotoPost.parse(tracking("900", "1", "101"), "101"))
        assertNull(PhotoPost.parse("<html>not logged in</html>", "101"))
    }

    @Test
    fun theAnswerToThePage_namesThePhoto() {
        val json = JSONObject(PhotoPost("https://m.facebook.com/1/posts/900/", 3).toJson("104"))
        assertEquals("104", json.getString("fbid"))
        assertEquals("https://m.facebook.com/1/posts/900/", json.getString("url"))
        assertEquals(3, json.getInt("index"))
    }

    @Test
    fun onlyDigits_areLookedUp() {
        assertTrue(PhotoPost.isPhotoId("10158336853136398"))
        assertFalse(PhotoPost.isPhotoId(""))
        assertFalse(PhotoPost.isPhotoId("1&x=2"))
        assertFalse(PhotoPost.isPhotoId("1".repeat(40)))
    }
}
