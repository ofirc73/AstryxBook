package com.astryx.book.utils
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

/** What the story creator's page in the Messages layer is asked to do for the music picker. */
sealed interface MusicCommand {
    /** Evaluates [script] there; its result (null without the creator) goes to [result]. */
    class Eval(val script: String, val result: CompletableDeferred<String?>) : MusicCommand
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
    suspend fun eval(script: String): String? {
        val result = CompletableDeferred<String?>()
        channel.send(MusicCommand.Eval(script, result))
        return withTimeoutOrNull(EVAL_MS) { result.await() }
    }

    // story_music.js's api called with [args]: its result as evaluateJavascript gives it (JSON).
    private suspend fun callJson(name: String, vararg args: Any): String? =
        eval("window.__astryxStoryMusic ? window.__astryxStoryMusic.$name(" +
            args.joinToString(",") { if (it is String) JSONObject.quote(it) else it.toString() } + ") : null")

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
    suspend fun style(index: Int) = call("style", index)
    suspend fun done() = call("done")

    /**
     * Moves the chosen song's clip to start [fraction] of the way along: Facebook's clip bar starts
     * the clip at the scroll of its waveform strip, which only real input moves (scrolled by a script
     * it's put back), so it's dragged by finger until it's there (to the left for a later start).
     * Facebook keeps the start from the scroll and plays it from there once its preview is played
     * again (replay); where that plays is the start it took, returned in ms. Null when the strip
     * couldn't be found.
     */
    suspend fun setStart(fraction: Float): Long? {
        var last = Double.NaN
        repeat(START_DRAGS) {
            val strip = callJson("strip")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
            val left = strip.getDouble("left")
            val right = left + strip.getDouble("width")
            val viewport = strip.getDouble("viewport")
            val target = fraction.coerceIn(0f, 1f) * strip.getDouble("range")
            val scroll = strip.getDouble("scroll")
            val off = target - scroll
            // Close enough, or the last drag moved nothing (one shorter than a finger's slop).
            if (abs(off) < START_CLOSE_PX || scroll == last) {
                call("replay")
                delay(REPLAY_MS)
                return callJson("position")?.toLongOrNull()?.takeIf { it >= 0 }
            }
            // From one end of the strip as far as the page goes the other way (a finger dragging the
            // strip may leave it): to the left for a later start, to the right for an earlier one.
            val (from, to) = if (off > 0) (right - EDGE_PX).let { it to (it - off).coerceAtLeast(EDGE_PX) }
            else (left + EDGE_PX).let { it to (it - off).coerceAtMost(viewport - EDGE_PX) }
            val y = strip.getDouble("top") + strip.getDouble("height") / 2
            val done = CompletableDeferred<Unit>()
            last = scroll
            channel.send(MusicCommand.Drag(from, to, y, viewport, done))
            withTimeoutOrNull(EVAL_MS) { done.await() }
            delay(START_SETTLE_MS)
        }
        return null
    }

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
}
