package com.astryx.book.utils
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

private const val SHARED_DIR = "shared_media"

/** The content:// items a SEND or SEND_MULTIPLE intent carries, in order, without repeats. */
fun sharedUris(intent: Intent): List<Uri> {
    val uris = mutableListOf<Uri>()
    when (intent.action) {
        Intent.ACTION_SEND -> streamExtra(intent)?.let { uris += it }
        Intent.ACTION_SEND_MULTIPLE -> uris += streamListExtra(intent)
    }
    intent.clipData?.let { clip -> for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris += it } }
    return uris.filter { it.scheme == "content" }.distinct()
}

@Suppress("DEPRECATION")
private fun streamExtra(intent: Intent): Uri? =
    if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    else intent.getParcelableExtra(Intent.EXTRA_STREAM)

@Suppress("DEPRECATION")
private fun streamListExtra(intent: Intent): List<Uri> =
    (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    else intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()

/** What the sharing app tells about [uri]: its name, type and size (-1 when it doesn't say). */
fun describeShared(context: Context, uri: Uri, fallbackType: String?): ShareCandidate {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment ?: "shared"
    var size = -1L
    runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { c.getString(it) }?.let { name = it }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 && !c.isNull(it) }?.let { size = c.getLong(it) }
            }
        }
    }
    val type = resolver.getType(uri) ?: fallbackType?.takeIf { '*' !in it } ?: ""
    return ShareCandidate(name, type, size)
}

/**
 * Copies what [target] takes of the shared [uris] into the app's cache (replacing an earlier
 * share's copies): the sharing app's read permission lasts only as long as this activity, and
 * the page asks for the files later. Runs off the main thread. Returns the copies and how many
 * shared items were left out (wrong type, too big, too many, unreadable).
 */
fun copySharedMedia(context: Context, target: ShareTarget, uris: List<Uri>, fallbackType: String?): Pair<List<SharedFile>, Int> {
    val dir = File(context.cacheDir, SHARED_DIR)
    dir.deleteRecursively()
    dir.mkdirs()
    val described = uris.map { it to describeShared(context, it, fallbackType) }
    val selection = ShareMedia.select(target, described.map { it.second })
    val copies = mutableListOf<SharedFile>()
    var failed = 0
    selection.accepted.forEachIndexed { index, candidate ->
        val uri = described.first { it.second === candidate }.first
        val name = ShareMedia.safeName(candidate.name, index, candidate.mimeType)
        val out = File(dir, name)
        val ok = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> copyCapped(input, output, ShareMedia.maxBytes(candidate.mimeType)) }
            } ?: false
        }.onFailure { Log.w("Astryxbook", "Share: couldn't copy a shared item", it) }.getOrDefault(false)
        if (ok) copies += SharedFile(out, name, candidate.mimeType) else { out.delete(); failed++ }
    }
    return copies to (selection.skipped + failed)
}

// Copies at most [max] bytes; false (nothing usable) if the stream is longer than that.
private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream, max: Long): Boolean {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        val read = input.read(buffer)
        if (read < 0) return total > 0
        total += read
        if (total > max) return false
        output.write(buffer, 0, read)
    }
}

/** Deletes the copies of the last share (when it's done, or the app clears its cache). */
fun clearSharedMedia(context: Context) {
    File(context.cacheDir, SHARED_DIR).deleteRecursively()
}
