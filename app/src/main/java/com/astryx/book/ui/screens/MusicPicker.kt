package com.astryx.book.ui.screens
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.astryx.book.utils.Playback
import com.astryx.book.utils.Song
import com.astryx.book.utils.StoryMusic
import com.astryx.book.utils.isFacebookCdnHost
import com.eepiemi.materialbook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.URL

/** The music picker's steps (NativeShareController's), for [MusicPicker]. */
class MusicActions(
    val search: (String) -> Unit,
    val play: (Song) -> Unit,
    val choose: (Song) -> Unit,
    val start: (Long) -> Unit,
    val toggle: () -> Unit,
    val close: () -> Unit,
    val remove: () -> Unit,
)

/**
 * The story's music, over the share screen: Facebook's songs (For you, or a search) to preview and
 * choose from, then the chosen one's clip. No lyrics style: the app keeps the song's sticker off
 * the story (story_layout.js), and the style is only the sticker's look. Facebook's own music panel does the
 * work out of sight ([actions], StoryMusic): it plays the previews and keeps the song for the story.
 * What it plays is read every [PLAYBACK_MS] and shown (the previewing row, the clip's playhead): a
 * song played with nothing on screen to say so looked like nothing worked (2026-11-18).
 */
@Composable
internal fun MusicPicker(share: NativeShare, actions: MusicActions, modifier: Modifier = Modifier) {
    BackHandler(onBack = actions.close)
    Box(modifier.fillMaxSize().zIndex(3f).background(Color.Black).testTag("musicPicker")) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.close) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.native_share_close), tint = Color.White)
                }
                Text(stringResource(R.string.native_share_music), color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            }
            val choice = share.music
            val playback by produceState<Playback?>(null, share.musicReady) {
                if (share.musicReady != true) return@produceState
                while (true) {
                    value = StoryMusic.playback()
                    delay(PLAYBACK_MS)
                }
            }
            when {
                share.musicReady == false -> Waiting(stringResource(R.string.native_share_music_preparing))
                share.musicReady == null -> Waiting(stringResource(R.string.native_share_music_unavailable), spinner = false)
                choice != null -> Chosen(choice, playback, actions)
                else -> Songs(playback, actions)
            }
        }
    }
}

@Composable
private fun Waiting(text: String, spinner: Boolean = true) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (spinner) CircularProgressIndicator(color = Color.White)
        Text(text, color = Color.White, modifier = Modifier.padding(top = 16.dp))
    }
}

// The search box and the songs: For you until a search is made, then what it found.
@Composable
private fun Songs(playback: Playback?, actions: MusicActions) {
    var text by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf("") }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White) },
        placeholder = { Text(stringResource(R.string.native_share_music_search)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { searched = text.trim(); actions.search(searched) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
            cursorColor = Color.White,
            focusedPlaceholderColor = Color.White.copy(alpha = 0.6f), unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("musicSearch"),
    )
    // Read through StoryMusic.version: the songs come from the page as its answers arrive.
    val songs = StoryMusic.version.let { StoryMusic.songs(searched) }
    if (searched.isEmpty()) {
        Text(stringResource(R.string.native_share_music_for_you), color = Color.White, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    }
    when {
        songs == null -> Waiting(stringResource(R.string.native_share_music_loading))
        songs.isEmpty() -> Waiting(stringResource(R.string.native_share_music_none), spinner = false)
        else -> LazyColumn(Modifier.fillMaxSize().testTag("musicSongs")) {
            items(songs, key = { it.id }) { song ->
                SongRow(song, playback?.takeIf { it.songId == song.id && !it.paused }?.ms, actions)
            }
        }
    }
}

// A song: a tap chooses it, its button previews it. Previewing ([playingMs], where the preview is),
// it's marked, its button pauses it, and a bar under it moves with the preview.
@Composable
private fun SongRow(song: Song, playingMs: Long?, actions: MusicActions) {
    val playing = playingMs != null
    Column(Modifier.fillMaxWidth().background(if (playing) ACCENT.copy(alpha = 0.18f) else Color.Transparent)) {
        Row(
            Modifier.fillMaxWidth().clickable { actions.choose(song) }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("musicSong"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Cover(song.cover, Modifier.size(48.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, color = if (playing) ACCENT_TEXT else Color.White, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            PlayButton(playing, song.title) { actions.play(song) }
        }
        if (playingMs != null && song.durationMs > 0) {
            LinearProgressIndicator(
                progress = { (playingMs.toFloat() / song.durationMs).coerceIn(0f, 1f) },
                color = ACCENT, trackColor = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp).testTag("musicPreviewing"),
            )
        }
    }
}

// Play, or Pause while [playing]: for [title].
@Composable
private fun PlayButton(playing: Boolean, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.native_share_music_pause else R.string.native_share_music_play, title),
            tint = Color.White,
        )
    }
}

// The chosen song: what plays (Pause / Play, where), its clip over the song (to drag where it starts),
// and Remove / Done. Facebook plays the clip over and over from its start, so a move is heard.
@Composable
private fun Chosen(choice: MusicChoice, playback: Playback?, actions: MusicActions) {
    val song = choice.song
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(song.cover, Modifier.size(64.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(song.artist, color = Color.White.copy(alpha = 0.7f))
            }
        }
        val playingMs = playback?.takeIf { !it.paused && it.ms >= 0 }?.ms
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayButton(playingMs != null, song.title, Modifier.background(ACCENT, RoundedCornerShape(50)).testTag("musicToggle"), actions.toggle)
            Text(
                playingMs?.let { stringResource(R.string.native_share_music_playing, time(it)) }
                    ?: stringResource(R.string.native_share_music_paused),
                color = Color.White, modifier = Modifier.padding(start = 12.dp).testTag("musicPlaying"),
            )
        }
        // While a move is dragged, its start; else where it goes, or went.
        var dragging by remember(song.id) { mutableStateOf<Long?>(null) }
        val shownMs = dragging ?: choice.targetMs
        Text(
            if (choice.startMs == null && dragging == null) stringResource(R.string.native_share_music_moving, time(shownMs))
            else stringResource(R.string.native_share_music_starts, time(shownMs)),
            color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp).testTag("musicStartTime"),
        )
        ClipTrack(song.durationMs, shownMs, playingMs, onDrag = { dragging = it }, onMove = { dragging = null; actions.start(it) })
        Text(
            stringResource(R.string.native_share_music_clip_hint, (StoryMusic.CLIP_MS / 1000).toInt()),
            color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp),
        )
        if (choice.failed) {
            Text(stringResource(R.string.native_share_music_start_failed), color = Color(0xFFFF8A80), fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp).testTag("musicStartFailed"))
        }
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.remove) { Text(stringResource(R.string.native_share_music_remove), color = Color.White) }
            Spacer(Modifier.weight(1f))
            Button(onClick = actions.close, modifier = Modifier.testTag("musicDone")) { Text(stringResource(R.string.native_share_done)) }
        }
    }
}

/**
 * The whole song as a bar, its clip ([StoryMusic.CLIP_MS] from [startMs]) highlighted and what plays
 * ([playingMs]) as a line. A drag or a tap moves the clip (centred under the finger): [onDrag] while
 * it moves, [onMove] with where it ends.
 */
@Composable
private fun ClipTrack(durationMs: Long, startMs: Long, playingMs: Long?, onDrag: (Long) -> Unit, onMove: (Long) -> Unit) {
    val lastStart = (durationMs - StoryMusic.CLIP_MS).coerceAtLeast(0)
    val current by rememberUpdatedState(startMs)
    val drag by rememberUpdatedState(onDrag)
    val move by rememberUpdatedState(onMove)
    val label = stringResource(R.string.native_share_music_starts, time(startMs))
    Canvas(
        Modifier.fillMaxWidth().height(48.dp).testTag("musicStart")
            .semantics {
                contentDescription = label
                if (lastStart > 0) {
                    progressBarRangeInfo = ProgressBarRangeInfo(startMs.toFloat() / lastStart, 0f..1f)
                    setProgress { onMove((it.coerceIn(0f, 1f) * lastStart).toLong()); true }
                }
            }
            .pointerInput(durationMs) {
                if (lastStart <= 0) return@pointerInput
                // The start that centres the clip at [x].
                fun at(x: Float) = (x / size.width * durationMs - StoryMusic.CLIP_MS / 2).toLong().coerceIn(0, lastStart)
                detectTapGestures { move(at(it.x)) }
            }
            .pointerInput(durationMs) {
                if (lastStart <= 0) return@pointerInput
                var moving = 0L
                detectHorizontalDragGestures(
                    onDragStart = { moving = current; drag(moving) },
                    onDragEnd = { move(moving) },
                    onDragCancel = { move(moving) },
                ) { _, dx -> moving = (moving + dx / size.width * durationMs).toLong().coerceIn(0, lastStart); drag(moving) }
            },
    ) {
        val radius = CornerRadius(8.dp.toPx())
        drawRoundRect(Color.White.copy(alpha = 0.18f), cornerRadius = radius)
        if (durationMs <= 0) return@Canvas
        fun x(ms: Long) = ms.toFloat() / durationMs * size.width
        val left = x(startMs)
        val width = x(minOf(startMs + StoryMusic.CLIP_MS, durationMs)) - left
        drawRoundRect(ACCENT.copy(alpha = 0.55f), Offset(left, 0f), Size(width, size.height), radius)
        drawRoundRect(Color.White, Offset(left, 0f), Size(width, size.height), radius, style = Stroke(2.dp.toPx()))
        playingMs?.let {
            val at = x(it.coerceIn(0, durationMs))
            drawLine(Color.White, Offset(at, -4.dp.toPx()), Offset(at, size.height + 4.dp.toPx()), 3.dp.toPx())
        }
    }
}

// A song's start as m:ss.
private fun time(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

// The picker's highlight (Facebook's blue), and its lighter shade for text on black.
private val ACCENT = Color(0xFF1877F2)
private val ACCENT_TEXT = Color(0xFF6CA8FF)

// How often what Facebook's panel plays is read.
private const val PLAYBACK_MS = 400L

// Covers already loaded, for the process.
private val covers = LruCache<String, ImageBitmap>(80)

/** A song's cover, from Facebook's media servers only (none for any other address). */
@Composable
internal fun Cover(url: String, modifier: Modifier = Modifier) {
    val bitmap by produceState(covers.get(url), url) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val parsed = URL(url)
                if (parsed.protocol != "https" || !isFacebookCdnHost(parsed.host)) return@runCatching null
                parsed.openStream().use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }?.also { covers.put(url, it) }
    }
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.15f))) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** The chosen song over the story's preview: its cover, title and artist. */
@Composable
internal fun MusicChip(choice: MusicChoice, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(6.dp).testTag("musicChip"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(choice.song.cover, Modifier.size(28.dp))
        Text("${choice.song.title} · ${choice.song.artist}", color = Color.White, fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp).width(220.dp))
    }
}
