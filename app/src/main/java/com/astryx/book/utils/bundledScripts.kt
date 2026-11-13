package com.astryx.book.utils
import androidx.annotation.RawRes

// Page scripts always come from the copy bundled in the (signed) APK. They used
// to be fetched from main on GitHub at every launch, which let anyone who could
// push to main run code inside every user's logged-in Facebook page.
data class Script(
    val isEnabled: Boolean,
    @param:RawRes val resourceId: Int
)

// Concatenates the enabled scripts in the caller's order, each in its own try block: they run
// as one evaluation, so an exception in one (a page Facebook laid out differently from what it
// expects) used to stop every script after it, e.g. adblock.js on a photo opened directly took
// the download and copy buttons with it. Each script's own top-level names stay in its block.
fun bundledScripts(
    scripts: List<Script>,
    readBundled: (Int) -> String
): String = buildString {
    scripts.filter { it.isEnabled }.forEach {
        append("try {\n")
        append(readBundled(it.resourceId))
        append("\n} catch (e) { console.error('Astryxbook page script failed:', e); }\n")
    }
}

private val pageScripts = java.util.concurrent.ConcurrentHashMap<Int, String>()

/**
 * A page script's text, from [read] the first time and kept for the process: the effects that run
 * one on every page (the video detector, the Messages layer's four…) read it from the APK, on the
 * main thread, each time.
 */
fun pageScript(@RawRes id: Int, read: (Int) -> String): String = pageScripts.getOrPut(id) { read(id) }

/** The bundled page script [id] ([pageScript]). */
fun android.content.res.Resources.pageScript(@RawRes id: Int): String =
    pageScript(id) { openRawResource(it).bufferedReader().use { reader -> reader.readText() } }
