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
 * took. Against a simulated strip as measured on the emulator (2026-11-18): a finger drag moves it
 * by its own length, one shorter than a finger's slop not at all.
 */
@RunWith(RobolectricTestRunner::class)
class StoryMusicTest {

    // The strip: [range] px of scroll, at [scroll]; its preview starts at the scroll's share of SONG_MS.
    private class Strip(val range: Double?, var scroll: Double) {
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
                            JSONObject(mapOf("left" to 60.0, "top" to 690.0, "width" to 236.0, "height" to 47.0,
                                "scroll" to strip.scroll, "range" to it, "viewport" to 411.0)).toString()
                        } ?: "null"
                        ".replay(" in command.script -> { strip.replays++; "\"ok\"" }
                        ".position(" in command.script -> strip.range?.let { (strip.scroll / it * SONG_MS).toLong().toString() } ?: "-1"
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
        val start = StoryMusic.setStart(0.725f)
        page.cancel()
        assertEquals("the strip isn't where the start goes", 0.725 * 3821, strip.scroll, 12.0)
        assertTrue("a drag was longer than the page: ${strip.longest}", strip.longest <= 411)
        assertEquals("the preview wasn't played again once there", 1, strip.replays)
        assertEquals((0.725 * SONG_MS), start!!.toDouble(), SONG_MS * 0.01)
    }

    @Test
    fun anEarlierStart_isDraggedToTheRight() = runBlocking {
        val strip = Strip(3821.0, 3000.0)
        val page = page(strip)
        StoryMusic.setStart(0.1f)
        page.cancel()
        assertEquals(0.1 * 3821, strip.scroll, 12.0)
    }

    @Test
    fun withoutAStrip_thereIsNoStart() = runBlocking {
        val strip = Strip(null, 0.0)
        val page = page(strip)
        assertNull(StoryMusic.setStart(0.5f))
        page.cancel()
        assertEquals(0, strip.drags)
    }

    private companion object {
        const val SONG_MS = 270_000.0
        const val SLOP_PX = 8.0
    }
}
