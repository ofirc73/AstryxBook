package com.astryx.book.ui.screens
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.astryx.book.utils.Song
import com.astryx.book.utils.StoryMusic
import com.astryx.book.utils.isFacebookCdnHost
import com.eepiemi.materialbook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/** The music picker's steps (NativeShareController's), for [MusicPicker]. */
class MusicActions(
    val search: (String) -> Unit,
    val play: (Song) -> Unit,
    val choose: (Song) -> Unit,
    val start: (Float) -> Unit,
    val close: () -> Unit,
    val remove: () -> Unit,
)

/**
 * The story's music, over the share screen: Facebook's songs (For you, or a search) to preview and
 * choose from, then the chosen one's start. No lyrics style: the app keeps the song's sticker off
 * the story (story_layout.js), and the style is only the sticker's look. Facebook's own music panel does the
 * work out of sight ([actions], StoryMusic): it plays the previews and keeps the song for the story.
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
            when {
                share.musicReady == false -> Waiting(stringResource(R.string.native_share_music_preparing))
                share.musicReady == null -> Waiting(stringResource(R.string.native_share_music_unavailable), spinner = false)
                choice != null -> Chosen(choice, actions)
                else -> Songs(actions)
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
private fun Songs(actions: MusicActions) {
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
            items(songs, key = { it.id }) { song -> SongRow(song, actions) }
        }
    }
}

@Composable
private fun SongRow(song: Song, actions: MusicActions) {
    Row(
        Modifier.fillMaxWidth().clickable { actions.choose(song) }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("musicSong"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song.cover, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { actions.play(song) }) {
            Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.native_share_music_play, song.title), tint = Color.White)
        }
    }
}

// The chosen song: where its clip starts, and Remove / Done. Facebook plays it
// from the start chosen, so moving the start is heard.
@Composable
private fun Chosen(choice: MusicChoice, actions: MusicActions) {
    val song = choice.song
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(song.cover, Modifier.size(64.dp))
            Column(Modifier.padding(start = 12.dp)) {
                Text(song.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text(song.artist, color = Color.White.copy(alpha = 0.7f))
            }
        }
        // The start as Facebook took it (shown once its preview plays from there; "…" meanwhile).
        var start by remember(song.id) { mutableFloatStateOf(choice.startFraction) }
        Text(
            stringResource(R.string.native_share_music_starts, choice.startMs?.let { time(it) } ?: "\u2026"),
            color = Color.White, modifier = Modifier.padding(top = 20.dp).testTag("musicStartTime"),
        )
        Slider(
            value = start,
            onValueChange = { start = it },
            onValueChangeFinished = { actions.start(start) },
            modifier = Modifier.testTag("musicStart"),
        )
        Row(Modifier.fillMaxWidth().padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = actions.remove) { Text(stringResource(R.string.native_share_music_remove), color = Color.White) }
            Spacer(Modifier.weight(1f))
            Button(onClick = actions.close, modifier = Modifier.testTag("musicDone")) { Text(stringResource(R.string.native_share_done)) }
        }
    }
}

// A song's start as m:ss.
private fun time(ms: Long): String = "%d:%02d".format(ms / 60_000, ms / 1000 % 60)

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
