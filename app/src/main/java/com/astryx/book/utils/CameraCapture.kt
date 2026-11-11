package com.astryx.book.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What the camera can give a page that asks for a file (Facebook's Photo buttons, a chat's
 * attachment): a photo it takes now, or a video it records. The page's own request opens Android's
 * picker, which only shows what's already on the phone.
 */
enum class CaptureKind(val prefix: String, val extension: String) {
    PHOTO("photo", "jpg"),
    VIDEO("video", "mp4"),
}

// What the camera makes: a JPEG photo, an MP4 video.
private val PHOTO_TYPES = setOf("image/*", "image/jpeg", "image/jpg", ".jpg", ".jpeg")
private val VIDEO_TYPES = setOf("video/*", "video/mp4", ".mp4")

/**
 * The camera's part in a file request that accepts [acceptTypes] (the input's `accept`: MIME types
 * and file extensions): a photo where JPEGs are taken, a video where MP4s are, both where anything
 * is (no types, or any), none where neither fits (documents only, PNG only).
 */
fun captureKinds(acceptTypes: Array<String>?): Set<CaptureKind> {
    val types = acceptTypes.orEmpty().flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (types.isEmpty() || "*/*" in types) return CaptureKind.entries.toSet()
    return buildSet {
        if (types.any { it in PHOTO_TYPES }) add(CaptureKind.PHOTO)
        if (types.any { it in VIDEO_TYPES }) add(CaptureKind.VIDEO)
    }
}

/** The name a capture is kept under: `photo_20261111_093100.jpg`. */
fun captureFileName(kind: CaptureKind, time: Date): String =
    "${kind.prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(time)}.${kind.extension}"

/** The cache folder the camera's photos and videos are kept in until the page has read them. */
const val CAPTURES_DIR = "captures"
private const val KEEP_CAPTURES_MS = 24 * 60 * 60 * 1000L

/** A file for the camera to fill, and the address the camera app (and then the page) reaches it by. */
class CaptureFile(val file: File, val uri: Uri)

/**
 * A new, empty file for a capture of [kind] in the app's cache, through the app's FileProvider
 * (the camera app is handed that one file, nothing else). Captures older than a day go: the page
 * read them long ago.
 */
fun newCaptureFile(context: Context, kind: CaptureKind, now: Date = Date()): CaptureFile {
    val dir = File(context.cacheDir, CAPTURES_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { if (now.time - it.lastModified() > KEEP_CAPTURES_MS) it.delete() }
    val file = File(dir, captureFileName(kind, now))
    return CaptureFile(file, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
}
