package com.astryx.book.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.astryx.book.ui.components.AttachSourceDialog
import com.astryx.book.utils.CaptureFile
import com.astryx.book.LocalAppLocked
import com.astryx.book.utils.CaptureKind
import com.astryx.book.utils.newCaptureFile
import com.eepiemi.materialbook.R

/**
 * A page's request for a file (a tap on Facebook's Photo button, a chat's attachment): the
 * [picker] WebView makes for it (Android's picker), what the camera can give it instead
 * ([capture], see captureKinds), and whether the page asked for the camera itself ([captureNow],
 * the input's `capture`).
 */
class FileRequest(val picker: Intent, val capture: Set<CaptureKind>, val captureNow: Boolean)

/**
 * Answers a WebView's file requests, [request] the one that just came ([onTaken] once it's in
 * hand): Android's picker, or, where the camera fits, a choice first (AttachSourceDialog) between
 * the picker, a photo taken now and a video recorded now. The page gets the files through
 * [onFiles], or [onCancel]: every request must be answered, or the page's next one never comes.
 *
 * The app declares the CAMERA permission (Messenger calls), and Android then refuses to open the
 * camera app for an app that hasn't been given it: asked for when the user picks the camera.
 */
@Composable
fun FileRequestEffects(request: FileRequest?, onTaken: () -> Unit, onFiles: (Array<Uri>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var asking by remember { mutableStateOf<FileRequest?>(null) }
    // The camera is out filling this file.
    var capturing by remember { mutableStateOf<CaptureFile?>(null) }
    var kindAwaitingPermission by remember { mutableStateOf<CaptureKind?>(null) }

    fun say(message: Int) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val picked = result.data?.takeIf { result.resultCode == Activity.RESULT_OK }?.let { pickedUris(it) }
        if (picked.isNullOrEmpty()) onCancel() else onFiles(picked.toTypedArray())
    }

    // An empty file is a capture the camera app gave up on, whatever it answered.
    fun captured(taken: Boolean) {
        val made = capturing
        capturing = null
        if (taken && made != null && made.file.length() > 0) {
            onFiles(arrayOf(made.uri))
        } else {
            made?.file?.delete()
            onCancel()
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured(it) }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { captured(it) }

    fun openCamera(kind: CaptureKind) {
        val made = newCaptureFile(context, kind)
        capturing = made
        runCatching { (if (kind == CaptureKind.PHOTO) photo else video).launch(made.uri) }.onFailure {
            capturing = null
            say(R.string.attach_no_camera)
            onCancel()
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val kind = kindAwaitingPermission
        kindAwaitingPermission = null
        if (granted && kind != null) {
            openCamera(kind)
        } else {
            say(R.string.attach_camera_denied)
            onCancel()
        }
    }
    fun useCamera(kind: CaptureKind) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera(kind)
        } else {
            kindAwaitingPermission = kind
            runCatching { permission.launch(Manifest.permission.CAMERA) }.onFailure {
                kindAwaitingPermission = null
                onCancel()
            }
        }
    }
    fun openPicker(intent: Intent) {
        runCatching { picker.launch(intent) }.onFailure { onCancel() }
    }

    // The choice has its own window, above the lock screen: answered as dismissed when the app
    // locks (shown, the camera or the picker could fill the page behind the lock).
    val locked = LocalAppLocked.current
    LaunchedEffect(locked) {
        if (locked && asking != null) {
            asking = null
            onCancel()
        }
    }

    LaunchedEffect(request) {
        val new = request ?: return@LaunchedEffect
        onTaken()
        Log.d("Astryxbook", "File request: " + if (new.capture.isEmpty()) "the picker" else if (new.captureNow) "the camera" else "a choice")
        when {
            new.capture.isEmpty() -> openPicker(new.picker)
            new.captureNow -> useCamera(new.capture.first())
            else -> asking = new
        }
    }

    asking?.let { asked ->
        AttachSourceDialog(
            kinds = asked.capture,
            onFiles = {
                asking = null
                openPicker(asked.picker)
            },
            onCapture = { kind ->
                asking = null
                useCamera(kind)
            },
            onDismiss = {
                asking = null
                onCancel()
            },
        )
    }
}

// What the picker gave back: one file, or several (a clip).
private fun pickedUris(result: Intent): List<Uri> =
    result.data?.let { listOf(it) }
        ?: result.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
