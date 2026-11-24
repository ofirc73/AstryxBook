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
 * (the only input that moves it), then Facebook's preview played again from it; and StoryMusic.apply,
 * a photo's song put into its story's editor (found, chosen, its start, Done). Against a simulated strip as measured on the emulator (2026-11-18): the whole song (its
 * scroll over its content's width is the start's share of the song), a finger drag moves it by its
 * own length, one shorter than a finger's slop not at all.
 */
@RunWith(RobolectricTestRunner::class)
class StoryMusicTest {

    // The strip: [range] px of scroll, at [scroll], WIDTH px wide; its clip starts at the scroll's
    // share of its content (range + WIDTH) of SONG_MS.
    private class Strip(val range: Double?, var scroll: Double, var ignored: Int = 0) {
        var drags = 0
        var replays = 0
        var longest = 0.0
    }

    // Facebook's music panel: the song ids in its For you list, those a search finds (by its text),
    // and what was searched for, chosen and kept (Done).
    private class Panel(val forYou: List<String> = emptyList(), val found: Map<String, List<String>> = emptyMap()) {
        val searched = mutableListOf<String>()
        var chosen: String? = null
        var done = 0
        fun songs(query: String): List<String> = if (query.isEmpty()) forYou else if (query in searched) found[query].orEmpty() else emptyList()
    }

    // The first quoted argument of a page call in [script].
    private fun argument(script: String) = Regex("""\("([^"]*)"""").find(script)?.groupValues?.get(1).orEmpty()

    // The creator's page answering StoryMusic's commands for [strip] (and [panel]) until the job's cancelled.
    private fun CoroutineScope.page(strip: Strip, panel: Panel = Panel()): Job = launch {
        StoryMusic.commands.collect { command ->
            when (command) {
                is MusicCommand.Eval -> command.result.complete(
                    when {
                        ".open(" in command.script -> "\"open\""
                        ".songs(" in command.script -> panel.songs(argument(command.script)).joinToString(",", "[", "]") { "{\"id\":\"$it\"}" }
                        ".search(" in command.script -> { panel.searched += argument(command.script); "\"searching\"" }
                        ".choose(" in command.script -> { panel.chosen = argument(command.script); "\"ok\"" }
                        ".done(" in command.script -> { panel.done++; "\"ok\"" }
                        ".strip(" in command.script -> strip.range?.let {
                            JSONObject(mapOf("left" to 60.0, "top" to 690.0, "width" to WIDTH, "height" to 47.0,
                                "scroll" to strip.scroll, "range" to it, "total" to it + WIDTH, "viewport" to 411.0)).toString()
                        } ?: "null"
                        ".replay(" in command.script -> { strip.replays++; "\"ok\"" }
                        else -> "null"
                    }
                )
                is MusicCommand.Drag -> {
                    val moved = command.fromX - command.toX
                    strip.longest = maxOf(strip.longest, abs(moved))
                    if (strip.ignored > 0) strip.ignored--
                    else if (abs(moved) >= SLOP_PX) strip.scroll = (strip.scroll + moved).coerceIn(0.0, strip.range ?: 0.0)
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

    // A story of several photos: each photo's song put into its story's editor as its turn comes.
    @Test
    fun aSongInForYou_isChosenThere_atItsStart_andKept() = runBlocking {
        val strip = Strip(3821.0, 0.0)
        val panel = Panel(forYou = listOf("1", "7"))
        val page = page(strip, panel)
        val applied = StoryMusic.apply(SongStart(SONG, 135_000))
        page.cancel()
        assertTrue(applied)
        assertEquals("searched though the song was in For you", emptyList<String>(), panel.searched)
        assertEquals("7", panel.chosen)
        assertEquals("the clip wasn't moved to the start chosen", 135_000 / SONG_MS * (3821 + WIDTH), strip.scroll, 12.0)
        assertEquals("the song wasn't kept (Done)", 1, panel.done)
    }

    @Test
    fun aSongNotInForYou_isSearchedForByItsTitle() = runBlocking {
        val strip = Strip(3821.0, 0.0)
        val panel = Panel(forYou = listOf("1"), found = mapOf("Sunshine" to listOf("7")))
        val page = page(strip, panel)
        val applied = StoryMusic.apply(SongStart(SONG, 0))
        page.cancel()
        assertTrue(applied)
        assertEquals(listOf("Sunshine"), panel.searched)
        assertEquals("7", panel.chosen)
        assertEquals("the clip was moved though Facebook's start was the one chosen", 0, strip.drags)
    }

    // A strip that ignores its first drag (Facebook's, right after the song was chosen): dragged again.
    @Test
    fun aFirstDragTheStripIgnores_isMadeAgain() = runBlocking {
        val strip = Strip(3821.0, 0.0, ignored = 1)
        val page = page(strip)
        StoryMusic.setStart(135_000, SONG_MS.toLong())
        page.cancel()
        assertEquals(135_000 / SONG_MS * (3821 + WIDTH), strip.scroll, 12.0)
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
        val SONG = Song("7", "Sunshine", "Lesley Gore", "", SONG_MS.toLong())
    }
}
