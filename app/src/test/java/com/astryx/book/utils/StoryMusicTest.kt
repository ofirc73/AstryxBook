package com.astryx.book.utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

/**
 * StoryMusic.setStart: the chosen song's clip start, set by finger drags on Facebook's clip strip
 * (the only input that moves it), then Facebook's preview played again and read for the start it
 * took. Against a simulated strip as measured on the emulator (2026-11-18): the whole song (its
 * scroll over its content's width is the start's share of the song), a finger drag moves it by its
 * own length, one shorter than a finger's slop not at all.
 */
@RunWith(RobolectricTestRunner::class)
class StoryMusicTest {

    // The strip: [range] px of scroll, at [scroll], WIDTH px wide; its preview starts at the scroll's
    // share of its content (range + WIDTH) of SONG_MS.
    private class Strip(val range: Double?, var scroll: Double) {
        fun startMs() = range?.let { (scroll / (it + WIDTH) * SONG_MS).toLong() }
        var drags = 0
        var replays = 0
        var longest = 0.0
    }

    // The creator's page answering StoryMusic's commands for [strip] until the job's cancelled.
    private fun CoroutineScope.page(strip: Strip): Job = launch {
        StoryMusic.commands.collect { command ->
            when (command) {
                is MusicCommand.Eval -> command.result.complete(
                    when {
                        ".strip(" in command.script -> strip.range?.let {
                            JSONObject(mapOf("left" to 60.0, "top" to 690.0, "width" to WIDTH, "height" to 47.0,
                                "scroll" to strip.scroll, "range" to it, "total" to it + WIDTH, "viewport" to 411.0)).toString()
                        } ?: "null"
                        ".replay(" in command.script -> { strip.replays++; "\"ok\"" }
                        ".position(" in command.script -> (strip.startMs() ?: -1).toString()
                        else -> "null"
                    }
                )
                is MusicCommand.Drag -> {
                    val moved = command.fromX - command.toX
                    strip.longest = maxOf(strip.longest, abs(moved))
                    if (abs(moved) >= SLOP_PX) strip.scroll = (strip.scroll + moved).coerceIn(0.0, strip.range ?: 0.0)
                    strip.drags++
                    command.done.complete(Unit)
                }
            }
        }
    }

    @Test
    fun aLaterStart_isDraggedToTheLeft_untilThere_thenPlayedFromThere() = runBlocking {
        val strip = Strip(3821.0, 0.0)
        val page = page(strip)
        val start = StoryMusic.setStart(195_000, SONG_MS.toLong())
        page.cancel()
        assertEquals("the strip isn't where the start goes", 195_000 / SONG_MS * (3821 + WIDTH), strip.scroll, 12.0)
        assertTrue("a drag was longer than the page: ${strip.longest}", strip.longest <= 411)
        assertEquals("the preview wasn't played again once there", 1, strip.replays)
        assertEquals(195_000.0, start!!.toDouble(), SONG_MS * 0.01)
    }

    @Test
    fun anEarlierStart_isDraggedToTheRight() = runBlocking {
        val strip = Strip(3821.0, 3000.0)
        val page = page(strip)
        StoryMusic.setStart(27_000, SONG_MS.toLong())
        page.cancel()
        assertEquals(0.1 * (3821 + WIDTH), strip.scroll, 12.0)
    }

    @Test
    fun aStartPastTheLastClip_goesToTheStripsEnd() = runBlocking {
        val strip = Strip(3821.0, 0.0)
        val page = page(strip)
        StoryMusic.setStart(SONG_MS.toLong(), SONG_MS.toLong())
        page.cancel()
        assertEquals(3821.0, strip.scroll, 12.0)
    }

    @Test
    fun aChosenSongsStart_isReadFromTheStrip() = runBlocking {
        val strip = Strip(3821.0, 2028.5)
        val page = page(strip)
        assertEquals(135_000L, StoryMusic.start(SONG_MS.toLong()))
        page.cancel()
        assertEquals("the strip was moved to be read", 2028.5, strip.scroll, 0.0)
    }

    @Test
    fun withoutAStrip_thereIsNoStart() = runBlocking {
        val strip = Strip(null, 0.0)
        val page = page(strip)
        assertNull(StoryMusic.setStart(135_000, SONG_MS.toLong()))
        page.cancel()
        assertEquals(0, strip.drags)
    }

    private companion object {
        const val SONG_MS = 270_000.0
        const val SLOP_PX = 8.0
        const val WIDTH = 236.0
    }
}
