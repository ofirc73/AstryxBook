package com.astryx.book.utils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The story creator's text-story backgrounds, which a story share doesn't load (StoryBackgrounds).
class StoryBackgroundsTest {

    @Test
    fun backgrounds_areTheirFbcdnPictures() {
        assertTrue(isStoryBackgroundUrl("https://scontent.ftlv19-3.fna.fbcdn.net/v/t39.10873-6/51627537_2013736385406998_2008135126298394624_n.jpg?_nc_cat=108"))
        assertTrue(isStoryBackgroundUrl("https://scontent-lhr8-1.xx.fbcdn.net/v/t39.10873-6/1_2_n.jpg"))
        // A story's photo, a profile picture, the same path on another host.
        assertFalse(isStoryBackgroundUrl("https://scontent.ftlv19-1.fna.fbcdn.net/v/t39.30808-6/603899071_n.jpg"))
        assertFalse(isStoryBackgroundUrl("https://scontent.ftlv19-1.fna.fbcdn.net/v/t1.30497-1/453178253_n.png"))
        assertFalse(isStoryBackgroundUrl("https://example.com/v/t39.10873-6/1_n.jpg"))
        assertFalse(isStoryBackgroundUrl("https://scontent.fbcdn.net.example.com/v/t39.10873-6/1_n.jpg"))
        assertFalse(isStoryBackgroundUrl("https://example.com/?u=https://scontent.xx.fbcdn.net/v/t39.10873-6/1_n.jpg"))
    }

    @Test
    fun nothingIsSkipped_outsideAStoryShare() {
        assertFalse(StoryBackgrounds.skips(null, "https://scontent.xx.fbcdn.net/v/t39.10873-6/1_n.jpg"))
    }
}
