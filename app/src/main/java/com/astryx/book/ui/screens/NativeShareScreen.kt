package com.astryx.book.ui.screens
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.astryx.book.utils.ShareTarget
import com.astryx.book.utils.SharedFile
import com.astryx.book.utils.StoryText
import com.astryx.book.utils.StoryTextLayer
import com.eepiemi.materialbook.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.min

/**
 * Photos and videos shared into the app for a story or the feed, on the app's own share screen
 * (NativeShareScreen) before they go to Facebook: per photo the texts put on it (a story), the
 * post's text (the feed), and how far the share has got ([phase]).
 */
class NativeShare(val target: ShareTarget, val files: List<SharedFile>) {
    enum class Phase { EDITING, SENDING, FAILED }

    /** The texts on each file, in order (a video's stay empty). */
    val texts = mutableStateListOf<List<StoryTextLayer>>().apply { repeat(files.size) { add(emptyList()) } }
    /** Each file's alternative text (a story's photos; blank: Facebook's generated one). */
    val altTexts = mutableStateListOf<String>().apply { repeat(files.size) { add("") } }
    /** Whether the story gets Facebook's AI label ("made with AI"). */
    var aiLabel by mutableStateOf(false)
    var caption by mutableStateOf("")
    var phase by mutableStateOf(Phase.EDITING)

    /** Whether the tool for texts on photos is offered ([target]'s a story). */
    val textTool: Boolean get() = target == ShareTarget.STORY
}

/** Whether [file] is a photo (texts can go on it). */
internal fun SharedFile.isPhoto(): Boolean = mimeType.lowercase().startsWith("image/")

// The size a preview is decoded at: about a phone screen's.
private const val PREVIEW_MAX_SIDE = 1440

/**
 * The app's own share screen for a story or a feed post, over the WebViews (Facebook's page gets
 * ready under it): the shared items to swipe through, a text tool on a story's photos, the post's
 * text for the feed. [onShare] (Share or Post) has the app hand them on and press Facebook's
 * Share itself; [onEditInFacebook] hands them to Facebook's own editor (music, stickers) as before;
 * [onCancel] (the close button, Back) gives the share up.
 */
@Composable
fun NativeShareScreen(
    share: NativeShare,
    onShare: () -> Unit,
    onEditInFacebook: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState { share.files.size }
    // The text being edited: its page and its place in that page's texts (-1: a new one).
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var movingText by remember { mutableStateOf(false) }
    // The page whose alternative text is being written.
    var altEditing by remember { mutableStateOf<Int?>(null) }
    val busy = share.phase != NativeShare.Phase.EDITING

    BackHandler {
        when {
            editing != null -> editing = null
            altEditing != null -> altEditing = null
            share.phase == NativeShare.Phase.SENDING -> Unit // the page is being handed the share
            else -> onCancel()
        }
    }

    Box(
        modifier
            .fillMaxSize()
            // Over the page, the Messages layer (1) and the connection error screen (3), which
            // MaterialbookWebView puts in the same Box; under app lock (10).
            .zIndex(5f)
            .background(Color.Black)
            .testTag("nativeShare")
            // Taps go no further: the page under it isn't the user's to touch now.
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            Row(
                Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCancel, enabled = !busy) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.native_share_close), tint = Color.White)
                }
                Text(
                    stringResource(if (share.target == ShareTarget.STORY) R.string.native_share_title_story else R.string.native_share_title_feed),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEditInFacebook, enabled = !busy) {
                    Text(stringResource(R.string.native_share_edit_in_facebook), color = Color.White)
                }
            }
            HorizontalPager(
                state = pager,
                userScrollEnabled = !movingText && !busy,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val file = share.files[page]
                MediaPreview(
                    file = file,
                    texts = share.texts[page],
                    editable = share.textTool && file.isPhoto() && !busy,
                    onTextChange = { index, layer ->
                        share.texts[page] = share.texts[page].toMutableList().also { it[index] = layer }
                    },
                    onTextTap = { index -> editing = page to index },
                    onMoving = { movingText = it },
                )
            }
            if (share.files.size > 1) {
                Text(
                    stringResource(R.string.native_share_page, pager.currentPage + 1, share.files.size),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp),
                )
            }
            if (share.target == ShareTarget.FEED) {
                OutlinedTextField(
                    value = share.caption,
                    onValueChange = { share.caption = it },
                    enabled = !busy,
                    placeholder = { Text(stringResource(R.string.native_share_caption_hint)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White, unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White,
                        focusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = Color.White.copy(alpha = 0.6f),
                    ),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("nativeShareCaption"),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val onPhoto = share.files.getOrNull(pager.currentPage)?.isPhoto() == true
                if (share.textTool && onPhoto) {
                    IconButton(
                        onClick = { editing = pager.currentPage to -1 },
                        enabled = !busy,
                        modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape),
                    ) {
                        Icon(Icons.Filled.TextFields, contentDescription = stringResource(R.string.native_share_add_text), tint = Color.White)
                    }
                    // The photo's alternative text, and the story's AI label: Facebook's editor
                    // gets them before Share (share_media.js storyExtras).
                    val hasAlt = share.altTexts[pager.currentPage].isNotBlank()
                    val altLabel = stringResource(R.string.native_share_alt_text)
                    TextButton(
                        onClick = { altEditing = pager.currentPage },
                        enabled = !busy,
                        modifier = Modifier.padding(start = 8.dp)
                            .background(if (hasAlt) Color.White else Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("nativeShareAlt"),
                    ) {
                        Text(
                            "ALT",
                            color = if (hasAlt) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.semantics { contentDescription = altLabel },
                        )
                    }
                    IconToggleButton(
                        checked = share.aiLabel,
                        onCheckedChange = { share.aiLabel = it },
                        enabled = !busy,
                        modifier = Modifier.padding(start = 8.dp)
                            .background(if (share.aiLabel) Color.White else Color.White.copy(alpha = 0.15f), CircleShape)
                            .testTag("nativeShareAiLabel"),
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = stringResource(R.string.native_share_ai_label),
                            tint = if (share.aiLabel) Color.Black else Color.White,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(onClick = onShare, enabled = !busy, modifier = Modifier.testTag("nativeShareShare")) {
                    Text(stringResource(if (share.target == ShareTarget.STORY) R.string.native_share_share_story else R.string.native_share_post))
                }
            }
        }

        altEditing?.let { page ->
            AltTextDialog(
                initial = share.altTexts[page],
                onDone = { share.altTexts[page] = it.trim(); altEditing = null },
                onDismiss = { altEditing = null },
            )
        }

        editing?.let { (page, index) ->
            TextEditor(
                initial = share.texts[page].getOrNull(index) ?: StoryTextLayer(""),
                onDone = { layer ->
                    val list = share.texts[page].toMutableList()
                    when {
                        index < 0 && layer.text.isNotBlank() -> list += layer
                        index >= 0 && layer.text.isBlank() -> list.removeAt(index)
                        index >= 0 -> list[index] = layer
                    }
                    share.texts[page] = list
                    editing = null
                },
                modifier = Modifier.zIndex(1f),
            )
        }

        when (share.phase) {
            NativeShare.Phase.SENDING -> Overlay {
                CircularProgressIndicator(color = Color.White)
                Text(
                    stringResource(if (share.target == ShareTarget.STORY) R.string.native_share_sharing_story else R.string.native_share_sharing_post),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            NativeShare.Phase.FAILED -> Overlay {
                Text(
                    stringResource(R.string.native_share_failed),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onCancel) { Text(stringResource(R.string.native_share_close), color = Color.White) }
                    Button(onClick = onEditInFacebook) { Text(stringResource(R.string.native_share_edit_in_facebook)) }
                }
            }
            NativeShare.Phase.EDITING -> Unit
        }
    }
}

/** A photo's alternative text: what Facebook reads out for people who can't see it. */
@Composable
private fun AltTextDialog(initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.native_share_alt_text)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.native_share_alt_text_hint)) },
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("nativeShareAltText"),
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(stringResource(R.string.native_share_done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun Overlay(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).zIndex(2f), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** A shared photo (with its texts) or a video's first frame, fitted to the space it has. */
@Composable
private fun MediaPreview(
    file: SharedFile,
    texts: List<StoryTextLayer>,
    editable: Boolean,
    onTextChange: (Int, StoryTextLayer) -> Unit,
    onTextTap: (Int) -> Unit,
    onMoving: (Boolean) -> Unit,
) {
    val image by produceState<ImageBitmap?>(null, file.file) {
        value = withContext(Dispatchers.IO) { previewOf(file) }?.asImageBitmap()
    }
    val slop = LocalViewConfiguration.current.touchSlop
    val currentTexts by rememberUpdatedState(texts)
    val currentOnChange by rememberUpdatedState(onTextChange)
    val currentOnTap by rememberUpdatedState(onTextTap)
    val currentOnMoving by rememberUpdatedState(onMoving)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val bitmap = image ?: run {
            CircularProgressIndicator(color = Color.White)
            return@BoxWithConstraints
        }
        val currentBitmap by rememberUpdatedState(bitmap)
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(editable) {
                    if (!editable) return@pointerInput
                    // A finger on a text moves it, two pinch it; a tap edits it. Elsewhere the pager swipes.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val box = fit(currentBitmap, size)
                        val w = box.width.toInt().coerceAtLeast(1)
                        val h = box.height.toInt().coerceAtLeast(1)
                        val hit = currentTexts.indexOfLast {
                            StoryText.bounds(it, w, h).contains(down.position.x - box.left, down.position.y - box.top)
                        }
                        if (hit < 0) return@awaitEachGesture
                        down.consume()
                        currentOnMoving(true)
                        val start = currentTexts[hit]
                        // Moved and scaled from where the finger came down: nothing moves within
                        // the touch slop (a tap), then the text catches up with the finger.
                        var panned = Offset.Zero
                        var zoomed = 1f
                        var travelled = 0f
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                val pan = event.calculatePan()
                                val zoom = event.calculateZoom()
                                panned += pan
                                zoomed *= zoom
                                travelled += abs(pan.x) + abs(pan.y) + abs(zoom - 1f) * w
                                if (travelled > slop) {
                                    currentOnChange(hit, start.copy(
                                        x = (start.x + panned.x / w).coerceIn(0f, 1f),
                                        y = (start.y + panned.y / h).coerceIn(0f, 1f),
                                        size = (start.size * zoomed).coerceIn(StoryText.MIN_SIZE, StoryText.MAX_SIZE),
                                    ))
                                }
                                event.changes.forEach { it.consume() }
                            }
                        } finally {
                            currentOnMoving(false)
                        }
                        if (travelled <= slop) currentOnTap(hit)
                    }
                }
        ) {
            val box = fit(bitmap, IntSize(size.width.toInt(), size.height.toInt()))
            drawImage(
                bitmap,
                dstOffset = androidx.compose.ui.unit.IntOffset(box.left.toInt(), box.top.toInt()),
                dstSize = IntSize(box.width.toInt(), box.height.toInt()),
            )
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                native.save()
                native.translate(box.left, box.top)
                texts.forEach { StoryText.draw(native, it, box.width.toInt(), box.height.toInt()) }
                native.restore()
            }
        }
        if (!file.isPhoto()) {
            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(64.dp))
        }
    }
}

// Where [bitmap] shows, fitted whole into [space] and centred.
private fun fit(bitmap: ImageBitmap, space: IntSize): Rect {
    val scale = min(space.width.toFloat() / bitmap.width, space.height.toFloat() / bitmap.height)
    val w = bitmap.width * scale
    val h = bitmap.height * scale
    val left = (space.width - w) / 2
    val top = (space.height - h) / 2
    return Rect(Offset(left, top), androidx.compose.ui.geometry.Size(w, h))
}

// A photo decoded for the screen, or a video's first frame.
private fun previewOf(file: SharedFile): Bitmap? {
    if (file.isPhoto()) return StoryText.decode(file.file, PREVIEW_MAX_SIDE)
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.file.path)
        retriever.getFrameAtTime(0)
    } catch (e: Exception) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

/**
 * Typing a text for a story's photo: the text over a dimmed screen, the colours under it, a box
 * behind it or not. Done (or Back, which the screen's handler turns into a cancel) ends it; an
 * empty text removes it.
 */
@Composable
private fun TextEditor(initial: StoryTextLayer, onDone: (StoryTextLayer) -> Unit, modifier: Modifier = Modifier) {
    var layer by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val textColor = Color(StoryText.textColor(layer))
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .systemBarsPadding()
            .imePadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { layer = layer.copy(background = !layer.background) },
                modifier = Modifier.background(Color.White.copy(alpha = if (layer.background) 0.4f else 0.15f), CircleShape),
            ) {
                Icon(Icons.Filled.FormatColorFill, contentDescription = stringResource(R.string.native_share_text_background), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onDone(layer) }, modifier = Modifier.testTag("nativeShareTextDone")) {
                Text(stringResource(R.string.native_share_done), color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
        BasicTextField(
            value = layer.text,
            onValueChange = { layer = layer.copy(text = it) },
            textStyle = TextStyle(color = textColor, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(textColor),
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .then(
                    if (layer.background) Modifier.background(Color(layer.color), RoundedCornerShape(10.dp)).padding(10.dp)
                    else Modifier
                )
                .focusRequester(focus)
                .testTag("nativeShareTextField"),
        )
        LazyRow(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            items(StoryText.COLORS) { color ->
                Box(
                    Modifier
                        .size(32.dp)
                        .background(Color(color), CircleShape)
                        .border(if (color == layer.color) 3.dp else 1.dp, Color.White, CircleShape)
                        .clickable { layer = layer.copy(color = color) }
                )
            }
        }
    }
}

/**
 * The files to hand Facebook for [share]: each photo with texts drawn onto it (a new JPEG beside
 * the copy, which goes), the others as they are; null when a photo couldn't be drawn. Off the main
 * thread.
 */
internal fun renderedFiles(share: NativeShare): List<SharedFile>? = share.files.mapIndexed { index, file ->
    val texts = share.texts.getOrNull(index).orEmpty().filter { it.text.isNotBlank() }
    if (texts.isEmpty() || !file.isPhoto()) return@mapIndexed file
    val name = file.name.substringBeforeLast('.') + "_text.jpg"
    val out = File(file.file.parentFile, name)
    if (!StoryText.render(file.file, texts, out)) return null
    file.file.delete()
    SharedFile(out, name, "image/jpeg")
}
