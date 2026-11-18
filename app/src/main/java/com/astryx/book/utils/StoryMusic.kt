package com.astryx.book.utils
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** A song Facebook's music panel offers (story_music.js reads it from the panel's own answers). */
data class Song(val id: String, val title: String, val artist: String, val cover: String, val durationMs: Long)

/**
 * What Facebook's music panel plays: the song whose preview plays in its list ([songId]; null for
 * none, as while a song is chosen), and where the page's audio is ([ms]; -1 without one).
 */
data class Playback(val songId: String?, val ms: Long, val paused: Boolean)

/** What the story creator's page in the Messages layer is asked to do for the music picker. */
sealed interface MusicCommand {
    /**
     * Evaluates [script] there; its result (null without the creator) goes to [result]. The page is
     * focused first ([focus]: Facebook's search box takes typed text only in a focused page), but
     * never for a read the picker repeats (it would take the focus from the picker's search box).
     */
    class Eval(val script: String, val result: CompletableDeferred<String?>, val focus: Boolean = true) : MusicCommand
    /** A real finger drag across the page, in CSS px of a viewport [viewport] px wide. */
    class Drag(val fromX: Double, val toX: Double, val y: Double, val viewport: Double, val done: CompletableDeferred<Unit>) : MusicCommand
}

/**
 * The native share screen's music picker, on the app's side: the songs Facebook's music panel gave
 * (story_music.js reports them, AstryxShare "music", by the search they answer), and the picker's
 * commands to the story creator in the Messages layer (MessagesLayer runs them on its page:
 * [commands]), which plays the previews and keeps the chosen song for the story. Process-wide, like
 * the share it belongs to.
 */
object StoryMusic {
    private val lists = mutableMapOf<String, List<Song>>()

    /** The songs for each search ("": Facebook's For you list), as they came. Counts in [version]. */
    fun songs(query: String): List<Song>? = synchronized(lists) { lists[query] }
    var version by mutableIntStateOf(0)
        private set

    private val channel = Channel<MusicCommand>(Channel.UNLIMITED)
    /** For MessagesLayer: the commands it runs on the creator's page. */
    val commands = channel.receiveAsFlow()

    /** The songs story_music.js read from an answer to [query]. */
    fun onSongs(query: String, json: JSONArray) {
        val songs = (0 until json.length()).mapNotNull { json.optJSONObject(it)?.toSong() }
        synchronized(lists) { lists[query] = songs }
        version++
    }

    /** Forgets every song (a new share). */
    fun clear() {
        synchronized(lists) { lists.clear() }
        version++
    }

    private fun JSONObject.toSong(): Song? {
        val id = optString("id").takeIf { it.isNotEmpty() } ?: return null
        return Song(id, optString("title"), optString("artist"), optString("cover"), optLong("durationMs"))
    }

    /** [script]'s result on the creator's page, or null when it isn't there to answer (EVAL_MS). */
    suspend fun eval(script: String, focus: Boolean = true): String? {
        val result = CompletableDeferred<String?>()
        channel.send(MusicCommand.Eval(script, result, focus))
        return withTimeoutOrNull(EVAL_MS) { result.await() }
    }

    // story_music.js's api called with [args]: its result as evaluateJavascript gives it (JSON).
    private suspend fun callJson(name: String, vararg args: Any, focus: Boolean = true): String? =
        eval("window.__astryxStoryMusic ? window.__astryxStoryMusic.$name(" +
            args.joinToString(",") { if (it is String) JSONObject.quote(it) else it.toString() } + ") : null", focus)

    // The same, a string result (null for anything else).
    private suspend fun call(name: String, vararg args: Any): String? =
        callJson(name, *args)?.let { runCatching { JSONArray("[$it]").opt(0) as? String }.getOrNull() }

    /** Opens Facebook's music panel once its editor shows the story: true when it's open. */
    suspend fun open(): Boolean {
        repeat(OPEN_TRIES) {
            when (call("open")) {
                "open" -> return true
                "opening" -> { delay(OPEN_RETRY_MS); if (call("open") == "open") return true }
                else -> delay(OPEN_RETRY_MS)
            }
        }
        return false
    }

    suspend fun search(text: String) = call("search", text)
    suspend fun play(id: String) = untilRow { call("play", id) }
    suspend fun choose(id: String) = untilRow { call("choose", id) } == "ok"

    // [step] again while the song's row isn't in Facebook's panel yet ("noRow"): its answer reaches
    // the app a moment before the panel draws its rows.
    private suspend fun untilRow(step: suspend () -> String?): String? {
        repeat(ROW_TRIES) {
            val result = step()
            if (result != "noRow") return result
            delay(ROW_RETRY_MS)
        }
        return "noRow"
    }
    suspend fun done() = call("done")

    /** What the panel plays now (null without the creator), for the picker to show; read without focusing it. */
    suspend fun playback(): Playback? =
        callJson("playback", focus = false)?.let { runCatching { JSONObject(it) }.getOrNull() }?.let {
            Playback(it.optString("id").takeIf { id -> id.isNotEmpty() }, it.optLong("ms", -1), it.optBoolean("paused", true))
        }

    /** The chosen song's clip paused, or played again (the clip bar's Pause or Play). */
    suspend fun toggle() = call("toggle")

    // The clip bar's strip, as story_music.js strip() gives it: where it is on the page (CSS px), its
    // scroll, as far as it scrolls, its whole width (the song's length), and the viewport's width.
    private class Strip(json: JSONObject) {
        val left = json.getDouble("left")
        val width = json.getDouble("width")
        val y = json.getDouble("top") + json.getDouble("height") / 2
        val scroll = json.getDouble("scroll")
        val range = json.getDouble("range")
        val total = json.getDouble("total")
        val viewport = json.getDouble("viewport")
        /** Where the clip starts, in ms of a song [durationMs] long. */
        fun startMs(durationMs: Long) = if (total > 0) (scroll / total * durationMs).toLong() else 0L
    }

    private suspend fun strip(): Strip? = callJson("strip")?.let { runCatching { Strip(JSONObject(it)) }.getOrNull() }

    /**
     * Where the chosen song's clip starts (ms of a song [durationMs] long), from the strip's scroll
     * (Facebook may start a song past its beginning): null if the clip bar doesn't show.
     */
    suspend fun start(durationMs: Long): Long? {
        repeat(ROW_TRIES) {
            strip()?.let { return it.startMs(durationMs) }
            delay(ROW_RETRY_MS)
        }
        return null
    }

    /**
     * Moves the chosen song's clip (of a song [durationMs] long) to start at [startMs]: Facebook's clip
     * bar starts the clip at the scroll of its waveform strip, which only real input moves (scrolled
     * by a script it's put back), so it's dragged by finger until it's there (to the left for a later
     * start). Facebook keeps the start from the scroll and plays it from there once its preview is
     * played again (replay); where that plays is the start it took, returned in ms. Null when the
     * strip couldn't be found or never got there. Logged (`Music start:`) for a phone where it doesn't move.
     */
    suspend fun setStart(startMs: Long, durationMs: Long): Long? {
        var last = Double.NaN
        repeat(START_DRAGS) { drags ->
            val strip = strip() ?: return null.also { Log.d(TAG, "Music start: no clip strip") }
            val target = (startMs.toDouble() / durationMs.coerceAtLeast(1) * strip.total).coerceIn(0.0, strip.range)
            val off = target - strip.scroll
            // Close enough, or the last drag moved nothing (one shorter than a finger's slop).
            if (abs(off) < START_CLOSE_PX || strip.scroll == last) {
                Log.d(TAG, "Music start: ${strip.scroll.toInt()} of ${strip.range.toInt()} px for ${target.toInt()}, after $drags drags")
                call("replay")
                delay(REPLAY_MS)
                return callJson("position")?.toLongOrNull()?.takeIf { it >= 0 } ?: strip.startMs(durationMs)
            }
            // From one end of the strip as far as the page goes the other way (a finger dragging the
            // strip may leave it): to the left for a later start, to the right for an earlier one.
            val right = strip.left + strip.width
            val (from, to) = if (off > 0) (right - EDGE_PX).let { it to (it - off).coerceAtLeast(EDGE_PX) }
            else (strip.left + EDGE_PX).let { it to (it - off).coerceAtMost(strip.viewport - EDGE_PX) }
            val done = CompletableDeferred<Unit>()
            last = strip.scroll
            channel.send(MusicCommand.Drag(from, to, strip.y, strip.viewport, done))
            withTimeoutOrNull(EVAL_MS) { done.await() }
            delay(START_SETTLE_MS)
        }
        Log.d(TAG, "Music start: not there after $START_DRAGS drags")
        return null
    }

    /** How long a story's clip of a song plays (Facebook's preview plays it over and over; read 2026-11-18). */
    const val CLIP_MS = 15_000L

    // How long a page call may take; the music panel's open, tried this often this far apart.
    private const val EVAL_MS = 5_000L
    private const val OPEN_TRIES = 60
    private const val OPEN_RETRY_MS = 1_000L
    private const val ROW_TRIES = 20
    private const val ROW_RETRY_MS = 500L
    // The clip start's drags: at most this many, until within this many CSS px of where it goes,
    // each given this long for Facebook to take it.
    private const val START_DRAGS = 30
    private const val EDGE_PX = 4.0
    private const val START_CLOSE_PX = 12.0
    private const val START_SETTLE_MS = 500L
    // How long after the replay its start is read (story_music.js position()).
    private const val REPLAY_MS = 1_200L
    private const val TAG = "Astryxbook"
}
