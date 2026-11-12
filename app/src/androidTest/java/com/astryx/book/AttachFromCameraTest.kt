package com.astryx.book

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eepiemi.materialbook.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A page's file request (Facebook's Photo button, a chat's attachment): where the camera can
 * answer it, the app asks first, Android's picker, a photo taken now or a video recorded now; the
 * page gets the picked file or the camera's; a request the camera can't answer opens the picker
 * as before; and a request dismissed is answered, so the next one comes. The picker and the
 * camera app are answered here, so neither opens.
 */
@RunWith(AndroidJUnit4::class)
class AttachFromCameraTest {

    private val app = AppHarness()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val monitors = mutableListOf<Instrumentation.ActivityMonitor>()

    private fun text(id: Int) = app.context.getString(id)

    @Before
    fun setUp() {
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    @After
    fun tearDown() {
        monitors.forEach(instrumentation::removeMonitor)
        app.finish()
        File(app.context.filesDir, "datastore").deleteRecursively()
    }

    // Answers the activities started with [action] instead of opening them.
    private class Answered(val action: String, val answer: (Intent) -> Instrumentation.ActivityResult) :
        Instrumentation.ActivityMonitor() {
        @Volatile var starts = 0

        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action != action) return null
            starts++
            return answer(intent)
        }
    }

    private fun answer(action: String, answer: (Intent) -> Instrumentation.ActivityResult) =
        Answered(action, answer).also { monitors += it; instrumentation.addMonitor(it) }

    // A page with a file input that says what it was given.
    private fun openPage(accept: String) {
        app.startOnPage(
            PAGE_URL,
            "<html><body><input id=\"f\" type=\"file\" accept=\"$accept\" style=\"display:block;width:300px;height:120px;margin:80px 20px\">" +
                "<script>window.__testPage = true; window.__touches = [];" +
                " [\"pointerdown\", \"click\"].forEach(function (t) { document.addEventListener(t, function (e) {" +
                " window.__touches.push(t + \":\" + (e.target.id || e.target.tagName) + \"@\" + Math.round(e.clientX) + \",\" + Math.round(e.clientY)); }, true); });" +
                " document.getElementById(\"f\").onchange = function () {" +
                " var f = this.files; window.__picked = f.length + \" \" + (f[0] ? f[0].name + \" \" + f[0].size : \"\"); };</script></body></html>"
        )
    }

    private fun tapInput() = assertTrue(app.tapOn(app.mainWebView(), "document.getElementById(\"f\")"))

    // What the page and the screen show, for a failure: the touches the page got (none: the tap
    // went elsewhere), the input's box and the windows.
    private fun state() = "the page got ${app.eval("JSON.stringify(window.__touches)")}, the input at " +
        "${app.eval("JSON.stringify(document.getElementById(\"f\").getBoundingClientRect())")}; ${app.screenState()}"

    // The choice of where the file comes from, with [label] among its sources.
    private fun awaitChoice(label: Int) = assertTrue({ "no choice with ${text(label)} (${state()})" }, app.waitFor { app.textShown(text(label)) })

    private fun picked() = app.eval("window.__picked || \"\"").trim('"')

    private fun allowCamera() =
        instrumentation.uiAutomation.grantRuntimePermission(app.context.packageName, Manifest.permission.CAMERA)

    @Test
    fun aPhotoRequest_asksWhereFrom_andThePickersFileReachesThePage() {
        val file = app.servedFile("picked.png", app.picture())
        val uri = app.servedUri(file)
        val picker = answer(Intent.ACTION_GET_CONTENT) { Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)) }
        openPage("image/*")
        tapInput()
        awaitChoice(R.string.attach_take_photo)
        assertTrue(app.textShown(text(R.string.attach_files)))
        assertFalse("a photo request offers to record a video", app.textShown(text(R.string.attach_record_video)))
        assertEquals("the picker opened before the user chose", 0, picker.starts)

        assertTrue(app.tapText(text(R.string.attach_files)))
        assertTrue({ "the picker's file never reached the page (${picked()})" }, app.waitFor { picked() == "1 picked.png ${file.length()}" })
        assertEquals(1, picker.starts)
    }

    @Test
    fun takingAPhoto_givesThePageTheCamerasFile() {
        allowCamera()
        val photo = app.picture()
        // The camera app: fills the file it's handed.
        val camera = answer(MediaStore.ACTION_IMAGE_CAPTURE) { intent ->
            @Suppress("DEPRECATION") val output = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
            app.context.contentResolver.openOutputStream(output)!!.use { it.write(photo) }
            Instrumentation.ActivityResult(Activity.RESULT_OK, null)
        }
        openPage("image/*,video/*")
        tapInput()
        awaitChoice(R.string.attach_take_photo)
        assertTrue("a photo and video request doesn't offer a video", app.textShown(text(R.string.attach_record_video)))
        assertTrue(app.tapText(text(R.string.attach_take_photo)))
        assertTrue({ "the camera's photo never reached the page (${picked()})" }, app.waitFor {
            Regex("""1 photo_\d{8}_\d{6}\.jpg ${photo.size}""").matches(picked())
        })
        assertEquals(1, camera.starts)
    }

    @Test
    fun aPhotoTheCameraGaveUpOn_givesThePageNothing_andTheNextRequestComes() {
        allowCamera()
        // The camera app closed without a photo: its file stays empty.
        val camera = answer(MediaStore.ACTION_IMAGE_CAPTURE) { Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) }
        openPage("image/*")
        tapInput()
        awaitChoice(R.string.attach_take_photo)
        assertTrue({ "Take a photo couldn't be tapped (${state()})" }, app.waitFor { app.tapText(text(R.string.attach_take_photo)) })
        assertTrue({ "the camera never opened (${state()})" }, app.waitFor { camera.starts == 1 })
        Thread.sleep(1000)
        assertEquals("", picked())
        // A request left unanswered keeps the page's next one from coming.
        tapInput()
        awaitChoice(R.string.attach_take_photo)
    }

    @Test
    fun aRequestDismissed_isAnswered_soTheNextOneComes() {
        openPage("video/*")
        tapInput()
        awaitChoice(R.string.attach_record_video)
        assertFalse("a video request offers a photo", app.textShown(text(R.string.attach_take_photo)))
        assertTrue(app.tapText(text(R.string.cancel)))
        assertTrue(app.waitFor { !app.textShown(text(R.string.attach_record_video)) })
        tapInput()
        awaitChoice(R.string.attach_record_video)
    }

    @Test
    fun aRequestTheCameraCantAnswer_opensThePickerAtOnce() {
        val picker = answer(Intent.ACTION_GET_CONTENT) { Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null) }
        openPage("application/pdf")
        tapInput()
        assertTrue({ "the picker never opened for a document (opened ${picker.starts} times; the choice " +
            "${if (app.textShown(text(R.string.attach_files))) "shows" else "doesn't show"}; ${state()})" }, app.waitFor(20_000) { picker.starts == 1 })
        assertFalse("a document request asks about the camera", app.textShown(text(R.string.attach_files)))
    }

    companion object {
        private const val PAGE_URL = "https://m.facebook.com/some.page/posts/3/"
    }
}
